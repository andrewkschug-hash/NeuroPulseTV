package com.grid.tv.feature.startup

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import coil.Coil
import com.grid.tv.feature.vod.VodCatalogSessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Responds to [ComponentCallbacks2.onTrimMemory] by clearing in-memory caches
 * while preserving disk-backed image data and the VOD partition for the active tab.
 */
@Singleton
class MemoryPressureHandler @Inject constructor(
    private val vodCatalogSessionStore: VodCatalogSessionStore,
) {

    fun onTrimMemory(context: Context, level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return

        val heapBefore = heapUsedBytes()
        var coilBytesCleared = 0
        runCatching {
            val cache = Coil.imageLoader(context.applicationContext).memoryCache
            coilBytesCleared = cache?.size ?: 0
            cache?.clear()
        }.onFailure { error ->
            Log.w(TAG, "Coil memory cache clear failed: ${error.message}")
        }

        val catalogTrim = vodCatalogSessionStore.trimForMemoryPressure()
        val heapAfter = heapUsedBytes()

        val result = MemoryTrimResult(
            trimLevel = level,
            coilMemoryCacheBytesCleared = coilBytesCleared,
            catalogWallItemsDropped = catalogTrim.wallItemsDropped,
            catalogBrowseRowsDropped = catalogTrim.browseRowsDropped,
            hubWasActive = catalogTrim.hubWasActive,
            activeContentFilter = catalogTrim.activeContentFilter,
            heapUsedBytesBefore = heapBefore,
            heapUsedBytesAfter = heapAfter,
        )
        logTrimResult(result)
    }

    private fun logTrimResult(result: MemoryTrimResult) {
        Log.i(
            TAG,
            "trimMemory level=${result.trimLevel} hubActive=${result.hubWasActive} " +
                "filter=${result.activeContentFilter} " +
                "coilMemoryClearedKb=${result.coilFreedKb} " +
                "catalogWallItemsDropped=${result.catalogWallItemsDropped} " +
                "catalogBrowseRowsDropped=${result.catalogBrowseRowsDropped} " +
                "heapFreedKb=${result.heapFreedKb} " +
                "heapUsedKb=${result.heapUsedBytesAfter / 1024}"
        )
    }

    private fun heapUsedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    companion object {
        const val TAG = "MEMORY_PRESSURE"
    }
}
