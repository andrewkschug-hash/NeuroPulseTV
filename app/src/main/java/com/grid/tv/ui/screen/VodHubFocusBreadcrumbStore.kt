package com.grid.tv.ui.screen

import android.content.Context
import com.grid.tv.domain.model.VodContentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists VOD hub focus breadcrumbs per playlist so focus survives process death.
 * Writes are async ([apply]); reads are synchronous O(1) from SharedPreferences.
 */
@Singleton
class VodHubFocusBreadcrumbStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(playlistId: Long): VodHubPersistedFocusSnapshot? {
        if (playlistId <= 0L) return null
        val raw = prefs.getString(keyFor(playlistId), null) ?: return null
        return runCatching { json.decodeFromString<VodHubPersistedFocusSnapshot>(raw) }.getOrNull()
    }

    fun write(playlistId: Long, snapshot: VodHubPersistedFocusSnapshot) {
        if (playlistId <= 0L) return
        val encoded = json.encodeToString(snapshot)
        prefs.edit()
            .putString(keyFor(playlistId), encoded)
            .putLong(KEY_UPDATED_AT_PREFIX + playlistId, System.currentTimeMillis())
            .apply()
    }

    fun clear(playlistId: Long) {
        if (playlistId <= 0L) return
        prefs.edit()
            .remove(keyFor(playlistId))
            .remove(KEY_UPDATED_AT_PREFIX + playlistId)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "vod_hub_focus_breadcrumbs"
        private const val KEY_UPDATED_AT_PREFIX = "updated_at_"

        private fun keyFor(playlistId: Long): String = "snapshot_$playlistId"
    }
}

internal fun snapshotVodHubFocus(
    ui: VodHubFocusUiState,
    contentFilter: VodContentFilter,
    focusZone: VodFocusZone = ui.focusZone,
): VodHubPersistedFocusSnapshot = VodHubPersistedFocusSnapshot(
    contentFilter = contentFilter.name,
    filterFocusIndex = ui.filterFocusIndex,
    focusZone = focusZone.name,
    movies = ui.exportPersistedBreadcrumb(VodContentFilter.MOVIES),
    series = ui.exportPersistedBreadcrumb(VodContentFilter.SERIES),
    all = ui.exportPersistedBreadcrumb(VodContentFilter.ALL),
)

internal fun hydrateVodHubFocus(ui: VodHubFocusUiState, snapshot: VodHubPersistedFocusSnapshot) {
    ui.importPersistedBreadcrumb(VodContentFilter.MOVIES, snapshot.movies)
    ui.importPersistedBreadcrumb(VodContentFilter.SERIES, snapshot.series)
    ui.importPersistedBreadcrumb(VodContentFilter.ALL, snapshot.all)
    ui.filterFocusIndex = snapshot.filterFocusIndex
}
