package com.grid.tv.ui.component

import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgProgramIndexNavigationTest {

    private fun program(id: Long, start: Long, end: Long) = Program(
        id = id,
        channelEpgId = "ch",
        title = "Show $id",
        description = "",
        startTime = start,
        endTime = end,
        genre = ProgramGenre.GENERAL,
        catchupUrl = null
    )

    @Test
    fun programIndexForTime_returnsContainingProgram() {
        val progs = listOf(
            program(1, 100L, 200L),
            program(2, 200L, 300L),
            program(3, 300L, 400L)
        )
        assertEquals(0, programIndexForTime(progs, 150L))
        assertEquals(1, programIndexForTime(progs, 250L))
    }

    @Test
    fun programIndexForTime_fallsBackToNearestEarlierStart() {
        val progs = listOf(
            program(1, 100L, 200L),
            program(2, 200L, 300L)
        )
        assertEquals(0, programIndexForTime(progs, 199L))
        assertEquals(0, programIndexForTime(progs, 50L))
    }
}
