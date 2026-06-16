package com.grid.tv.feature.recording

import java.io.File

object RecordingIntegrityChecker {

    data class Result(
        val status: RecordingIntegrityStatus,
        val message: String? = null
    )

    fun validate(file: File, expectedDurationMs: Long, outcome: RecordingOutcome): Result {
        if (!file.exists()) {
            return Result(RecordingIntegrityStatus.CORRUPT, "Recording file is missing")
        }
        val size = file.length()
        if (size < 188L) {
            return Result(RecordingIntegrityStatus.CORRUPT, "Recording file is too small")
        }
        val tsSync = sampleTsSyncRatio(file)
        val syncOk = tsSync >= MIN_SYNC_RATIO
        if (!syncOk) {
            return Result(RecordingIntegrityStatus.CORRUPT, "Invalid transport stream sync ratio")
        }
        if (outcome.signalLost) {
            return Result(RecordingIntegrityStatus.INCOMPLETE, "Signal lost before scheduled end")
        }
        if (expectedDurationMs > 60_000L && size < expectedDurationMs / 20) {
            return Result(RecordingIntegrityStatus.INCOMPLETE, "Recording appears shorter than expected")
        }
        if (outcome.hadDropouts || outcome.corruptedChunksSkipped > 0 || outcome.gapPatchedMs > 0L) {
            val reason = buildString {
                if (outcome.corruptedChunksSkipped > 0) {
                    append("${outcome.corruptedChunksSkipped} corrupt chunk(s) skipped")
                }
                if (outcome.gapPatchedMs > 0L) {
                    if (isNotEmpty()) append(", ")
                    append("${outcome.gapPatchedMs / 1000}s stream gap patched")
                }
                if (isEmpty()) append("Stream interruptions detected")
            }
            return Result(RecordingIntegrityStatus.INCOMPLETE, reason)
        }
        return Result(RecordingIntegrityStatus.OK)
    }

    private fun sampleTsSyncRatio(file: File): Float {
        var checkedPackets = 0
        var syncPackets = 0
        file.inputStream().buffered().use { input ->
            val packet = ByteArray(188)
            while (checkedPackets < MAX_SYNC_PACKETS) {
                val read = input.read(packet)
                if (read < 188) break
                checkedPackets++
                if (packet[0] == 0x47.toByte()) {
                    syncPackets++
                }
            }
        }
        if (checkedPackets == 0) return 0f
        return syncPackets.toFloat() / checkedPackets.toFloat()
    }

    private const val MAX_SYNC_PACKETS = 512
    private const val MIN_SYNC_RATIO = 0.85f
}
