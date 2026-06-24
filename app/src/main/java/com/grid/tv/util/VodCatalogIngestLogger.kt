package com.grid.tv.util

import android.util.Log

/**
 * Batch-level memory and timing logs for streaming VOD/series catalog ingestion.
 * Filter logcat: `adb logcat -s VodCatalogIngest`
 */
object VodCatalogIngestLogger {
    private const val TAG = "VodCatalogIngest"

    fun logBatchStart(
        phase: String,
        playlistId: Long,
        batchIndex: Int,
        batchSize: Int,
        parsedSoFar: Int
    ) {
        val mem = PlaybackDiagnostics.memorySnapshotForLog()
        Log.i(
            TAG,
            "BATCH_START phase=$phase playlist=$playlistId batch=$batchIndex size=$batchSize " +
                "parsedSoFar=$parsedSoFar heapPct=${mem.usedPercent} usedMb=${mem.usedMb}"
        )
    }

    fun logBatchComplete(
        phase: String,
        playlistId: Long,
        batchIndex: Int,
        batchSize: Int,
        parsedSoFar: Int,
        elapsedMs: Long
    ) {
        val mem = PlaybackDiagnostics.memorySnapshotForLog()
        Log.i(
            TAG,
            "BATCH_DONE phase=$phase playlist=$playlistId batch=$batchIndex size=$batchSize " +
                "parsedSoFar=$parsedSoFar elapsedMs=$elapsedMs heapPct=${mem.usedPercent} usedMb=${mem.usedMb}"
        )
    }

    fun logIngestComplete(
        phase: String,
        playlistId: Long,
        parsedCount: Int,
        skippedCount: Int,
        totalElapsedMs: Long
    ) {
        val mem = PlaybackDiagnostics.memorySnapshotForLog()
        Log.i(
            TAG,
            "INGEST_COMPLETE phase=$phase playlist=$playlistId parsed=$parsedCount skipped=$skippedCount " +
                "elapsedMs=$totalElapsedMs heapPct=${mem.usedPercent} usedMb=${mem.usedMb}"
        )
    }
}
