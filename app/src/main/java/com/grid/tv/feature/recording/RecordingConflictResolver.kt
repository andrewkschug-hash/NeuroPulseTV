package com.grid.tv.feature.recording

object RecordingConflictResolver {
    fun resolve(overlappingRecordingIds: List<Long>, maxSimultaneous: Int = 2): ConflictDecision {
        return if (overlappingRecordingIds.size < maxSimultaneous) {
            ConflictDecision(allowed = true, activeIds = overlappingRecordingIds)
        } else {
            ConflictDecision(
                allowed = false,
                activeIds = overlappingRecordingIds,
                reason = "Scheduling conflict: ${overlappingRecordingIds.size} recordings overlap this time window"
            )
        }
    }
}
