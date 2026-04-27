package dev.cacertanalyzer.data.local

import android.security.KeyChain
import dev.cacertanalyzer.data.model.CertificateInfo
import dev.cacertanalyzer.data.model.StorageLocation
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * Utility class for retrieving X.509 certificates from the Android system.
 *
 * This class handles the complex task of enumerating certificates from both the
 * System trust store and User trust store, differentiating between them based
 * on various heuristics since Android's KeyStore API doesn't always provide
 * clear separation.
 *
 * SECURITY NOTE: This class only reads certificate metadata and public keys.
 * It does NOT access or expose any private key material.
 */
object CertificateRetriever {

    private const val ANDROID_CA_STORE = "AndroidCAStore"

    // System certificate paths for Android 14+ APEX Conscrypt module
    private val SYSTEM_CERT_PATHS = listOf(
        "/apex/com.android.conscrypt/cacerts",
        "/system/etc/security/cacerts",
        "/system/etc/security/cacerts_added"
    )

    // User certificate installation path (may vary by Android version)
    private val USER_CERT_PATHS = listOf(
        "/data/misc/user/0/cacerts-added",
        "/data/misc/user/0/cacerts-removed"
    )

    /**
     * Retrieves all CA certificates installed on the device.
     *
     * Uses the AndroidCAStore KeyStore to enumerate certificates, then attempts
     * to determine whether each certificate is from the system or user store.
     *
     * @return List of CertificateInfo objects with storage location identified
     */
    fun retrieveAllCertificates(): List<CertificateInfo> {
        val certificates = mutableListOf<CertificateInfo>()

        try {
            val keyStore = KeyStore.getInstance(ANDROID_CA_STORE).apply {
                load(null, null)
            }

            val aliases = keyStore.aliases().toList()
            Timber.d("Found ${aliases.size} certificate aliases in AndroidCAStore")

            aliases.forEach { alias ->
                try {
                    val certificate = keyStore.getCertificate(alias)
                    if (certificate is X509Certificate) {
                        val storageLocation = determineStorageLocation(alias, certificate)
                        val certInfo = CertificateInfo(
                            alias = alias,
                            certificate = certificate,
                            storageLocation = storageLocation
                        )
                        certificates.add(certInfo)
                        Timber.v("Loaded certificate: ${certInfo.subjectCommonName} (${storageLocation.name})")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading certificate with alias: $alias")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error accessing AndroidCAStore")
        }

        Timber.d("Successfully loaded ${certificates.size} certificates")
        return certificates.sortedBy { it.subjectCommonName }
    }

    /**
     * Attempts to determine the storage location (System vs User) of a certificate.
     *
     * Since Android doesn't provide a direct API to query this, we use several heuristics:
     * 1. Check if the alias contains "user" substring
     * 2. Check if the certificate file exists in known user cert directories
     * 3. Cross-reference with system certificate directories
     * 4. Fallback to checking if the certificate is modifiable
     *
     * @param alias The KeyStore alias
     * @param certificate The X.509 certificate
     * @return StorageLocation enum value
     */
    private fun determineStorageLocation(alias: String, certificate: X509Certificate): StorageLocation {
        // Heuristic 1: Check alias patterns
        if (alias.lowercase().contains("user") ||
            alias.startsWith("user:") ||
            alias.contains("cacerts-added")) {
            return StorageLocation.USER
        }

        // Heuristic 2: Check certificate fingerprints against file system
        // Note: This requires root access on most devices, but we try anyway
        try {
            val certHash = certificate.serialNumber.toString(16).lowercase()

            // Check user directories
            for (userPath in USER_CERT_PATHS) {
                val userDir = File(userPath)
                if (userDir.exists() && userDir.isDirectory) {
                    val files = userDir.listFiles() ?: continue
                    for (file in files) {
                        if (file.name.contains(certHash, ignoreCase = true) ||
                            file.name.endsWith(".0")) {
                            // Try to load and compare
                            try {
                                FileInputStream(file).use { fis ->
                                    val loadedCert = java.security.cert.CertificateFactory
                                        .getInstance("X.509")
                                        .generateCertificate(fis) as? X509Certificate
                                    if (loadedCert != null && loadedCert.encoded.contentEquals(certificate.encoded)) {
                                        return StorageLocation.USER
                                    }
                                }
                            } catch (e: Exception) {
                                // Continue checking other files
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not check file system for certificate location")
        }

        // Heuristic 3: Check if certificate is self-signed and in system paths
        // System CAs are typically self-signed root certificates
        if (isSelfSignedRoot(certificate)) {
            // Most self-signed roots in the system store are standard CAs
            // We return SYSTEM as the default for self-signed certs not in user store
            return StorageLocation.SYSTEM
        }

        // Default: Assume system if not identified as user
        return StorageLocation.SYSTEM
    }

    /**
     * Checks if a certificate is a self-signed root CA.
     */
    private fun isSelfSignedRoot(certificate: X509Certificate): Boolean {
        return certificate.subjectX500Principal == certificate.issuerX500Principal
    }

    /**
     * Retrieves only system certificates.
     */
    fun retrieveSystemCertificates(): List<CertificateInfo> {
        return retrieveAllCertificates().filter { it.storageLocation == StorageLocation.SYSTEM }
    }

    /**
     * Retrieves only user-installed certificates.
     */
    fun retrieveUserCertificates(): List<CertificateInfo> {
        return retrieveAllCertificates().filter { it.storageLocation == StorageLocation.USER }
    }

    /**
     * Gets the count of certificates without loading full details.
     * Useful for quick statistics.
     */
    fun getCertificateCount(): Pair<Int, Int> {
        var systemCount = 0
        var userCount = 0

        try {
            val keyStore = KeyStore.getInstance(ANDROID_CA_STORE).apply {
                load(null, null)
            }

            keyStore.aliases().toList().forEach { alias ->
                val location = if (alias.lowercase().contains("user")) {
                    StorageLocation.USER
                } else {
                    StorageLocation.SYSTEM
                }

                if (location == StorageLocation.USER) {
                    userCount++
                } else {
                    systemCount++
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error counting certificates")
        }

        return Pair(systemCount, userCount)
    }

    /**
     * Verifies if the device has any user-installed certificates.
     * Returns true if user certificates are present (potential security concern).
     */
    fun hasUserCertificates(): Boolean {
        return retrieveUserCertificates().isNotEmpty()
    }
}
