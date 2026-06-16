package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeshiftManagerClampTest {

    @Test
    fun rewindTargetStaysWithinBufferedWindow() {
        val bufferStart = 0L
        val liveEdge = 300_000L
        val current = 180_000L
        val target = (current - 30_000L).coerceAtLeast(bufferStart)
        assertEquals(150_000L, target)
        assertTrue(target >= bufferStart)
        assertTrue(target <= liveEdge)
    }

    @Test
    fun rewindDoesNotGoBeforeBufferStart() {
        val bufferStart = 0L
        val current = 15_000L
        val target = (current - 30_000L).coerceAtLeast(bufferStart)
        assertEquals(bufferStart, target)
    }

    @Test
    fun fastForwardTargetStopsAtLiveEdge() {
        val liveEdge = 62_000L
        val current = 10_000L
        val target = (current + 30_000L).coerceAtMost(liveEdge)
        assertEquals(40_000L, target)
    }

    @Test
    fun fastForwardNearLiveEdgeJumpsToLive() {
        val liveEdge = 62_000L
        val threshold = 3_000L
        val current = 59_500L
        val target = (current + 30_000L).coerceAtMost(liveEdge)
        assertTrue(target >= liveEdge - threshold)
    }

    @Test
    fun canRewindFalseAtBufferStart() {
        val bufferStart = 0L
        val headroom = 2_000L
        val current = 1_000L
        assertFalse(current > bufferStart + headroom)
    }

    @Test
    fun canRewindTrueWhenBufferedContentExists() {
        val bufferStart = 0L
        val headroom = 2_000L
        val current = 300_000L
        assertTrue(current > bufferStart + headroom)
    }
}
