package com.grid.tv.feature.scanner

import com.grid.tv.util.cache.AppCacheRegistry
import com.grid.tv.util.cache.BoundedMemoryCache
import com.grid.tv.util.cache.CacheStatistics

/**
 * Session-scoped hostname failure tracker. Hosts with repeated DNS failures are
 * skipped for the remainder of the current scan session to avoid request storms.
 */
class HostFailureTracker(
    private val blacklistThreshold: Int = DEFAULT_BLACKLIST_THRESHOLD,
    registry: AppCacheRegistry? = null
) {
    private val dnsFailures = BoundedMemoryCache<String, Int>(
        name = DNS_FAILURES_CACHE,
        maxEntries = MAX_DNS_FAILURE_HOSTS,
        maxBytes = 256L * 1024L,
        valueSizeEstimator = { BoundedMemoryCache.DEFAULT_STRING_ENTRY_BYTES },
        registry = registry
    )
    private val blacklisted = BoundedMemoryCache<String, Boolean>(
        name = HOST_BLACKLIST_CACHE,
        maxEntries = MAX_BLACKLISTED_HOSTS,
        maxBytes = 128L * 1024L,
        valueSizeEstimator = { 32 },
        registry = registry
    )

    fun isBlacklisted(hostname: String): Boolean =
        blacklisted.get(hostname.lowercase()) == true

    fun recordDnsFailure(hostname: String) {
        val key = hostname.lowercase()
        val count = (dnsFailures.get(key) ?: 0) + 1
        dnsFailures.put(key, count)
        if (count > blacklistThreshold) {
            blacklisted.put(key, true)
        }
    }

    fun resetSession() {
        dnsFailures.clear()
        blacklisted.clear()
    }

    fun blacklistedHostCount(): Int = blacklisted.size()

    fun statistics(): List<CacheStatistics> =
        listOf(dnsFailures.statistics(), blacklisted.statistics())

    companion object {
        const val DEFAULT_BLACKLIST_THRESHOLD = 10
        const val DNS_FAILURES_CACHE = "host_dns_failures"
        const val HOST_BLACKLIST_CACHE = "host_blacklist"
        const val MAX_DNS_FAILURE_HOSTS = 2_000
        const val MAX_BLACKLISTED_HOSTS = 500
    }
}
