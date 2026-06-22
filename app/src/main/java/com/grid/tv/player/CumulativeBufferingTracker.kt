package com.grid.tv.player

/**
 * Rolling buffering budget — catches intermittent buffering loops that never exceed
 * a single continuous threshold.
 *
 * Default: 45s total buffering within any 60s window, after startup grace.
 */
class CumulativeBufferingTracker(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val budgetMs: Long = DEFAULT_BUDGET_MS,
    private val startupGraceMs: Long = DEFAULT_STARTUP_GRACE_MS,
    private val healthyResetMs: Long = DEFAULT_HEALTHY_RESET_MS
) {
    private data class BufferSpan(val startMs: Long, val endMs: Long)

    private val spans = mutableListOf<BufferSpan>()
    private var openBufferStartMs: Long = NO_OPEN_BUFFER
    private var tuneStartedAtMs: Long = 0L
    private var healthySinceMs: Long = 0L
    private var budgetViolated = false

    fun onTuneStarted(nowMs: Long = System.currentTimeMillis()) {
        reset(nowMs)
        tuneStartedAtMs = nowMs
    }

    fun onBufferingStarted(nowMs: Long = System.currentTimeMillis()) {
        if (openBufferStartMs == NO_OPEN_BUFFER) {
            openBufferStartMs = nowMs
        }
        healthySinceMs = 0L
    }

    fun onBufferingEnded(nowMs: Long = System.currentTimeMillis()) {
        if (openBufferStartMs != NO_OPEN_BUFFER) {
            spans += BufferSpan(openBufferStartMs, nowMs)
            openBufferStartMs = NO_OPEN_BUFFER
        }
        prune(nowMs)
    }

    fun onHealthyPlayback(nowMs: Long = System.currentTimeMillis()) {
        onBufferingEnded(nowMs)
        if (healthySinceMs == 0L) {
            healthySinceMs = nowMs
        }
        if (nowMs - healthySinceMs >= healthyResetMs) {
            resetBudget(nowMs)
        }
    }

    fun onFirstFrameRendered(nowMs: Long = System.currentTimeMillis()) {
        healthySinceMs = nowMs
    }

    /**
     * @param requireFirstFrame When true, budget is only evaluated after first frame (avoids startup false positives).
     */
    fun isBudgetExceeded(
        nowMs: Long = System.currentTimeMillis(),
        requireFirstFrame: Boolean = true,
        hasRenderedFirstFrame: Boolean = false
    ): Boolean {
        if (budgetViolated) return true
        if (requireFirstFrame && !hasRenderedFirstFrame) return false
        if (nowMs - tuneStartedAtMs < startupGraceMs) return false

        val total = totalBufferingMs(nowMs)
        if (total >= budgetMs) {
            budgetViolated = true
            return true
        }
        return false
    }

    fun totalBufferingMs(nowMs: Long = System.currentTimeMillis()): Long {
        prune(nowMs)
        var total = spans.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) }
        if (openBufferStartMs != NO_OPEN_BUFFER) {
            total += (nowMs - openBufferStartMs).coerceAtLeast(0)
        }
        return total
    }

    fun reset(nowMs: Long = System.currentTimeMillis()) {
        spans.clear()
        openBufferStartMs = NO_OPEN_BUFFER
        tuneStartedAtMs = nowMs
        healthySinceMs = 0L
        budgetViolated = false
    }

    private fun resetBudget(nowMs: Long) {
        spans.clear()
        openBufferStartMs = NO_OPEN_BUFFER
        budgetViolated = false
        healthySinceMs = nowMs
    }

    private fun prune(nowMs: Long) {
        val cutoff = nowMs - windowMs
        spans.removeAll { it.endMs < cutoff }
        if (openBufferStartMs != NO_OPEN_BUFFER && openBufferStartMs < cutoff) {
            openBufferStartMs = cutoff
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 60_000L
        const val DEFAULT_BUDGET_MS = 45_000L
        /** Matches [StreamPlaybackMonitor] initial tune evaluation delay. */
        const val DEFAULT_STARTUP_GRACE_MS = 12_000L
        const val DEFAULT_HEALTHY_RESET_MS = 10_000L
        private const val NO_OPEN_BUFFER = -1L
    }
}
