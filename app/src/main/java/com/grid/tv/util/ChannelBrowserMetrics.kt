package com.grid.tv.util

import android.os.Debug
import android.util.Log

/**
 * Channel Browser paging telemetry. Filter logcat with tag `ChannelBrowserMetrics`.
 */
object ChannelBrowserMetrics {
    private const val TAG = "ChannelBrowserMetrics"

    fun logFilterApplied(
        group: String?,
        favoritesOnly: Boolean,
        matchSports: Boolean,
        search: String,
        totalCount: Int
    ) {
        Log.i(
            TAG,
            "FILTER_APPLIED group=${group ?: "ALL"} favorites=$favoritesOnly sports=$matchSports " +
                "searchLen=${search.length} totalCount=$totalCount ${memorySuffix()}"
        )
    }

    fun logInitialPageLoaded(
        itemCount: Int,
        elapsedMs: Long,
        group: String?,
        favoritesOnly: Boolean,
        matchSports: Boolean,
        search: String
    ) {
        Log.i(
            TAG,
            "INITIAL_PAGE items=$itemCount elapsedMs=$elapsedMs group=${group ?: "ALL"} " +
                "favorites=$favoritesOnly sports=$matchSports searchLen=${search.length} ${memorySuffix()}"
        )
    }

    fun logPageLoaded(offset: Int, itemCount: Int, elapsedMs: Long) {
        Log.d(
            TAG,
            "PAGE offset=$offset items=$itemCount elapsedMs=$elapsedMs ${memorySuffix()}"
        )
    }

    private fun memorySuffix(): String {
        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        return "heapUsedMb=$heapUsedMb nativeMb=$nativeMb"
    }
}
