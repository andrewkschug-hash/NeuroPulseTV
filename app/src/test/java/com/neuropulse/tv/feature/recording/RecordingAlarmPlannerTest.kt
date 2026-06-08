package com.neuropulse.tv.feature.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingAlarmPlannerTest {

    @Test
    fun triggerAt_returnsThirtySecondsBeforeStart_whenFarFuture() {
        val now = 1_000L
        val start = 40_000L
        assertEquals(10_000L, RecordingAlarmPlanner.triggerAt(start, now))
    }

    @Test
    fun triggerAt_returnsSoon_whenPast() {
        val now = 10_000L
        assertEquals(12_000L, RecordingAlarmPlanner.triggerAt(1_000L, now))
    }
}
