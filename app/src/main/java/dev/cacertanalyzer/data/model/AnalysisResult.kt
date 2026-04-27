package dev.cacertanalyzer.data.model

/**
 * Represents the complete analysis result after cross-referencing
 * device certificates against the CCADB database.
 */
data class AnalysisResult(
    /** All certificates found on the device */
    val allCertificates: List<CertificateInfo> = emptyList(),

    /** System certificates verified in CCADB (publicly trusted) */
    val verifiedSystemCerts: List<CertificateInfo> = emptyList(),

    /** System certificates NOT found in CCADB (OEM/Carrier/Enterprise) */
    val unverifiedSystemCerts: List<CertificateInfo> = emptyList(),

    /** User-installed certificates (high risk) */
    val userInstalledCerts: List<CertificateInfo> = emptyList(),

    /** Certificates that are expired */
    val expiredCerts: List<CertificateInfo> = emptyList(),

    /** Certificates expiring soon */
    val expiringSoonCerts: List<CertificateInfo> = emptyList(),

    /** Timestamp when analysis was performed */
    val analysisTimestamp: Long = System.currentTimeMillis(),

    /** Whether CCADB data was available for cross-referencing */
    val ccadbDataAvailable: Boolean = false
) {
    val totalCount: Int get() = allCertificates.size
    val systemCount: Int get() = verifiedSystemCerts.size + unverifiedSystemCerts.size
    val userCount: Int get() = userInstalledCerts.size
    val flaggedCount: Int get() = unverifiedSystemCerts.size + userInstalledCerts.size + expiredCerts.size

    /**
     * Returns statistics for display in dashboard.
     */
    fun getStatistics(): CertificateStatistics {
        return CertificateStatistics(
            totalSystemCerts = systemCount,
            verifiedPublicRoots = verifiedSystemCerts.size,
            unverifiedSystemRoots = unverifiedSystemCerts.size,
            userInstalledRoots = userCount,
            expiredCerts = expiredCerts.size,
            expiringSoonCerts = expiringSoonCerts.size,
            highRiskCount = userInstalledCerts.size + expiredCerts.size
        )
    }
}

/**
 * Statistics summary for dashboard display.
 */
data class CertificateStatistics(
    val totalSystemCerts: Int = 0,
    val verifiedPublicRoots: Int = 0,
    val unverifiedSystemRoots: Int = 0,
    val userInstalledRoots: Int = 0,
    val expiredCerts: Int = 0,
    val expiringSoonCerts: Int = 0,
    val highRiskCount: Int = 0
)
