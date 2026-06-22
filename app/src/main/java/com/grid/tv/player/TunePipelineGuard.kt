package com.grid.tv.player

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes [LivePlayerManager] tune/loadStream work and suppresses accidental duplicate
 * requests for the same channel+URL (observer re-entry, double LaunchedEffect, etc.).
 */
internal class TunePipelineGuard(
    private val dedupeWindowMs: Long = DEFAULT_DEDUPE_WINDOW_MS
) {
    data class TuneKey(val channelId: Long, val streamUrl: String)

    enum class SuppressReason {
        /** Same channel+URL tune already running — observer re-entry. */
        REENTRANT_IN_FLIGHT,
        /** Same channel+URL accepted/completed within the dedupe window. */
        DUPLICATE_WITHIN_WINDOW
    }

    sealed class Admission {
        data object Accepted : Admission()
        data class Suppressed(val reason: SuppressReason) : Admission()
    }

    private val mutex = Mutex()

    @Volatile
    var pipelineActive: Boolean = false
        private set

    private var inFlightKey: TuneKey? = null
    private var lastCompletedKey: TuneKey? = null
    private var lastCompletedAtMs: Long = 0L

    var acceptedCount: Int = 0
        private set

    var suppressedCount: Int = 0
        private set

    fun evaluateAdmission(key: TuneKey, bypassDedupe: Boolean): Admission {
        if (bypassDedupe) return Admission.Accepted
        val now = System.currentTimeMillis()
        if (pipelineActive && inFlightKey == key) {
            suppressedCount++
            return Admission.Suppressed(SuppressReason.REENTRANT_IN_FLIGHT)
        }
        if (lastCompletedKey == key && now - lastCompletedAtMs < dedupeWindowMs) {
            suppressedCount++
            return Admission.Suppressed(SuppressReason.DUPLICATE_WITHIN_WINDOW)
        }
        return Admission.Accepted
    }

    suspend fun <T> runPipeline(key: TuneKey, block: suspend () -> T): T {
        mutex.withLock {
            pipelineActive = true
            inFlightKey = key
        }
        try {
            return block()
        } finally {
            mutex.withLock {
                pipelineActive = false
                inFlightKey = null
                lastCompletedKey = key
                lastCompletedAtMs = System.currentTimeMillis()
                acceptedCount++
            }
        }
    }

    internal fun resetForTests() {
        pipelineActive = false
        inFlightKey = null
        lastCompletedKey = null
        lastCompletedAtMs = 0L
        acceptedCount = 0
        suppressedCount = 0
    }

    companion object {
        const val DEFAULT_DEDUPE_WINDOW_MS = 400L
    }
}
