package dev.cacertanalyzer.data.repository

import android.content.Context
import dev.cacertanalyzer.data.local.CCADBCache
import dev.cacertanalyzer.data.local.CertificateRetriever
import dev.cacertanalyzer.data.model.*
import dev.cacertanalyzer.data.remote.CCADBCrossReferenceService
import dev.cacertanalyzer.data.remote.CCADBParser
import dev.cacertanalyzer.data.remote.CCADBService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Repository for managing certificate data operations.
 *
 * This repository implements the Repository pattern with StateFlow for reactive
 * state management. It handles:
 * - Device certificate retrieval
 * - CCADB data fetching and caching
 * - Cross-referencing and analysis
 * - Offline support
 */
class CertificateRepository(
    private val context: Context,
    private val ccadbService: CCADBService = CCADBService(),
    private val ccadbCache: CCADBCache = CCADBCache(context),
    private val crossReferenceService: CCADBCrossReferenceService = CCADBCrossReferenceService()
) {
    // State flows for reactive UI updates
    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    private val _analysisResult = MutableStateFlow<AnalysisResult>(AnalysisResult())
    val analysisResult: StateFlow<AnalysisResult> = _analysisResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    /**
     * Performs full certificate analysis including:
     * 1. Loading device certificates
     * 2. Loading cached CCADB data or fetching from network
     * 3. Cross-referencing fingerprints
     * 4. Categorizing by trust status
     */
    suspend fun analyzeCertificates(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            _analysisState.value = AnalysisState.Loading

            Timber.d("Starting certificate analysis (forceRefresh=$forceRefresh)")

            // Step 1: Retrieve device certificates
            val deviceCertificates = CertificateRetriever.retrieveAllCertificates()
            Timber.d("Retrieved ${deviceCertificates.size} device certificates")

            // Step 2: Load or fetch CCADB data
            val ccadbRecords = if (forceRefresh || isCacheStale()) {
                fetchAndCacheCCADBData()
            } else {
                loadCachedCCADBData() ?: fetchAndCacheCCADBData()
            }

            val ccadbAvailable = ccadbRecords != null
            val records = ccadbRecords ?: emptyList()
            Timber.d("CCADB records available: ${records.size}")

            // Step 3: Cross-reference and categorize
            val analysisResult = categorizeCertificates(deviceCertificates, records)

            _analysisResult.value = analysisResult
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _analysisState.value = AnalysisState.Success(analysisResult)

            Timber.d("Analysis complete: ${analysisResult.totalCount} certs, " +
                    "${analysisResult.verifiedSystemCerts.size} verified, " +
                    "${analysisResult.unverifiedSystemCerts.size} unverified, " +
                    "${analysisResult.userInstalledCerts.size} user")

        } catch (e: Exception) {
            Timber.e(e, "Error during certificate analysis")
            _analysisState.value = AnalysisState.Error(e.message ?: "Unknown error")
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Categorizes certificates based on storage location and CCADB cross-reference.
     */
    private fun categorizeCertificates(
        certificates: List<CertificateInfo>,
        ccadbRecords: List<CCADBRecord>
    ): AnalysisResult {
        val verifiedSystem = mutableListOf<CertificateInfo>()
        val unverifiedSystem = mutableListOf<CertificateInfo>()
        val userInstalled = mutableListOf<CertificateInfo>()
        val expired = mutableListOf<CertificateInfo>()
        val expiringSoon = mutableListOf<CertificateInfo>()

        val now = Date()
        val warningThreshold = TimeUnit.DAYS.toMillis(30) // 30 days

        // Create CCADB lookup map
        val ccadbLookup = ccadbRecords.associateBy {
            CCADBRecord.normalizeFingerprint(it.sha256Fingerprint)
        }

        certificates.forEach { cert ->
            // Check expiry
            val timeToExpiry = cert.validTo.time - now.time
            val isExpired = timeToExpiry < 0
            val isExpiringSoon = timeToExpiry in 1..warningThreshold

            if (isExpired) {
                expired.add(cert)
                cert.trustStatus = TrustStatus.EXPIRED
            } else if (isExpiringSoon) {
                expiringSoon.add(cert)
                cert.trustStatus = TrustStatus.EXPIRING_SOON
            }

            // Categorize by storage location and CCADB match
            when (cert.storageLocation) {
                StorageLocation.USER -> {
                    userInstalled.add(cert)
                    if (cert.trustStatus == TrustStatus.UNVERIFIED_SYSTEM_ROOT) {
                        cert.trustStatus = TrustStatus.USER_INSTALLED_ROOT
                    }
                }
                StorageLocation.SYSTEM -> {
                    val normalizedFingerprint = CCADBRecord.normalizeFingerprint(cert.sha256Fingerprint)
                    val ccadbMatch = ccadbLookup[normalizedFingerprint]

                    if (ccadbMatch != null) {
                        verifiedSystem.add(cert)
                        if (cert.trustStatus == TrustStatus.UNVERIFIED_SYSTEM_ROOT) {
                            cert.trustStatus = TrustStatus.VERIFIED_PUBLIC_ROOT
                        }
                        // Add root program info
                        ccadbMatch.toRootPrograms().forEach { program ->
                            cert.addRootProgram(program)
                        }
                    } else {
                        unverifiedSystem.add(cert)
                        // Keep as UNVERIFIED_SYSTEM_ROOT (already default)
                    }
                }
            }
        }

        return AnalysisResult(
            allCertificates = certificates,
            verifiedSystemCerts = verifiedSystem,
            unverifiedSystemCerts = unverifiedSystem,
            userInstalledCerts = userInstalled,
            expiredCerts = expired,
            expiringSoonCerts = expiringSoon,
            ccadbDataAvailable = ccadbRecords.isNotEmpty()
        )
    }

    /**
     * Fetches CCADB data from network and caches it locally.
     */
    private suspend fun fetchAndCacheCCADBData(): List<CCADBRecord>? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Fetching CCADB data from network")
            val csvContent = ccadbService.downloadCCADBCsv()

            if (csvContent != null) {
                val parser = CCADBParser()
                val records = parser.parse(csvContent)

                // Cache the raw CSV for offline use
                ccadbCache.saveCCADBData(csvContent, System.currentTimeMillis())

                return@withContext records
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching CCADB data")
        }
        return@withContext null
    }

    /**
     * Loads cached CCADB data from local storage.
     */
    private suspend fun loadCachedCCADBData(): List<CCADBRecord>? = withContext(Dispatchers.IO) {
        try {
            val cachedData = ccadbCache.loadCCADBData()
            if (cachedData != null) {
                Timber.d("Loading CCADB data from cache")
                _lastSyncTimestamp.value = ccadbCache.getLastSyncTimestamp()
                val parser = CCADBParser()
                return@withContext parser.parse(cachedData)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading cached CCADB data")
        }
        return@withContext null
    }

    /**
     * Checks if the CCADB cache is considered stale (older than 7 days).
     */
    private fun isCacheStale(): Boolean {
        val lastSync = ccadbCache.getLastSyncTimestamp() ?: return true
        val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)
        return (System.currentTimeMillis() - lastSync) > sevenDaysInMillis
    }

    /**
     * Gets a certificate by its alias.
     */
    fun getCertificateByAlias(alias: String): CertificateInfo? {
        return _analysisResult.value.allCertificates.find { it.alias == alias }
    }

    /**
     * Refreshes the CCADB data from network.
     */
    suspend fun refreshCCADBData(): Boolean {
        return try {
            fetchAndCacheCCADBData() != null
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh CCADB data")
            false
        }
    }

    /**
     * Clears the analysis results and cache.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        ccadbCache.clear()
        _analysisResult.value = AnalysisResult()
        _analysisState.value = AnalysisState.Idle
        _lastSyncTimestamp.value = null
    }
}

/**
 * Sealed class representing the state of certificate analysis.
 */
sealed class AnalysisState {
    object Idle : AnalysisState()
    object Loading : AnalysisState()
    data class Success(val result: AnalysisResult) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}
