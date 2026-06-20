package com.grid.tv.feature.scanner

import java.util.concurrent.ConcurrentHashMap

/**
 * Session-scoped hostname failure tracker. Hosts with repeated DNS failures are
 * skipped for the remainder of the current scan session to avoid request storms.
 */
class HostFailureTracker(
    private val blacklistThreshold: Int = DEFAULT_BLACKLIST_THRESHOLD
) {
    private val dnsFailures = ConcurrentHashMap<String, Int>()
    private val blacklisted = ConcurrentHashMap.newKeySet<String>()

    fun isBlacklisted(hostname: String): Boolean = blacklisted.contains(hostname.lowercase())

    fun recordDnsFailure(hostname: String) {
        val key = hostname.lowercase()
        val count = dnsFailures.merge(key, 1, Int::plus) ?: 1
        if (count > blacklistThreshold) {
            blacklisted.add(key)
        }
    }

    fun resetSession() {
        dnsFailures.clear()
        blacklisted.clear()
    }

    fun blacklistedHostCount(): Int = blacklisted.size

    companion object {
        const val DEFAULT_BLACKLIST_THRESHOLD = 10
    }
}
