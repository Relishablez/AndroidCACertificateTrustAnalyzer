package dev.cacertanalyzer.data.model

import java.security.cert.X509Certificate
import java.util.Date

/**
 * Represents the trust status of a certificate based on CCADB cross-referencing.
 */
enum class TrustStatus {
    /**
     * System certificate found in CCADB public trust store.
     * This is a standard, publicly recognized root CA.
     */
    VERIFIED_PUBLIC_ROOT,

    /**
     * System certificate NOT found in CCADB.
     * Could be OEM-specific, carrier-specific, or enterprise-supply chain roots.
     * Requires user attention for verification.
     */
    UNVERIFIED_SYSTEM_ROOT,

    /**
     * Certificate installed in the user store.
     * High risk for MITM interception.
     */
    USER_INSTALLED_ROOT,

    /**
     * Certificate has expired.
     */
    EXPIRED,

    /**
     * Certificate is not yet valid (future activation date).
     */
    NOT_YET_VALID,

    /**
     * Certificate will expire within warning threshold (e.g., 30 days).
     */
    EXPIRING_SOON
}

/**
 * Storage location of the certificate on the device.
 */
enum class StorageLocation {
    SYSTEM,  // /system/etc/security/cacerts or APEX Conscrypt module
    USER     // /data/misc/user/0/cacerts-added/
}

/**
 * Wrapper data class for X.509 certificate information.
 * Extracts and formats certificate fields for UI presentation and analysis.
 */
data class CertificateInfo(
    /** Alias/identifier used by the Android KeyStore */
    val alias: String,

    /** The underlying X.509 certificate object */
    val certificate: X509Certificate,

    /** Storage location: System or User */
    val storageLocation: StorageLocation,

    /** Computed trust status based on CCADB cross-reference */
    var trustStatus: TrustStatus = TrustStatus.UNVERIFIED_SYSTEM_ROOT,

    /** Root programs this certificate belongs to (from CCADB) */
    val rootPrograms: MutableList<RootProgram> = mutableListOf()
) {
    /** Subject Distinguished Name */
    val subjectDN: String
        get() = certificate.subjectX500Principal.name

    /** Issuer Distinguished Name */
    val issuerDN: String
        get() = certificate.issuerX500Principal.name

    /** Certificate validity start date */
    val validFrom: Date
        get() = certificate.notBefore

    /** Certificate validity end date */
    val validTo: Date
        get() = certificate.notAfter

    /** SHA-256 fingerprint of the certificate */
    val sha256Fingerprint: String by lazy {
        computeSha256Fingerprint(certificate)
    }

    /** SHA-1 fingerprint (for reference) */
    val sha1Fingerprint: String by lazy {
        computeSha1Fingerprint(certificate)
    }

    /** Serial number of the certificate */
    val serialNumber: String
        get() = certificate.serialNumber.toString(16).uppercase()

    /** Signature algorithm used */
    val signatureAlgorithm: String
        get() = certificate.sigAlgName

    /** Key algorithm (e.g., RSA, EC) */
    val keyAlgorithm: String
        get() = certificate.publicKey.algorithm

    /** Key size in bits (computed from public key) */
    val keySize: Int by lazy {
        computeKeySize(certificate)
    }

    /** Human-readable subject common name */
    val subjectCommonName: String by lazy {
        extractCommonName(certificate.subjectX500Principal.name)
    }

    /** Human-readable issuer common name */
    val issuerCommonName: String by lazy {
        extractCommonName(certificate.issuerX500Principal.name)
    }

    /** Full PEM-encoded certificate */
    val pemEncoded: String by lazy {
        encodeToPem(certificate)
    }

    /**
     * Determines if the certificate is currently valid.
     */
    fun isValid(now: Date = Date()): Boolean {
        return now.after(validFrom) && now.before(validTo)
    }

    /**
     * Checks if certificate is self-signed (root CA).
     */
    fun isSelfSigned(): Boolean {
        return subjectDN == issuerDN
    }

    /**
     * Adds root program information from CCADB mapping.
     */
    fun addRootProgram(program: RootProgram) {
        rootPrograms.add(program)
    }

    companion object {
        private fun computeSha256Fingerprint(cert: X509Certificate): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(cert.encoded)
            return hash.joinToString(":") { "%02X".format(it) }
        }

        private fun computeSha1Fingerprint(cert: X509Certificate): String {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            val hash = digest.digest(cert.encoded)
            return hash.joinToString(":") { "%02X".format(it) }
        }

        private fun computeKeySize(cert: X509Certificate): Int {
            val key = cert.publicKey
            return when (key.algorithm) {
                "RSA" -> {
                    // RSA key size from modulus
                    val modulusField = key.javaClass.getDeclaredField("modulus")
                    modulusField.isAccessible = true
                    val modulus = modulusField.get(key) as java.math.BigInteger
                    modulus.bitLength()
                }
                "EC" -> {
                    // EC key size from parameters
                    val paramsField = key.javaClass.getDeclaredField("params")
                    paramsField.isAccessible = true
                    val params = paramsField.get(key) as java.security.spec.ECParameterSpec
                    params.order.bitLength()
                }
                else -> -1 // Unknown
            }
        }

        private fun extractCommonName(dn: String): String {
            // Extract CN= value from DN string
            val cnRegex = Regex("CN=([^,]+)", RegexOption.IGNORE_CASE)
            val match = cnRegex.find(dn)
            return match?.groupValues?.get(1)?.trim() ?: dn
        }

        private fun encodeToPem(cert: X509Certificate): String {
            val encoder = java.util.Base64.getMimeEncoder(64, "\n".toByteArray())
            val base64 = encoder.encodeToString(cert.encoded)
            return buildString {
                appendLine("-----BEGIN CERTIFICATE-----")
                appendLine(base64)
                appendLine("-----END CERTIFICATE-----")
            }
        }
    }
}

/**
 * Represents a root program from CCADB (e.g., Mozilla, Google, Microsoft, Apple).
 */
data class RootProgram(
    /** Name of the root program (e.g., "Mozilla", "Google", "Microsoft") */
    val programName: String,

    /** Whether this program trusts this certificate for server authentication */
    val serverAuthTrusted: Boolean,

    /** Whether this program trusts this certificate for email (S/MIME) */
    val emailTrusted: Boolean,

    /** Whether this program trusts this certificate for code signing */
    val codeSigningTrusted: Boolean,

    /** Policy OID associated with this trust bit */
    val policyOid: String? = null
)
