package com.grid.tv.player

/**
 * Lightweight cross-cutting flag for suppressing scanner probe metrics during playback.
 * Avoids DI cycles between [PlaybackNetworkExclusivity] and [com.grid.tv.feature.scanner.ScanMetricsLogger].
 */
internal object PlaybackActivityGate {
    @Volatile
    var suppressScannerMetrics: Boolean = false
}
