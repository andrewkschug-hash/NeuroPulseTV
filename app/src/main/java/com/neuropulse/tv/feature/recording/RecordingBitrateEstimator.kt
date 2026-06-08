package com.neuropulse.tv.feature.recording

object RecordingBitrateEstimator {
    const val DEFAULT_BITRATE_BPS = 5_000_000

    fun estimateBytes(durationMs: Long, bitrateBps: Int = DEFAULT_BITRATE_BPS): Long {
        val seconds = durationMs.coerceAtLeast(0) / 1000.0
        return (bitrateBps / 8.0 * seconds).toLong()
    }

    fun formatEstimate(durationMs: Long, bitrateBps: Int = DEFAULT_BITRATE_BPS): String {
        val minutes = (durationMs / 60_000).coerceAtLeast(1)
        val bytes = estimateBytes(durationMs, bitrateBps)
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return "~${String.format("%.1f", gb)} GB for $minutes min at this channel's bitrate"
    }
}
