package com.grid.tv.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IptvLoadErrorHandlingPolicyTest {

    @Test
    fun retryBackoffSchedule_allowsSingleRetry() {
        assertArrayEquals(
            longArrayOf(1_000L),
            IptvLoadErrorHandlingPolicy.RETRY_BACKOFF_MS
        )
    }

    @Test
    fun minimumRetries_manifestAndSegments() {
        assertEquals(1, IptvLoadErrorHandlingPolicy.MANIFEST_MIN_RETRIES)
        assertEquals(1, IptvLoadErrorHandlingPolicy.SEGMENT_MIN_RETRIES)
        assertEquals(1, IptvLoadErrorHandlingPolicy.MAX_RECOVERABLE_ATTEMPTS)
    }
}
