package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHealthMonitorTest {

    @Test
    fun tierMapping_matchesSpec() {
        assertEquals(PlaybackSessionHealthTier.HEALTHY, PlaybackSessionHealthTier.fromScore(95))
        assertEquals(PlaybackSessionHealthTier.HEALTHY, PlaybackSessionHealthTier.fromScore(90))
        assertEquals(PlaybackSessionHealthTier.DEGRADED, PlaybackSessionHealthTier.fromScore(89))
        assertEquals(PlaybackSessionHealthTier.DEGRADED, PlaybackSessionHealthTier.fromScore(70))
        assertEquals(PlaybackSessionHealthTier.UNSTABLE, PlaybackSessionHealthTier.fromScore(69))
        assertEquals(PlaybackSessionHealthTier.UNSTABLE, PlaybackSessionHealthTier.fromScore(0))
    }
}
