package com.neuropulse.tv.feature.recording

object RecordingAlarmPlanner {
    fun triggerAt(startTimeMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        return if (startTimeMs > nowMs) startTimeMs else nowMs + 2_000
    }
}
