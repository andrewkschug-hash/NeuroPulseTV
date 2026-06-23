package com.grid.tv.feature.startup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists last-known catalog sizes for instant startup UI.
 *
 * Uses [android.content.SharedPreferences] (not DataStore) so counts can be read synchronously
 * on the first background startup frame without awaiting async I/O — important for sub-second UI.
 */
data class PersistedCatalogCounts(
    val movies: Int,
    val series: Int,
    val channels: Int,
    val updatedAtMs: Long
) {
    val isValid: Boolean get() = updatedAtMs > 0L
}

@Singleton
class StartupCatalogCountsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): PersistedCatalogCounts = PersistedCatalogCounts(
        movies = prefs.getInt(KEY_MOVIES, 0),
        series = prefs.getInt(KEY_SERIES, 0),
        channels = prefs.getInt(KEY_CHANNELS, 0),
        updatedAtMs = prefs.getLong(KEY_UPDATED_AT, 0L)
    )

    fun write(counts: PersistedCatalogCounts) {
        prefs.edit()
            .putInt(KEY_MOVIES, counts.movies)
            .putInt(KEY_SERIES, counts.series)
            .putInt(KEY_CHANNELS, counts.channels)
            .putLong(KEY_UPDATED_AT, counts.updatedAtMs)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "startup_catalog_counts"
        private const val KEY_MOVIES = "last_movie_count"
        private const val KEY_SERIES = "last_series_count"
        private const val KEY_CHANNELS = "last_channel_count"
        private const val KEY_UPDATED_AT = "last_updated_time"
    }
}
