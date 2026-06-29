package com.grid.tv.ui.screen

import android.util.Log
import com.grid.tv.BuildConfig
import com.grid.tv.domain.model.VodContentFilter

/** Debug-only VOD hub focus transition logger. */
internal object VodHubFocusLogger {
    private const val TAG = "VodHubFocus"

    private fun logD(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.d(TAG, message) }
    }

    fun zoneTransition(from: VodFocusZone, to: VodFocusZone, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        val extra = if (detail.isBlank()) "" else " $detail"
        logD("${zoneLabel(from)} → ${zoneLabel(to)}$extra")
    }

    fun filterHighlight(filter: VodContentFilter, index: Int) {
        if (!BuildConfig.DEBUG) return
        logD("FILTER(${filter.name}) index=$index")
    }

    fun genreFocus(label: String, index: Int) {
        if (!BuildConfig.DEBUG) return
        logD("GENRE($label) index=$index")
    }

    fun gridFocus(filter: VodContentFilter, index: Int, contentKey: String?) {
        if (!BuildConfig.DEBUG) return
        logD("GRID(${filter.name} #$index key=${contentKey ?: "?"})")
    }

    fun gridRestore(
        filter: VodContentFilter,
        targetIndex: Int,
        scrollIndex: Int,
        contentKey: String?,
    ) {
        if (!BuildConfig.DEBUG) return
        logD(
            "GRID_RESTORE(${filter.name}) scroll=$scrollIndex → focus=$targetIndex key=${contentKey ?: "?"}"
        )
    }

    fun sidebar(item: String) {
        if (!BuildConfig.DEBUG) return
        logD("SIDEBAR($item)")
    }

    fun emptyRecovery(branch: String) {
        if (!BuildConfig.DEBUG) return
        logD("EMPTY_RECOVERY $branch")
    }

    fun persist(playlistId: Long, filter: VodContentFilter) {
        if (!BuildConfig.DEBUG) return
        logD("PERSIST playlist=$playlistId filter=${filter.name}")
    }

    fun restore(playlistId: Long, filter: VodContentFilter) {
        if (!BuildConfig.DEBUG) return
        logD("RESTORE playlist=$playlistId filter=${filter.name}")
    }

    private fun zoneLabel(zone: VodFocusZone): String = when (zone) {
        VodFocusZone.NAV_DRAWER -> "SIDEBAR"
        VodFocusZone.FILTER_PANEL -> "FILTER"
        VodFocusZone.GENRE_PANEL -> "GENRE"
        VodFocusZone.LANGUAGE_SUBMENU -> "LANG_SUB"
        VodFocusZone.HERO -> "HERO"
        VodFocusZone.CONTENT -> "GRID"
    }
}
