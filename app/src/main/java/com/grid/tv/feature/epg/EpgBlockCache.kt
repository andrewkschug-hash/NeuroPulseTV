package com.grid.tv.feature.epg

import com.grid.tv.domain.model.Program
import com.grid.tv.util.cache.AppCacheRegistry
import com.grid.tv.util.cache.BoundedMemoryCache
import com.grid.tv.util.cache.CacheSizeEstimators
import com.grid.tv.util.cache.CacheStatistics

class EpgBlockCache(
    maxBlocks: Int = DEFAULT_MAX_BLOCKS,
    maxBytes: Long = DEFAULT_MAX_BYTES,
    registry: AppCacheRegistry? = null
) {
    private val cache = BoundedMemoryCache<String, List<Program>>(
        name = CACHE_NAME,
        maxEntries = maxBlocks,
        maxBytes = maxBytes,
        valueSizeEstimator = CacheSizeEstimators::programList,
        registry = registry
    )

    fun get(key: String): List<Program>? = cache.get(key)

    fun put(key: String, programs: List<Program>) {
        cache.put(key, programs)
    }

    fun clear() {
        cache.clear()
    }

    fun statistics(): CacheStatistics = cache.statistics()

    companion object {
        const val CACHE_NAME = "epg_block"
        const val DEFAULT_MAX_BLOCKS = 6
        /** ~4 MB cap for in-memory EPG programme windows. */
        const val DEFAULT_MAX_BYTES = 4L * 1024L * 1024L
    }
}
