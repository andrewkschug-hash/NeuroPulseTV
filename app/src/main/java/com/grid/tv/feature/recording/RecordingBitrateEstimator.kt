package com.grid.tv.feature.recording

import com.grid.tv.domain.model.RecordQuality

object RecordingBitrateEstimator {
    const val DEFAULT_BITRATE_BPS = 5_000_000

    fun estimateBytes(durationMs: Long, quality: RecordQuality = RecordQuality.ORIGINAL): Long =
        estimateBytes(durationMs, quality.bitrateBps)

    fun estimateBytes(durationMs: Long, bitrateBps: Int): Long {
        val seconds = durationMs.coerceAtLeast(0) / 1000.0
        return (bitrateBps / 8.0 * seconds).toLong()
    }

    fun formatEstimate(durationMs: Long, quality: RecordQuality = RecordQuality.ORIGINAL): String {
        val minutes = (durationMs / 60_000).coerceAtLeast(1)
        val hours = durationMs.coerceAtLeast(60_000L) / 3_600_000.0
        val gb = quality.gbPerHour * hours
        return "~${String.format("%.1f", gb)} GB for $minutes min (${quality.label})"
    }
}
