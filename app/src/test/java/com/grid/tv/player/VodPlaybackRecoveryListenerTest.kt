package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VodPlaybackRecoveryListenerTest {

    @Test
    fun errorBackoffMs_usesExponentialBackoff() {
        assertEquals(1_000L, VodPlaybackRecoveryListener.errorBackoffMs(1))
        assertEquals(2_000L, VodPlaybackRecoveryListener.errorBackoffMs(2))
        assertEquals(4_000L, VodPlaybackRecoveryListener.errorBackoffMs(3))
    }

    @Test
    fun recoveryTimingConstants_matchSpec() {
        assertEquals(2_000L, VodPlaybackRecoveryListener.POSITION_POLL_INTERVAL_MS)
        assertEquals(6_000L, VodPlaybackRecoveryListener.POSITION_STALL_THRESHOLD_MS)
        assertEquals(15_000L, VodPlaybackRecoveryListener.BUFFERING_RECOVERY_THRESHOLD_MS)
        assertEquals(20_000L, VodPlaybackRecoveryListener.RECOVERY_DEBOUNCE_MS)
        assertEquals(2, VodPlaybackRecoveryListener.MAX_BUFFERING_RECOVERY_ATTEMPTS)
        assertEquals(3, VodPlaybackRecoveryListener.MAX_SOURCE_ERROR_RETRIES)
    }
}
