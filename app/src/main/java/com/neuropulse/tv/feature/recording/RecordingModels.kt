package com.neuropulse.tv.feature.recording

import com.neuropulse.tv.domain.model.RecordQuality

enum class RecordingStatus { SCHEDULED, RECORDING, COMPLETED, FAILED }

enum class RecordingSort { DATE, CHANNEL, DURATION, FILE_SIZE }

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
    val activeIds: List<Long>
)
