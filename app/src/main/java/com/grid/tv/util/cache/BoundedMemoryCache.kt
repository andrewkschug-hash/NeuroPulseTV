package com.grid.tv.util.cache

import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe LRU cache with max entry count and optional estimated byte budget.
 */
class BoundedMemoryCache<K, V>(
    val name: String,
    private val maxEntries: Int,
    private val maxBytes: Long = 0L,
    private val valueSizeEstimator: (V) -> Int = { 64 },
    registry: AppCacheRegistry? = null
) {
    private val lock = Any()
    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val evictions = AtomicLong()
    private val puts = AtomicLong()
    private var estimatedBytes = 0L

    private val map = object : LinkedHashMap<K, V>(maxEntries.coerceAtLeast(1) + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            val overEntries = size > maxEntries
            val overBytes = maxBytes > 0L && estimatedBytes > maxBytes
            if (!overEntries && !overBytes) return false
            estimatedBytes -= valueSizeEstimator(eldest.value).coerceAtLeast(0)
            evictions.incrementAndGet()
            return true
        }
    }

    init {
        registry?.register(name) { statistics() }
    }

    fun get(key: K): V? = synchronized(lock) {
        val value = map[key]
        if (value != null) {
            hits.incrementAndGet()
        } else {
            misses.incrementAndGet()
        }
        value
    }

    fun put(key: K, value: V): V? = synchronized(lock) {
        puts.incrementAndGet()
        val previous = map.remove(key)
        if (previous != null) {
            estimatedBytes -= valueSizeEstimator(previous).coerceAtLeast(0)
        }
        map[key] = value
        estimatedBytes += valueSizeEstimator(value).coerceAtLeast(0)
        trimToBudget()
        previous
    }

    fun remove(key: K): V? = synchronized(lock) {
        val removed = map.remove(key)
        if (removed != null) {
            estimatedBytes -= valueSizeEstimator(removed).coerceAtLeast(0)
        }
        removed
    }

    fun removeIf(predicate: (K) -> Boolean): Int = synchronized(lock) {
        val keys = map.keys.filter(predicate)
        keys.forEach { remove(it) }
        keys.size
    }

    fun clear() = synchronized(lock) {
        map.clear()
        estimatedBytes = 0L
    }

    fun size(): Int = synchronized(lock) { map.size }

    fun statistics(): CacheStatistics = synchronized(lock) {
        CacheStatistics(
            name = name,
            size = map.size,
            maxEntries = maxEntries,
            estimatedBytes = estimatedBytes,
            maxBytes = maxBytes,
            hits = hits.get(),
            misses = misses.get(),
            evictions = evictions.get(),
            puts = puts.get()
        )
    }

    private fun trimToBudget() {
        while (map.size > maxEntries || (maxBytes > 0L && estimatedBytes > maxBytes)) {
            val eldest = map.entries.firstOrNull() ?: break
            map.remove(eldest.key)
            estimatedBytes -= valueSizeEstimator(eldest.value).coerceAtLeast(0)
            evictions.incrementAndGet()
        }
    }

    companion object {
        const val DEFAULT_STRING_ENTRY_BYTES = 96
    }
}
