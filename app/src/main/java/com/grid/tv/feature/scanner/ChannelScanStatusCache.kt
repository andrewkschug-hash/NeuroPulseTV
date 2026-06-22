package com.grid.tv.feature.scanner

import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.util.cache.AppCacheRegistry
import com.grid.tv.util.cache.CacheStatistics
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded in-memory cache for channel scan statuses.
 * Evicts by LRU (max entries), age (>24h), and channels removed from the playlist.
 */
internal class ChannelScanStatusCache(
    private val maxEntries: Int = MAX_ENTRIES,
    registry: AppCacheRegistry? = null
) {
    private val lock = Any()
    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val evictions = AtomicLong()
    private val puts = AtomicLong()
    private val entries = object : LinkedHashMap<Long, ChannelScanSnapshot>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ChannelScanSnapshot>): Boolean {
            if (size <= maxEntries) return false
            evictions.incrementAndGet()
            return true
        }
    }

    init {
        registry?.register(CACHE_NAME) { statistics() }
    }

    fun put(channelId: Long, snapshot: ChannelScanSnapshot) {
        synchronized(lock) {
            puts.incrementAndGet()
            entries[channelId] = snapshot
        }
    }

    fun get(channelId: Long): ChannelScanSnapshot? = synchronized(lock) {
        val snapshot = entries[channelId]
        if (snapshot != null) {
            hits.incrementAndGet()
        } else {
            misses.incrementAndGet()
        }
        snapshot
    }

    fun snapshot(): Map<Long, ChannelScanSnapshot> = synchronized(lock) {
        entries.toMap()
    }

    fun size(): Int = synchronized(lock) {
        entries.size
    }

    fun statistics(): CacheStatistics = synchronized(lock) {
        CacheStatistics(
            name = CACHE_NAME,
            size = entries.size,
            maxEntries = maxEntries,
            estimatedBytes = entries.size.toLong() * ESTIMATED_BYTES_PER_ENTRY,
            maxBytes = maxEntries.toLong() * ESTIMATED_BYTES_PER_ENTRY,
            hits = hits.get(),
            misses = misses.get(),
            evictions = evictions.get(),
            puts = puts.get()
        )
    }

    fun evict(maxAgeMs: Long, validChannelIds: Set<Long>?, now: Long = System.currentTimeMillis()): Int =
        synchronized(lock) {
            var removed = 0
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val (channelId, snapshot) = iterator.next()
                val lastChecked = snapshot.lastCheckedAt ?: 0L
                val stale = lastChecked > 0L && now - lastChecked > maxAgeMs
                val orphan = validChannelIds != null && channelId !in validChannelIds
                if (stale || orphan) {
                    iterator.remove()
                    evictions.incrementAndGet()
                    removed++
                }
            }
            removed
        }

    fun replaceAll(from: Map<Long, ChannelScanSnapshot>) {
        synchronized(lock) {
            entries.clear()
            from.forEach { (id, snapshot) ->
                entries[id] = snapshot
            }
        }
    }

    fun remove(channelId: Long): Boolean = synchronized(lock) {
        entries.remove(channelId) != null
    }

    fun keySet(): Set<Long> = synchronized(lock) {
        entries.keys.toSet()
    }

    fun clear() = synchronized(lock) {
        entries.clear()
    }

    companion object {
        const val CACHE_NAME = "channel_scan_status"
        const val MAX_ENTRIES = 10_000
        const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
        private const val ESTIMATED_BYTES_PER_ENTRY = 128
    }
}
