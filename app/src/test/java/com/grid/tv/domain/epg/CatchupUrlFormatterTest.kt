package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupUrlFormatterTest {

    private val channel = Channel(
        id = 1,
        number = 1,
        name = "ESPN",
        group = "Sports",
        logoUrl = null,
        epgId = "espn.us",
        streamUrl = "http://example.com/live/espn.ts",
        playlistId = 1,
        isFavorite = false,
        catchupDays = 7,
        catchupSource = null,
        catchupMode = "append"
    )

    private val program = Program(
        id = 10,
        channelEpgId = "espn.us",
        title = "SportsCenter",
        description = "",
        startTime = 1_700_000_000_000L,
        endTime = 1_700_003_600_000L,
        genre = com.grid.tv.domain.model.ProgramGenre.SPORTS,
        catchupUrl = null
    )

    @Test
    fun build_appendMode_addsUtcQuery() {
        val url = CatchupUrlFormatter.build(program, channel)
        assertNotNull(url)
        assertEquals(true, url!!.contains("utc=1700000000"))
        assertEquals(true, url.contains("lutc=1700003600"))
    }

    @Test
    fun build_usesCatchupSourceTemplate() {
        val templated = channel.copy(
            catchupSource = "http://cdn/{channel}?start={utc}&end={lutc}"
        )
        val url = CatchupUrlFormatter.build(program, templated)
        assertNotNull(url)
        assertEquals(true, url!!.contains("espn.us"))
        assertEquals(true, url.contains("start=1700000000"))
    }

    @Test
    fun build_disabledWhenNoCatchupSupport() {
        val noCatchup = channel.copy(catchupDays = 0, catchupMode = null, catchupSource = null)
        assertNull(CatchupUrlFormatter.build(program, noCatchup))
    }

    @Test
    fun build_shiftMode_usesTimeshiftPath() {
        val shiftChannel = channel.copy(
            streamUrl = "http://example.com/live/user/pass/123.ts",
            catchupMode = "shift"
        )
        val url = CatchupUrlFormatter.build(program, shiftChannel)
        assertNotNull(url)
        assertTrue(url!!.contains("/timeshift/"))
        assertTrue(url.contains("/3600/1700000000"))
    }
}
