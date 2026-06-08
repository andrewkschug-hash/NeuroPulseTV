package com.neuropulse.tv.feature.recording

object RecordingConflictResolver {
    fun resolve(activeRecordingIds: List<Long>, maxSimultaneous: Int = 2): ConflictDecision {
        return if (activeRecordingIds.size < maxSimultaneous) {
            ConflictDecision(allowed = true, activeIds = activeRecordingIds)
        } else {
            ConflictDecision(allowed = false, activeIds = activeRecordingIds)
        }
    }
}
