package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.BuildConfig
import com.grid.tv.domain.model.VodContentFilter

/**
 * Debug-only VOD hub lifecycle logger.
 *
 * Logs surface phase transitions (including Refreshing while Ready + catalog ingest)
 * and a compact snapshot of tab, counts, and focus zone for regression diagnosis.
 */
object VodHubLifecycleLogger {
    private const val TAG = "VOD_STATE"

    data class Snapshot(
        val tab: VodContentFilter,
        val focusZone: String,
        val genreCount: Int,
        val browseRowCount: Int,
        val pagedItemCount: Int,
        val focusContentMode: VodHubFocusContentMode,
        val catalogLoading: Boolean,
        val blocksGridFocus: Boolean,
    ) {
        fun format(): String =
            "tab=${tab.name} zone=$focusZone genres=$genreCount browseRows=$browseRowCount " +
                "paged=$pagedItemCount focusMode=$focusContentMode catalogLoading=$catalogLoading " +
                "blocksGridFocus=$blocksGridFocus"
    }

    fun logTransition(
        from: VodHubSurfacePhase,
        to: VodHubSurfacePhase,
        snapshot: Snapshot,
    ) {
        if (!BuildConfig.DEBUG) return
        if (from == to) return
        runCatching {
            Log.i(TAG, "${from.name} -> ${to.name} | ${snapshot.format()}")
        }
    }

    fun logSnapshot(phase: VodHubSurfacePhase, snapshot: Snapshot, reason: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG, "${phase.name} ($reason) | ${snapshot.format()}")
        }
    }
}

/** High-level lifecycle phase used for transition logging. */
enum class VodHubSurfacePhase {
    Loading,
    Ready,
    Empty,
    Error,
    Refreshing,
}

fun VodHubSurfaceState.lifecyclePhase(catalogLoading: Boolean): VodHubSurfacePhase = when (this) {
    is VodHubSurfaceState.Loading -> VodHubSurfacePhase.Loading
    is VodHubSurfaceState.Empty -> VodHubSurfacePhase.Empty
    is VodHubSurfaceState.Error -> VodHubSurfacePhase.Error
    is VodHubSurfaceState.Ready -> if (catalogLoading) {
        VodHubSurfacePhase.Refreshing
    } else {
        VodHubSurfacePhase.Ready
    }
}
