package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgProgramScopeTest {
    @Test
    fun lookupKeysDifferByPlaylist() {
        val keyA = EpgProgramScope.rawLookupKey(playlistId = 1L, channelEpgId = "BBC One")
        val keyB = EpgProgramScope.rawLookupKey(playlistId = 2L, channelEpgId = "BBC One")
        assert(keyA != keyB)
    }

    @Test
    fun programmeIndexDoesNotCrossPollinatePlaylists() {
        val channelA = Channel(
            id = 1L,
            number = 1,
            name = "BBC One",
            group = "UK",
            logoUrl = null,
            epgId = "bbc.one",
            streamUrl = "http://a",
            playlistId = 10L,
            isFavorite = false
        )
        val channelB = channelA.copy(id = 2L, playlistId = 20L)
        val programs = listOf(
            Program(1L, "bbc.one", "A guide", "", 0L, 1L, ProgramGenre.GENERAL, null, playlistId = 10L),
            Program(2L, "bbc.one", "B guide", "", 0L, 1L, ProgramGenre.GENERAL, null, playlistId = 20L)
        )
        val index = ProgrammeIndex.build(listOf(channelA, channelB), programs)
        assertEquals("A guide", index.programsFor(channelA.id).single().title)
        assertEquals("B guide", index.programsFor(channelB.id).single().title)
    }
}
