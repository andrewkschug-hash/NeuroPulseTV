package com.grid.tv.ui.component

import androidx.compose.ui.unit.dp
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgProgramTitleDisplayTest {

    @Test
    fun hidesTitleWhenBlockTooNarrow() {
        val display = epgProgramTitleDisplay("Morning News", 40.dp)
        assertNull(display.visibleText)
        assertEquals(true, display.isTruncated)
    }

    @Test
    fun showsFullTitleWhenWideEnough() {
        val display = epgProgramTitleDisplay("News", 120.dp)
        assertEquals("News", display.visibleText)
        assertEquals(false, display.isTruncated)
    }

    @Test
    fun trimsWithoutEllipsisWhenPartiallyWide() {
        val title = "The Late Show with Someone Very Long"
        val display = epgProgramTitleDisplay(title, 72.dp)
        assertEquals(true, display.isTruncated)
        assertEquals(false, display.visibleText.orEmpty().endsWith("…"))
        assertEquals(true, title.startsWith(display.visibleText.orEmpty()))
    }

    @Test
    fun nextProgramAfter_returnsEarliestFollowingProgram() {
        val programs = listOf(
            program(1, 0L, 60L),
            program(2, 60L, 120L),
            program(3, 120L, 180L)
        )
        val next = nextProgramAfter(programs[0], programs)
        assertEquals(2L, next?.id)
    }

    private fun program(id: Long, startMin: Long, endMin: Long) = Program(
        id = id,
        channelEpgId = "ch",
        title = "Title $id",
        description = "",
        startTime = startMin * 60_000,
        endTime = endMin * 60_000,
        genre = ProgramGenre.GENERAL,
        catchupUrl = null
    )
}
