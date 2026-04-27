package dev.cacertanalyzer.data.model

/**
 * Represents a single record from the Common CA Database (CCADB).
 * This maps to the CSV format provided by CCADB's "All Included Root Certificate
 * Trust Bit Settings" report.
 *
 * Reference: https://www.ccadb.org/resources
 */
data class CCADBRecord(
    /** Certificate Name (Friendly name) */
    val certificateName: String,

    /** SHA-256 fingerprint of the certificate (primary matching key) */
    val sha256Fingerprint: String,

    /** SHA-1 fingerprint (for reference) */
    val sha1Fingerprint: String,

    /** Subject Distinguished Name */
    val subjectDN: String,

    /** Issuer Distinguished Name */
    val issuerDN: String,

    /** Serial number in hex format */
    val serialNumber: String,

    /** Valid From date */
    val validFrom: String,

    /** Valid To date */
    val validTo: String,

    /** Mozilla trust status (Included, Distrusted, etc.) */
    val mozillaStatus: String,

    /** Google Chromium trust status */
    val googleStatus: String,

    /** Microsoft Windows trust status */
    val microsoftStatus: String,

    /** Apple trust status */
    val appleStatus: String,

    /** Server Authentication trust bit (from Mozilla) */
    val serverAuthTrusted: Boolean,

    /** Secure Email (S/MIME) trust bit */
    val emailTrusted: Boolean,

    /** Code Signing trust bit */
    val codeSigningTrusted: Boolean,

    /** EV Policy OID if applicable */
    val evPolicyOid: String? = null,

    /** Certification Practice Statement URL */
    val cpsUrl: String? = null
) {
    /**
     * Returns a list of root programs that include this certificate.
     */
    fun getIncludedRootPrograms(): List<String> {
        val programs = mutableListOf<String>()
        if (mozillaStatus.lowercase().contains("included")) programs.add("Mozilla")
        if (googleStatus.lowercase().contains("included")) programs.add("Google")
        if (microsoftStatus.lowercase().contains("included")) programs.add("Microsoft")
        if (appleStatus.lowercase().contains("included")) programs.add("Apple")
        return programs
    }

    /**
     * Creates a RootProgram list from this record.
     */
    fun toRootPrograms(): List<RootProgram> {
        val programs = mutableListOf<RootProgram>()

        if (mozillaStatus.lowercase().contains("included")) {
            programs.add(
                RootProgram(
                    programName = "Mozilla",
                    serverAuthTrusted = serverAuthTrusted,
                    emailTrusted = emailTrusted,
                    codeSigningTrusted = codeSigningTrusted,
                    policyOid = evPolicyOid
                )
            )
        }

        if (googleStatus.lowercase().contains("included")) {
            programs.add(
                RootProgram(
                    programName = "Google",
                    serverAuthTrusted = serverAuthTrusted,
                    emailTrusted = emailTrusted,
                    codeSigningTrusted = codeSigningTrusted,
                    policyOid = evPolicyOid
                )
            )
        }

        if (microsoftStatus.lowercase().contains("included")) {
            programs.add(
                RootProgram(
                    programName = "Microsoft",
                    serverAuthTrusted = serverAuthTrusted,
                    emailTrusted = emailTrusted,
                    codeSigningTrusted = codeSigningTrusted,
                    policyOid = evPolicyOid
                )
            )
        }

        if (appleStatus.lowercase().contains("included")) {
            programs.add(
                RootProgram(
                    programName = "Apple",
                    serverAuthTrusted = serverAuthTrusted,
                    emailTrusted = emailTrusted,
                    codeSigningTrusted = codeSigningTrusted,
                    policyOid = evPolicyOid
                )
            )
        }

        return programs
    }

    companion object {
        /**
         * Normalizes SHA-256 fingerprint by removing colons and spaces,
         * converting to uppercase for consistent comparison.
         */
        fun normalizeFingerprint(fingerprint: String): String {
            return fingerprint.replace(":", "")
                .replace(" ", "")
                .replace("-", "")
                .uppercase()
        }
    }
}
