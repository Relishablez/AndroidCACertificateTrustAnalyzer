package dev.cacertanalyzer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File

// Extension for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ccadb_cache")

/**
 * Cache manager for CCADB data.
 *
 * Stores the raw CCADB CSV data locally for offline use and tracks
 * the last sync timestamp for cache freshness determination.
 */
class CCADBCache(private val context: Context) {

    companion object {
        private val CCADB_DATA_KEY = stringPreferencesKey("ccadb_csv_data")
        private val LAST_SYNC_KEY = longPreferencesKey("ccadb_last_sync")
        private const val CACHE_FILE_NAME = "ccadb_data.csv"
    }

    private val cacheDir: File by lazy {
        File(context.filesDir, "ccadb_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private val cacheFile: File by lazy {
        File(cacheDir, CACHE_FILE_NAME)
    }

    /**
     * Saves CCADB data to cache.
     *
     * @param csvContent The raw CSV content
     * @param timestamp The sync timestamp
     */
    suspend fun saveCCADBData(csvContent: String, timestamp: Long) {
        try {
            // Save to file for large data
            cacheFile.writeText(csvContent)

            // Save metadata to DataStore
            context.dataStore.edit { preferences ->
                preferences[LAST_SYNC_KEY] = timestamp
            }

            Timber.d("CCADB data cached successfully (${csvContent.length} bytes)")
        } catch (e: Exception) {
            Timber.e(e, "Error saving CCADB cache")
        }
    }

    /**
     * Loads cached CCADB data.
     *
     * @return The CSV content, or null if no cache exists
     */
    suspend fun loadCCADBData(): String? {
        return try {
            if (cacheFile.exists()) {
                val content = cacheFile.readText()
                Timber.d("Loaded CCADB data from cache (${content.length} bytes)")
                content
            } else {
                // Try loading from DataStore (fallback for older data)
                val preferences = context.dataStore.data.first()
                preferences[CCADB_DATA_KEY]?.also {
                    // Migrate to file storage
                    cacheFile.writeText(it)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading CCADB cache")
            null
        }
    }

    /**
     * Gets the last sync timestamp.
     *
     * @return The timestamp, or null if never synced
     */
    suspend fun getLastSyncTimestamp(): Long? {
        return try {
            context.dataStore.data.map { preferences ->
                preferences[LAST_SYNC_KEY]
            }.first()
        } catch (e: Exception) {
            Timber.e(e, "Error reading sync timestamp")
            null
        }
    }

    /**
     * Clears all cached data.
     */
    suspend fun clear() {
        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            context.dataStore.edit { preferences ->
                preferences.remove(CCADB_DATA_KEY)
                preferences.remove(LAST_SYNC_KEY)
            }
            Timber.d("CCADB cache cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing CCADB cache")
        }
    }

    /**
     * Checks if cache exists.
     */
    fun hasCache(): Boolean {
        return cacheFile.exists() && cacheFile.length() > 0
    }
}
