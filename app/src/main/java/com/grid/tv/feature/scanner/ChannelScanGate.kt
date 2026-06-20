package com.grid.tv.feature.scanner

import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates channel validation with EPG download.
 * Post-import: priority channels first, then EPG, then background full scan.
 */
interface ChannelScanGate {
    val isValidationActive: StateFlow<Boolean>

    /**
     * Phase 1: validate favorites + recently watched + first visible channels (max 200).
     * Phase 2: schedule EPG immediately after priority pass.
     * Phase 3: continue full-catalog validation in background.
     */
    fun beginPostImportPriorityWorkflow()

    /** Blocks until priority validation is idle (used by [com.grid.tv.worker.EpgRefreshWorker]). */
    suspend fun awaitValidationIdle(maxWaitMs: Long = VALIDATION_IDLE_TIMEOUT_MS)

    companion object {
        const val VALIDATION_IDLE_TIMEOUT_MS = 5L * 60L * 1000L
        const val PRIORITY_VALIDATION_LIMIT = 200
    }
}
