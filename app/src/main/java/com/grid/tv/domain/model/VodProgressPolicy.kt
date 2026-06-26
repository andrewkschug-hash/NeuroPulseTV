package com.grid.tv.domain.model

/** Shared VOD progress thresholds — aligned with common binge UX (90% watched). */
object VodProgressPolicy {
    const val WATCHED_FRACTION = 0.90
    const val NEXT_UP_COUNTDOWN_SEC = 15
    const val NEXT_UP_TRIGGER_FRACTION = 0.92

    fun isWatched(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs <= 0L || durationMs <= 0L) return false
        return positionMs.toDouble() / durationMs >= WATCHED_FRACTION
    }

    fun shouldOfferNextUp(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        return positionMs.toDouble() / durationMs >= NEXT_UP_TRIGGER_FRACTION
    }

    fun progressFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
    }
}
