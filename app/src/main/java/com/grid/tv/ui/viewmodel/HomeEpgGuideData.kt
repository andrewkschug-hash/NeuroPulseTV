package com.grid.tv.ui.viewmodel

import com.grid.tv.domain.epg.ProgrammeIndex
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program

/** Hot guide data combined for a single Compose collector — reduces EPG screen recompositions. */
data class HomeEpgGuideData(
    val channels: List<Channel>,
    val epgPrograms: List<Program>,
    val programmeIndex: ProgrammeIndex,
    val windowStart: Long,
    val windowDurationMs: Long
)
