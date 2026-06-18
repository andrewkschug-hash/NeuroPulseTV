package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeLookupTest {

    @Test
    fun programmesForChannel_matchesByEpgIdOrName() {
        val channel = Channel(
            id = 1L,
            number = 1,
            name = "BBC One",
            group = "UK",
            logoUrl = null,
            epgId = "bbc1.uk",
            streamUrl = "http://x",
            playlistId = 1L,
            isFavorite = false
        )
        val programs = listOf(
            Program(1L, "bbc1.uk", "News", "", 0L, 1L, ProgramGenre.NEWS, null),
            Program(2L, "other", "Other", "", 0L, 1L, ProgramGenre.GENERAL, null)
        )
        assertEquals(1, programmesForChannel(channel, programs).size)
    }

    @Test
    fun programmesForChannel_fallsBackToChannelName() {
        val channel = Channel(
            id = 1L,
            number = 1,
            name = "CNN",
            group = "News",
            logoUrl = null,
            epgId = null,
            streamUrl = "http://x",
            playlistId = 1L,
            isFavorite = false
        )
        val programs = listOf(
            Program(1L, "CNN", "Live", "", 0L, 1L, ProgramGenre.NEWS, null)
        )
        assertEquals("Live", programmesForChannel(channel, programs).single().title)
    }
}
