package dev.cacertanalyzer.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.cacertanalyzer.data.local.CCADBCache
import dev.cacertanalyzer.data.remote.CCADBService
import timber.log.Timber

/**
 * Background worker for syncing CCADB data.
 *
 * Scheduled to run periodically to keep the certificate database up-to-date.
 */
class CCADBSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "ccadb_sync_work"
        const val WORK_TAG = "ccadb_sync"
    }

    private val ccadbService = CCADBService()
    private val ccadbCache = CCADBCache(context)

    override suspend fun doWork(): Result {
        Timber.d("Starting CCADB sync worker")

        return try {
            // Download fresh CCADB data
            val csvContent = ccadbService.downloadCCADBCsv()

            if (csvContent != null) {
                // Save to cache
                ccadbCache.saveCCADBData(csvContent, System.currentTimeMillis())
                Timber.d("CCADB sync completed successfully (${csvContent.length} bytes)")
                Result.success()
            } else {
                Timber.w("CCADB sync failed: no data downloaded")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "CCADB sync failed with exception")
            Result.retry()
        }
    }
}
