package com.grid.tv.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IptvLoadErrorHandlingPolicyTest {

    @Test
    fun retryBackoffSchedule_matchesIptvSpec() {
        assertArrayEquals(
            longArrayOf(1_000L, 3_000L, 8_000L),
            IptvLoadErrorHandlingPolicy.RETRY_BACKOFF_MS
        )
    }

    @Test
    fun minimumRetries_manifestAndSegments() {
        assertEquals(3, IptvLoadErrorHandlingPolicy.MANIFEST_MIN_RETRIES)
        assertEquals(3, IptvLoadErrorHandlingPolicy.SEGMENT_MIN_RETRIES)
    }
}
