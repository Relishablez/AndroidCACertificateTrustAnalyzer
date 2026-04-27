package dev.cacertanalyzer.data.remote

import com.opencsv.CSVReader
import dev.cacertanalyzer.data.model.CCADBRecord
import dev.cacertanalyzer.data.model.RootProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parser for the Common CA Database (CCADB) CSV format.
 *
 * CCADB provides public reports in CSV format containing trust bit settings
 * for root certificates across multiple programs (Mozilla, Google, Microsoft, Apple).
 *
 * Data source: https://ccadb-public.secure.force.com/ccadb/AllIncludedRootCertificateTrustBitSettingsPEMCSV
 *
 * Expected CSV columns (as of 2024):
 * - Certificate Name
 * - Certificate Serial Number
 * - SHA-256 Fingerprint
 * - SHA-1 Fingerprint
 * - Subject Distinguished Name
 * - Issuer Distinguished Name
 * - Valid From [GMT]
 * - Valid To [GMT]
 * - PEM Info
 * - Trust Bits
 * - Mozilla Status
 * - Microsoft Status
 * - Apple Status
 * - Google Status
 * - EV Policy OID(s)
 * - Certification Practice Statement (CPS) URL
 */
class CCADBParser {

    companion object {
        // Default CCADB CSV download URL
        const val CCADB_CSV_URL = "https://ccadb-public.secure.force.com/ccadb/AllIncludedRootCertificateTrustBitSettingsPEMCSV"

        // CSV column headers (may vary, we map them dynamically)
        private val COLUMN_SHA256 = listOf("SHA-256 Fingerprint", "SHA256 Fingerprint", "Fingerprint SHA256", "sha256")
        private val COLUMN_SHA1 = listOf("SHA-1 Fingerprint", "SHA1 Fingerprint", "Fingerprint SHA1", "sha1")
        private val COLUMN_SUBJECT_DN = listOf("Subject Distinguished Name", "Subject", "subjectDN")
        private val COLUMN_ISSUER_DN = listOf("Issuer Distinguished Name", "Issuer", "issuerDN")
        private val COLUMN_SERIAL = listOf("Certificate Serial Number", "Serial Number", "Serial")
        private val COLUMN_VALID_FROM = listOf("Valid From [GMT]", "Valid From", "Not Before")
        private val COLUMN_VALID_TO = listOf("Valid To [GMT]", "Valid To", "Not After")
        private val COLUMN_CERT_NAME = listOf("Certificate Name", "Common Name", "CN")
        private val COLUMN_MOZILLA_STATUS = listOf("Mozilla Status")
        private val COLUMN_GOOGLE_STATUS = listOf("Google Status", "Chromium Status")
        private val COLUMN_MICROSOFT_STATUS = listOf("Microsoft Status")
        private val COLUMN_APPLE_STATUS = listOf("Apple Status")
        private val COLUMN_TRUST_BITS = listOf("Trust Bits")
        private val COLUMN_EV_POLICY = listOf("EV Policy OID(s)", "EV Policy OIDs")
        private val COLUMN_CPS_URL = listOf("Certification Practice Statement (CPS) URL", "CPS URL")

        private val DATE_FORMATS = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US)
        )
    }

    /**
     * Parses CCADB CSV content into a list of records.
     *
     * @param csvContent The raw CSV content as a string
     * @return List of CCADBRecord objects
     */
    suspend fun parse(csvContent: String): List<CCADBRecord> = withContext(Dispatchers.Default) {
        val records = mutableListOf<CCADBRecord>()

        try {
            CSVReader(StringReader(csvContent)).use { reader ->
                val headerLine = reader.readNext()
                if (headerLine == null) {
                    Timber.w("Empty CSV content")
                    return@withContext emptyList()
                }

                // Map column indices from header
                val columnMap = mapColumns(headerLine)
                Timber.d("CSV column mapping: $columnMap")

                if (!columnMap.containsKey("sha256")) {
                    Timber.e("Required column 'SHA-256 Fingerprint' not found in CSV")
                    return@withContext emptyList()
                }

                var lineNumber = 2 // Start at 2 (1-based, after header)
                var line: Array<String>?

                while (reader.readNext().also { line = it } != null) {
                    try {
                        line?.let { columns ->
                            val record = parseRecord(columns, columnMap)
                            if (record != null) {
                                records.add(record)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error parsing CSV record at line $lineNumber")
                    }
                    lineNumber++
                }
            }

            Timber.d("Successfully parsed ${records.size} CCADB records")

        } catch (e: IOException) {
            Timber.e(e, "Error reading CSV content")
        }

        return@withContext records
    }

    /**
     * Maps CSV header columns to standardized field names.
     */
    private fun mapColumns(headers: Array<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()

        headers.forEachIndexed { index, header ->
            val normalizedHeader = header.trim().replace(Regex("\\s+"), " ")

            when {
                COLUMN_SHA256.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["sha256"] = index
                COLUMN_SHA1.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["sha1"] = index
                COLUMN_SUBJECT_DN.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["subjectDN"] = index
                COLUMN_ISSUER_DN.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["issuerDN"] = index
                COLUMN_SERIAL.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["serial"] = index
                COLUMN_VALID_FROM.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["validFrom"] = index
                COLUMN_VALID_TO.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["validTo"] = index
                COLUMN_CERT_NAME.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["certName"] = index
                COLUMN_MOZILLA_STATUS.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["mozillaStatus"] = index
                COLUMN_GOOGLE_STATUS.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["googleStatus"] = index
                COLUMN_MICROSOFT_STATUS.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["microsoftStatus"] = index
                COLUMN_APPLE_STATUS.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["appleStatus"] = index
                COLUMN_TRUST_BITS.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["trustBits"] = index
                COLUMN_EV_POLICY.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["evPolicy"] = index
                COLUMN_CPS_URL.any { normalizedHeader.equals(it, ignoreCase = true) } ->
                    map["cpsUrl"] = index
            }
        }

        return map
    }

    /**
     * Parses a single CSV row into a CCADBRecord.
     */
    private fun parseRecord(columns: Array<String>, columnMap: Map<String, Int>): CCADBRecord? {
        val sha256Index = columnMap["sha256"] ?: return null

        val sha256Fingerprint = normalizeFingerprint(getColumn(columns, sha256Index))
        if (sha256Fingerprint.isBlank()) {
            return null
        }

        // Parse trust bits if available
        val trustBits = columnMap["trustBits"]?.let { getColumn(columns, it) } ?: ""
        val serverAuth = trustBits.contains("Websites", ignoreCase = true) ||
                trustBits.contains("Server Authentication", ignoreCase = true)
        val email = trustBits.contains("Email", ignoreCase = true) ||
                trustBits.contains("Secure Email", ignoreCase = true)
        val codeSigning = trustBits.contains("Code Signing", ignoreCase = true)

        return CCADBRecord(
            certificateName = columnMap["certName"]?.let { getColumn(columns, it) } ?: "Unknown",
            sha256Fingerprint = sha256Fingerprint,
            sha1Fingerprint = columnMap["sha1"]?.let { normalizeFingerprint(getColumn(columns, it)) } ?: "",
            subjectDN = columnMap["subjectDN"]?.let { getColumn(columns, it) } ?: "",
            issuerDN = columnMap["issuerDN"]?.let { getColumn(columns, it) } ?: "",
            serialNumber = columnMap["serial"]?.let { getColumn(columns, it) } ?: "",
            validFrom = columnMap["validFrom"]?.let { getColumn(columns, it) } ?: "",
            validTo = columnMap["validTo"]?.let { getColumn(columns, it) } ?: "",
            mozillaStatus = columnMap["mozillaStatus"]?.let { getColumn(columns, it) } ?: "Unknown",
            googleStatus = columnMap["googleStatus"]?.let { getColumn(columns, it) } ?: "Unknown",
            microsoftStatus = columnMap["microsoftStatus"]?.let { getColumn(columns, it) } ?: "Unknown",
            appleStatus = columnMap["appleStatus"]?.let { getColumn(columns, it) } ?: "Unknown",
            serverAuthTrusted = serverAuth,
            emailTrusted = email,
            codeSigningTrusted = codeSigning,
            evPolicyOid = columnMap["evPolicy"]?.let { getColumn(columns, it) },
            cpsUrl = columnMap["cpsUrl"]?.let { getColumn(columns, it) }
        )
    }

    private fun getColumn(columns: Array<String>, index: Int): String {
        return if (index >= 0 && index < columns.size) {
            columns[index].trim()
        } else {
            ""
        }
    }

    private fun normalizeFingerprint(fingerprint: String): String {
        return fingerprint.replace(":", "")
            .replace(" ", "")
            .replace("-", "")
            .uppercase()
    }
}

/**
 * Service for cross-referencing device certificates against CCADB data.
 */
class CCADBCrossReferenceService {

    /**
     * Cross-references a SHA-256 fingerprint against the CCADB records.
     *
     * @param fingerprint The SHA-256 fingerprint to look up (with or without colons)
     * @param ccadbRecords The parsed CCADB database
     * @return The matching CCADBRecord, or null if not found
     */
    fun findByFingerprint(fingerprint: String, ccadbRecords: List<CCADBRecord>): CCADBRecord? {
        val normalizedFingerprint = CCADBRecord.normalizeFingerprint(fingerprint)
        return ccadbRecords.find { record ->
            record.sha256Fingerprint.equals(normalizedFingerprint, ignoreCase = true)
        }
    }

    /**
     * Performs batch cross-referencing for multiple certificates.
     *
     * @param fingerprints Map of certificate ID to fingerprint
     * @param ccadbRecords The parsed CCADB database
     * @return Map of certificate ID to matching CCADBRecord (only for matches)
     */
    fun crossReferenceBatch(
        fingerprints: Map<String, String>,
        ccadbRecords: List<CCADBRecord>
    ): Map<String, CCADBRecord> {
        val results = mutableMapOf<String, CCADBRecord>()

        // Create lookup map for O(1) searching
        val ccadbLookup = ccadbRecords.associateBy { it.sha256Fingerprint.uppercase() }

        fingerprints.forEach { (id, fingerprint) ->
            val normalized = CCADBRecord.normalizeFingerprint(fingerprint)
            ccadbLookup[normalized]?.let { record ->
                results[id] = record
            }
        }

        return results
    }

    /**
     * Extracts RootProgram information from a CCADB match.
     */
    fun extractRootPrograms(record: CCADBRecord): List<RootProgram> {
        return record.toRootPrograms()
    }
}
