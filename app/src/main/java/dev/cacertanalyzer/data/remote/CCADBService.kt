package dev.cacertanalyzer.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for downloading CCADB data from the internet.
 *
 * Uses OkHttp for efficient HTTP operations with proper timeout handling.
 */
class CCADBService {

    companion object {
        // CCADB public report URLs
        const val CCADB_CSV_URL = "https://ccadb-public.secure.force.com/ccadb/AllIncludedRootCertificateTrustBitSettingsPEMCSV"
        const val CCADB_JSON_URL = "https://ccadb-public.secure.force.com/ccadb/AllIncludedRootCertificateTrustBitSettingsJSON"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Downloads the CCADB CSV report.
     *
     * @return The CSV content as a String, or null if download failed
     */
    suspend fun downloadCCADBCsv(url: String = CCADB_CSV_URL): String? = withContext(Dispatchers.IO) {
        return@withContext downloadContent(url)
    }

    /**
     * Downloads content from the specified URL.
     *
     * @param url The URL to download from
     * @return The content as a String, or null if download failed
     */
    private suspend fun downloadContent(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/csv,application/json,text/plain,*/*")
            .header("Accept-Charset", "UTF-8")
            .header("User-Agent", "CACertAnalyzer/1.0 (Android; Open Source)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Timber.d("Downloaded ${body?.length ?: 0} bytes from $url")
                    body
                } else {
                    Timber.w("HTTP ${response.code} when downloading from $url")
                    null
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "Network error downloading CCADB data from $url")
            null
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error downloading CCADB data")
            null
        }
    }

    /**
     * Checks if the CCADB server is reachable.
     *
     * @return true if server responds with HTTP 200
     */
    suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CCADB_CSV_URL)
            .head() // Use HEAD request to check availability without downloading
            .build()

        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.w(e, "CCADB server not available")
            false
        }
    }
}
