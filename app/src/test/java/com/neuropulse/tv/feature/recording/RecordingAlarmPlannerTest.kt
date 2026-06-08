package com.neuropulse.tv.feature.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingAlarmPlannerTest {

    @Test
    fun triggerAt_returnsStartTime_whenFuture() {
        val now = 1_000L
        val start = 5_000L
        assertEquals(start, RecordingAlarmPlanner.triggerAt(start, now))
    }

    @Test
    fun triggerAt_returnsSoon_whenPast() {
        val now = 10_000L
        assertEquals(12_000L, RecordingAlarmPlanner.triggerAt(1_000L, now))
    }
}
