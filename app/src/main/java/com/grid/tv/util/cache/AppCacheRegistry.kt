package com.grid.tv.util.cache

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCacheRegistry @Inject constructor() {
    private val providers = ConcurrentHashMap<String, () -> CacheStatistics>()

    fun register(name: String, provider: () -> CacheStatistics) {
        providers[name] = provider
    }

    fun inventory(): List<CacheStatistics> =
        providers.values.map { it() }.sortedBy { it.name }

    fun logInventory(reason: String = "snapshot") {
        val stats = inventory()
        Log.i(TAG, "CACHE_INVENTORY reason=$reason caches=${stats.size}")
        stats.forEach { stat ->
            Log.i(TAG, stat.toLogLine())
        }
    }

    fun clearAll() {
        inventory().forEach { stat ->
            Log.i(TAG, "CACHE_CLEAR name=${stat.name} size=${stat.size}")
        }
    }

    companion object {
        const val TAG = "AppCacheMetrics"
    }
}
