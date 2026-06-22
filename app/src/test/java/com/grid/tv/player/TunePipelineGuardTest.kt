package com.grid.tv.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TunePipelineGuardTest {

    private lateinit var guard: TunePipelineGuard

    @Before
    fun setUp() {
        guard = TunePipelineGuard(dedupeWindowMs = 400L)
    }

    @Test
    fun evaluateAdmission_acceptsFirstRequest() {
        val key = TunePipelineGuard.TuneKey(1L, "http://example.com/stream.m3u8")
        assertTrue(guard.evaluateAdmission(key, bypassDedupe = false) is TunePipelineGuard.Admission.Accepted)
    }

    @Test
    fun evaluateAdmission_suppressesReentrantInFlight() = runBlocking {
        val key = TunePipelineGuard.TuneKey(1L, "http://example.com/stream.m3u8")
        guard.runPipeline(key) {
            val admission = guard.evaluateAdmission(key, bypassDedupe = false)
            assertTrue(admission is TunePipelineGuard.Admission.Suppressed)
            assertEquals(
                TunePipelineGuard.SuppressReason.REENTRANT_IN_FLIGHT,
                (admission as TunePipelineGuard.Admission.Suppressed).reason
            )
        }
    }

    @Test
    fun evaluateAdmission_suppressesDuplicateWithinWindow() = runBlocking {
        val key = TunePipelineGuard.TuneKey(2L, "http://example.com/other.m3u8")
        guard.runPipeline(key) { }
        val admission = guard.evaluateAdmission(key, bypassDedupe = false)
        assertTrue(admission is TunePipelineGuard.Admission.Suppressed)
        assertEquals(
            TunePipelineGuard.SuppressReason.DUPLICATE_WITHIN_WINDOW,
            (admission as TunePipelineGuard.Admission.Suppressed).reason
        )
    }

    @Test
    fun evaluateAdmission_bypassDedupe_allowsDuplicate() = runBlocking {
        val key = TunePipelineGuard.TuneKey(3L, "http://example.com/bypass.m3u8")
        guard.runPipeline(key) { }
        val admission = guard.evaluateAdmission(key, bypassDedupe = true)
        assertTrue(admission is TunePipelineGuard.Admission.Accepted)
    }

    @Test
    fun runPipeline_clearsActiveFlagAfterCompletion() = runBlocking {
        val key = TunePipelineGuard.TuneKey(4L, "http://example.com/serial.m3u8")
        guard.runPipeline(key) {
            assertTrue(guard.pipelineActive)
        }
        assertFalse(guard.pipelineActive)
        assertEquals(1, guard.acceptedCount)
    }

    @Test
    fun differentUrl_sameChannel_isNotDuplicate() = runBlocking {
        val keyA = TunePipelineGuard.TuneKey(5L, "http://cdn/a.m3u8")
        val keyB = TunePipelineGuard.TuneKey(5L, "http://cdn/b.m3u8")
        guard.runPipeline(keyA) { }
        val admission = guard.evaluateAdmission(keyB, bypassDedupe = false)
        assertTrue(admission is TunePipelineGuard.Admission.Accepted)
    }
}
