package dev.cacertanalyzer

import android.app.Application
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.cacertanalyzer.worker.CCADBSyncWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Application class for CA Certificate Trust Analyzer.
 *
 * Initializes logging and schedules background sync work for CCADB data.
 */
class CertAnalyzerApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("CertAnalyzerApplication initialized")

        // Schedule periodic CCADB sync
        scheduleCCADBSync()
    }

    /**
     * Schedules periodic background sync of CCADB data.
     * Runs once per week when network is available and device is charging.
     */
    private fun scheduleCCADBSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<CCADBSyncWorker>(
            7, TimeUnit.DAYS // Run once per week
        )
            .setConstraints(constraints)
            .addTag(CCADBSyncWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CCADBSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if scheduled
            syncWorkRequest
        )

        Timber.d("Scheduled CCADB sync worker")
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
    }
}
