package com.grid.tv.feature.vod

import android.content.Context
import com.grid.tv.domain.model.SeriesCatalogHydrationState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists per-playlist series hydration state so zero-series providers are not re-fetched every tab open. */
@Singleton
class SeriesCatalogHydrationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(playlistId: Long): SeriesCatalogHydrationState {
        if (playlistId <= 0L) return SeriesCatalogHydrationState.NEVER_FETCHED
        val raw = prefs.getString(key(playlistId), null) ?: return SeriesCatalogHydrationState.NEVER_FETCHED
        return runCatching { SeriesCatalogHydrationState.valueOf(raw) }
            .getOrDefault(SeriesCatalogHydrationState.NEVER_FETCHED)
    }

    fun setState(playlistId: Long, state: SeriesCatalogHydrationState) {
        if (playlistId <= 0L) return
        prefs.edit().putString(key(playlistId), state.name).apply()
    }

    /** DB rows win over persisted state when series exist on disk. */
    fun resolveState(playlistId: Long, seriesCountOnDisk: Int): SeriesCatalogHydrationState =
        resolveSeriesHydrationState(getState(playlistId), seriesCountOnDisk).also { resolved ->
            if (resolved == SeriesCatalogHydrationState.POPULATED && playlistId > 0L) {
                setState(playlistId, resolved)
            }
        }

    fun clear(playlistId: Long) {
        if (playlistId <= 0L) return
        prefs.edit().remove(key(playlistId)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun key(playlistId: Long): String = "playlist_$playlistId"

    companion object {
        private const val PREFS_NAME = "series_catalog_hydration"
    }
}

/** Pure resolution for unit tests and [SeriesCatalogHydrationStore.resolveState]. */
internal fun resolveSeriesHydrationState(
    persisted: SeriesCatalogHydrationState,
    seriesCountOnDisk: Int,
): SeriesCatalogHydrationState = when {
    seriesCountOnDisk > 0 -> SeriesCatalogHydrationState.POPULATED
    else -> persisted
}
