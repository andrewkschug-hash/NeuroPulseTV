package com.grid.tv.feature.recording

object RecordingCountdown {
    fun formatUntilStart(startTimeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = (startTimeMs - nowMs).coerceAtLeast(0)
        if (diff == 0L) return "Starting soon"
        val hours = diff / 3_600_000
        val minutes = (diff % 3_600_000) / 60_000
        return when {
            hours > 0 -> "Records in ${hours}h ${minutes}m"
            minutes > 0 -> "Records in ${minutes}m"
            else -> "Records in ${diff / 1000}s"
        }
    }

    fun formatElapsed(elapsedSec: Long): String {
        val hours = elapsedSec / 3600
        val minutes = (elapsedSec % 3600) / 60
        val seconds = elapsedSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
