package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DecoderPressureTrackerTest {

    @Test
    fun snapshot_startsEmpty() {
        val tracker = DecoderPressureTracker()
        tracker.resetForTests()
        val snap = tracker.snapshot()
        assertEquals(0, snap.playerCount)
        assertEquals(0, snap.surfaceCount)
        assertEquals(DecoderPressureLevel.NORMAL, snap.pressureLevel)
    }
}
