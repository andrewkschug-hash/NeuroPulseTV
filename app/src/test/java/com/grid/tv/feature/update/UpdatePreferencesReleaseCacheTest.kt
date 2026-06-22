package com.grid.tv.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePreferencesReleaseCacheTest {

    @Test
    fun releaseCacheFresh_within24Hours() {
        val nowMs = 1_000_000_000_000L
        val fetchedAtMs = nowMs - (12 * 60 * 60 * 1000L)
        assertTrue(nowMs - fetchedAtMs < UpdatePreferences.RELEASE_CACHE_TTL_MS)
    }

    @Test
    fun releaseCacheStale_after24Hours() {
        val nowMs = 1_000_000_000_000L
        val fetchedAtMs = nowMs - UpdatePreferences.RELEASE_CACHE_TTL_MS - 1L
        assertFalse(nowMs - fetchedAtMs < UpdatePreferences.RELEASE_CACHE_TTL_MS)
    }
}
