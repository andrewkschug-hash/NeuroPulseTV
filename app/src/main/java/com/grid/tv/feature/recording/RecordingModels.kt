package com.grid.tv.feature.recording

import com.grid.tv.domain.model.RecordQuality

enum class RecordingStatus { SCHEDULED, RECORDING, COMPLETED, FAILED }

enum class RecordingSort { DATE, CHANNEL, DURATION, FILE_SIZE }

/** Live recording health shown in notifications and the top-bar REC badge. */
enum class RecordingHealth {
    IDLE,
    RECORDING,
    RECONNECTING,
    SIGNAL_LOST,
    STORAGE_PAUSED
}

object RecordingResilienceConfig {
    /** Total time to retry before declaring the stream lost. */
    const val SIGNAL_LOST_WINDOW_MS = 30_000L
    const val INITIAL_BACKOFF_MS = 1_000L
    const val MAX_BACKOFF_MS = 8_000L
}

data class RecordingOutcome(
    val bytesWritten: Long,
    val hadDropouts: Boolean,
    val signalLost: Boolean,
    val gapPatchedMs: Long = 0L,
    val corruptedChunksSkipped: Int = 0
) {
    val saved: Boolean get() = bytesWritten > 0
    val success: Boolean get() = saved && !signalLost
}

enum class RecordingIntegrityStatus { OK, INCOMPLETE, CORRUPT }

data class PendingRecording(
    val channelId: Long,
    val channelName: String,
    val title: String,
    val streamUrl: String,
    val durationMs: Long,
    val scheduled: Boolean,
    val programStartTime: Long? = null,
    val programEndTime: Long? = null
)

data class RecordingPrecheck(
    val estimateText: String,
    val freeStorageText: String,
    val lowStorageWarning: String?,
    val insufficientSpaceWarning: String?,
    val availableQualities: List<RecordQuality>,
    val selectedQuality: RecordQuality
)

data class ConflictDecision(
    val allowed: Boolean,
    val activeIds: List<Long>,
    val reason: String? = null
)
