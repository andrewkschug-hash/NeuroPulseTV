package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.ui.component.ProgramTimeState
import com.grid.tv.ui.component.programTimeState

fun Channel.isCatchupEnabled(): Boolean =
    catchupDays > 0 ||
        !catchupSource.isNullOrBlank() ||
        !catchupMode.isNullOrBlank()

fun Channel.effectiveCatchupDays(): Int = when {
    catchupDays > 0 -> catchupDays
    isCatchupEnabled() -> 7
    else -> 0
}

fun Channel.archiveWindowHours(): Int = effectiveCatchupDays() * 24

fun Channel.archiveWindowMs(): Long = archiveWindowHours() * 3_600_000L

enum class EpgProgramAction(val label: String) {
    WATCH_LIVE("Watch Live"),
    WATCH_REPLAY("Watch Replay"),
    REMINDER("Reminder"),
    NONE("")
}

data class EpgProgramReplayState(
    val programId: Long,
    val timeState: ProgramTimeState,
    val action: EpgProgramAction,
    val canReplay: Boolean,
    val replayUrl: String?
)

fun resolveProgramAction(
    program: Program?,
    channel: Channel,
    now: Long,
    replayUrl: String?
): EpgProgramAction {
    if (program == null) return EpgProgramAction.WATCH_LIVE
    return when (programTimeState(program, now)) {
        ProgramTimeState.FUTURE -> EpgProgramAction.REMINDER
        ProgramTimeState.AIRING -> EpgProgramAction.WATCH_LIVE
        ProgramTimeState.PAST -> {
            if (canReplayProgram(program, channel, now, replayUrl)) {
                EpgProgramAction.WATCH_REPLAY
            } else {
                EpgProgramAction.WATCH_LIVE
            }
        }
    }
}

fun canReplayProgram(
    program: Program,
    channel: Channel,
    now: Long,
    replayUrl: String?
): Boolean {
    if (replayUrl.isNullOrBlank()) return false
    if (!channel.isCatchupEnabled()) return false
    if (now <= program.endTime) return false
    val windowMs = channel.archiveWindowMs()
    if (windowMs > 0 && program.startTime < now - windowMs) return false
    return true
}
