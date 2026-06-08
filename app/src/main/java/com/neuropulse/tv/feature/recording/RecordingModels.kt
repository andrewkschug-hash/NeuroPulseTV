package com.neuropulse.tv.feature.recording

enum class RecordingStatus { SCHEDULED, RECORDING, COMPLETED, FAILED }

enum class RecordingSort { DATE, CHANNEL, DURATION }

data class ConflictDecision(
    val allowed: Boolean,
    val activeIds: List<Long>
)
