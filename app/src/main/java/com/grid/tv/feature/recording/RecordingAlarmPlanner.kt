package com.grid.tv.feature.recording

object RecordingAlarmPlanner {
    private const val PRE_START_MS = 30_000L

    fun triggerAt(startTimeMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        val thirtySecBefore = startTimeMs - PRE_START_MS
        return if (thirtySecBefore > nowMs) thirtySecBefore else nowMs + 2_000
    }
}
