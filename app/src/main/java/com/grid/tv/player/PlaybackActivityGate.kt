package com.grid.tv.player

/**
 * Lightweight cross-cutting flags for playback network coordination.
 * Avoids DI cycles between scanner, exclusivity, and [PlaybackNetworkCoordinator].
 */
internal object PlaybackActivityGate {
    @Volatile
    var suppressScannerMetrics: Boolean = false

    @Volatile
    var networkTuneLockUntilMs: Long = 0L

    fun isNetworkTuneLocked(): Boolean = System.currentTimeMillis() < networkTuneLockUntilMs

    fun openNetworkTuneLock(durationMs: Long) {
        networkTuneLockUntilMs = System.currentTimeMillis() + durationMs
    }

    fun clearNetworkTuneLock() {
        networkTuneLockUntilMs = 0L
    }
}
