package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeIndexTest {

    private val channel = Channel(
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

    private val programs = listOf(
        Program(1L, "bbc1.uk", "News", "", 100L, 200L, ProgramGenre.NEWS, null),
        Program(2L, "other", "Other", "", 0L, 1L, ProgramGenre.GENERAL, null),
        Program(3L, "bbc1.uk", "Late", "", 300L, 400L, ProgramGenre.GENERAL, null)
    )

    @Test
    fun programsFor_returnsSortedMatchesForChannelId() {
        val index = ProgrammeIndex.build(listOf(channel), programs)

        val result = index.programsFor(channel.id)

        assertEquals(listOf("News", "Late"), result.map { it.title })
    }

    @Test
    fun programsFor_fallsBackToChannelName() {
        val nameChannel = channel.copy(epgId = null, name = "CNN")
        val namePrograms = listOf(
            Program(1L, "CNN", "Live", "", 0L, 1L, ProgramGenre.NEWS, null)
        )
        val index = ProgrammeIndex.build(listOf(nameChannel), namePrograms)

        assertEquals("Live", index.programsFor(nameChannel.id).single().title)
    }

    @Test
    fun programmesForChannel_matchesIndexLookup() {
        val index = ProgrammeIndex.build(listOf(channel), programs)

        assertEquals(
            programmesForChannel(channel, programs),
            index.programsFor(channel.id)
        )
    }
}
