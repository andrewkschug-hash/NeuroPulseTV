package com.grid.tv.feature.scanner

import com.grid.tv.domain.model.ChannelScanSnapshot

/**
 * Bounded in-memory cache for channel scan statuses.
 * Evicts by LRU (max entries), age (>24h), and channels removed from the playlist.
 */
internal class ChannelScanStatusCache(
    private val maxEntries: Int = MAX_ENTRIES
) {
    private val entries = object : LinkedHashMap<Long, ChannelScanSnapshot>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ChannelScanSnapshot>): Boolean =
            size > maxEntries
    }

    fun put(channelId: Long, snapshot: ChannelScanSnapshot) {
        entries[channelId] = snapshot
    }

    fun get(channelId: Long): ChannelScanSnapshot? = entries[channelId]

    fun snapshot(): Map<Long, ChannelScanSnapshot> = entries.toMap()

    fun size(): Int = entries.size

    /**
     * Removes entries older than [maxAgeMs] and/or whose channel id is not in [validChannelIds].
     * Pass null for [validChannelIds] to skip orphan eviction.
     */
    fun evict(maxAgeMs: Long, validChannelIds: Set<Long>?, now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (channelId, snapshot) = iterator.next()
            val lastChecked = snapshot.lastCheckedAt ?: 0L
            val stale = lastChecked > 0L && now - lastChecked > maxAgeMs
            val orphan = validChannelIds != null && channelId !in validChannelIds
            if (stale || orphan) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }

    fun replaceAll(from: Map<Long, ChannelScanSnapshot>) {
        entries.clear()
        from.forEach { (id, snapshot) -> put(id, snapshot) }
    }

    companion object {
        const val MAX_ENTRIES = 10_000
        const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
