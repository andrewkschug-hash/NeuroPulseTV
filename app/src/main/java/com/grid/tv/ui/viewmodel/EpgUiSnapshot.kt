package com.grid.tv.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.grid.tv.domain.epg.ProgrammeIndex
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program

/**
 * Immutable EPG grid snapshot. Emitted only when channel list, program window, or index changes.
 * [contentFingerprint] provides stable equality — identical content reuses the same instance.
 */
@Immutable
data class EpgUiSnapshot(
    val generation: Long,
    val contentFingerprint: Long,
    val channels: List<Channel>,
    val programs: List<Program>,
    val programmeIndex: ProgrammeIndex,
    val windowStart: Long,
    val windowDurationMs: Long
) {
    companion object {
        val EMPTY = EpgUiSnapshot(
            generation = 0L,
            contentFingerprint = 0L,
            channels = emptyList(),
            programs = emptyList(),
            programmeIndex = ProgrammeIndex.EMPTY,
            windowStart = 0L,
            windowDurationMs = 4 * 60 * 60 * 1000L
        )

        fun computeFingerprint(
            channels: List<Channel>,
            programs: List<Program>,
            programmeIndex: ProgrammeIndex,
            windowStart: Long,
            windowDurationMs: Long
        ): Long {
            var hash = 17L
            hash = hash * 31L + channels.size
            hash = hash * 31L + programs.size
            hash = hash * 31L + programmeIndex.channelCount
            hash = hash * 31L + programmeIndex.totalProgramCount
            hash = hash * 31L + windowStart
            hash = hash * 31L + windowDurationMs
            for (channel in channels) {
                hash = hash * 31L + channel.id
                hash = hash * 31L + channel.hashCode()
            }
            if (programs.isNotEmpty()) {
                hash = hash * 31L + programs.first().id
                hash = hash * 31L + programs.last().id
            }
            return hash
        }

        fun build(
            channels: List<Channel>,
            programs: List<Program>,
            programmeIndex: ProgrammeIndex,
            windowStart: Long,
            windowDurationMs: Long,
            previous: EpgUiSnapshot = EMPTY
        ): EpgUiSnapshot {
            val fingerprint = computeFingerprint(
                channels = channels,
                programs = programs,
                programmeIndex = programmeIndex,
                windowStart = windowStart,
                windowDurationMs = windowDurationMs
            )
            if (previous.contentFingerprint == fingerprint) {
                return previous
            }
            return EpgUiSnapshot(
                generation = previous.generation + 1L,
                contentFingerprint = fingerprint,
                channels = channels,
                programs = programs,
                programmeIndex = programmeIndex,
                windowStart = windowStart,
                windowDurationMs = windowDurationMs
            )
        }
    }
}
