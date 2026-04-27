package dev.cacertanalyzer.viewmodel

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cacertanalyzer.data.model.AnalysisResult
import dev.cacertanalyzer.data.model.CertificateInfo
import dev.cacertanalyzer.data.model.CertificateStatistics
import dev.cacertanalyzer.data.model.StorageLocation
import dev.cacertanalyzer.data.model.TrustStatus
import dev.cacertanalyzer.data.repository.AnalysisState
import dev.cacertanalyzer.data.repository.CertificateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the Certificate Analyzer feature.
 *
 * Implements MVVM architecture, exposing UI state through StateFlow
 * and handling all user interactions and business logic.
 */
class CertificateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CertificateRepository = CertificateRepository(application)

    // Exposed states
    val analysisState: StateFlow<AnalysisState> = repository.analysisState
    val analysisResult: StateFlow<AnalysisResult> = repository.analysisResult
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val lastSyncTimestamp: StateFlow<Long?> = repository.lastSyncTimestamp

    // UI-specific state
    private val _selectedTab = MutableStateFlow(CertificateTab.ALL)
    val selectedTab: StateFlow<CertificateTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCertificate = MutableStateFlow<CertificateInfo?>(null)
    val selectedCertificate: StateFlow<CertificateInfo?> = _selectedCertificate.asStateFlow()

    // Filtered certificate list based on search and tab
    val filteredCertificates = combine(
        analysisResult,
        selectedTab,
        searchQuery
    ) { result, tab, query ->
        filterCertificates(result, tab, query)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    // Statistics for dashboard
    val statistics = combine(analysisResult, isLoading) { result, loading ->
        if (loading && result.allCertificates.isEmpty()) {
            null // Don't show stats while loading initial data
        } else {
            result.getStatistics()
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    init {
        Timber.d("CertificateViewModel initialized")
        loadCertificates()
    }

    /**
     * Loads and analyzes certificates.
     */
    fun loadCertificates(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.analyzeCertificates(forceRefresh)
        }
    }

    /**
     * Refreshes CCADB data from network.
     */
    fun refreshCCADBData() {
        viewModelScope.launch {
            repository.refreshCCADBData()
            // Reload analysis with new data
            repository.analyzeCertificates(forceRefresh = true)
        }
    }

    /**
     * Sets the selected tab for filtering.
     */
    fun setTab(tab: CertificateTab) {
        _selectedTab.value = tab
    }

    /**
     * Updates the search query for filtering.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Selects a certificate for detail view.
     */
    fun selectCertificate(certificate: CertificateInfo) {
        _selectedCertificate.value = certificate
    }

    /**
     * Clears the selected certificate (navigate back).
     */
    fun clearSelection() {
        _selectedCertificate.value = null
    }

    /**
     * Creates an Intent to open system Trusted Credentials settings.
     */
    fun openTrustedCredentialsSettings(): Intent {
        // Try the specific trusted credentials intent first
        return Intent("com.android.settings.TRUSTED_CREDENTIALS").apply {
            // Fallback to general security settings if specific intent not available
            if (resolveActivity(getApplication<Application>().packageManager) == null) {
                action = Settings.ACTION_SECURITY_SETTINGS
            }
        }
    }

    /**
     * Clears cache and reloads.
     */
    fun clearCacheAndReload() {
        viewModelScope.launch {
            repository.clearCache()
            loadCertificates(forceRefresh = true)
        }
    }

    /**
     * Filters certificates based on tab selection and search query.
     */
    private fun filterCertificates(
        result: AnalysisResult,
        tab: CertificateTab,
        query: String
    ): List<CertificateInfo> {
        val baseList = when (tab) {
            CertificateTab.ALL -> result.allCertificates
            CertificateTab.SYSTEM -> result.verifiedSystemCerts + result.unverifiedSystemCerts
            CertificateTab.USER -> result.userInstalledCerts
            CertificateTab.UNVERIFIED -> result.unverifiedSystemCerts
            CertificateTab.FLAGGED -> result.unverifiedSystemCerts + result.userInstalledCerts + result.expiredCerts
        }

        if (query.isBlank()) {
            return baseList
        }

        val normalizedQuery = query.lowercase()
        return baseList.filter { cert ->
            cert.subjectCommonName.lowercase().contains(normalizedQuery) ||
            cert.issuerCommonName.lowercase().contains(normalizedQuery) ||
            cert.sha256Fingerprint.lowercase().replace(":", "").contains(normalizedQuery.replace(":", "")) ||
            cert.serialNumber.lowercase().contains(normalizedQuery)
        }
    }

    /**
     * Gets a user-friendly description of the trust status.
     */
    fun getTrustStatusDescription(cert: CertificateInfo): String {
        return when (cert.trustStatus) {
            TrustStatus.VERIFIED_PUBLIC_ROOT ->
                "This certificate is recognized by major root programs (${cert.rootPrograms.joinToString { it.programName }})."
            TrustStatus.UNVERIFIED_SYSTEM_ROOT ->
                "This system certificate is not found in public trust databases. It may be an OEM, carrier, or enterprise-specific root CA. Review carefully."
            TrustStatus.USER_INSTALLED_ROOT ->
                "⚠️ This certificate was manually installed by the user or an app. It can intercept HTTPS traffic. Verify this is intentional and trustworthy."
            TrustStatus.EXPIRED ->
                "❌ This certificate has expired and should not be trusted."
            TrustStatus.NOT_YET_VALID ->
                "This certificate is not yet valid (future activation date)."
            TrustStatus.EXPIRING_SOON ->
                "⚠️ This certificate will expire soon (${cert.validTo})."
        }
    }

    /**
     * Gets a color-coded risk level for the certificate.
     */
    fun getRiskLevel(cert: CertificateInfo): RiskLevel {
        return when (cert.trustStatus) {
            TrustStatus.VERIFIED_PUBLIC_ROOT -> RiskLevel.LOW
            TrustStatus.UNVERIFIED_SYSTEM_ROOT -> RiskLevel.MEDIUM
            TrustStatus.USER_INSTALLED_ROOT -> RiskLevel.HIGH
            TrustStatus.EXPIRED -> RiskLevel.CRITICAL
            TrustStatus.EXPIRING_SOON -> RiskLevel.MEDIUM
            TrustStatus.NOT_YET_VALID -> RiskLevel.MEDIUM
        }
    }
}

/**
 * Tab options for certificate filtering.
 */
enum class CertificateTab(val label: String) {
    ALL("All"),
    SYSTEM("System"),
    USER("User Installed"),
    UNVERIFIED("Unverified"),
    FLAGGED("Flagged")
}

/**
 * Risk levels for certificate coloring.
 */
enum class RiskLevel {
    LOW,      // Green - verified public root
    MEDIUM,   // Yellow - unverified or expiring soon
    HIGH,     // Orange - user installed
    CRITICAL  // Red - expired or dangerous
}
