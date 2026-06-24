package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        Program(1L, "bbc1.uk", "News", "", 100L, 200L, ProgramGenre.NEWS, null, playlistId = 1L),
        Program(2L, "other", "Other", "", 0L, 1L, ProgramGenre.GENERAL, null, playlistId = 1L),
        Program(3L, "bbc1.uk", "Late", "", 300L, 400L, ProgramGenre.GENERAL, null, playlistId = 1L)
    )

    @Test
    fun buildWithCache_skipsWhenFingerprintUnchanged() {
        val first = ProgrammeIndex.buildWithCache(null, listOf(channel), programs)
        val second = ProgrammeIndex.buildWithCache(first.cache, listOf(channel), programs)
        assertEquals(true, second.skipped)
        assertEquals(first.index.programsFor(channel.id), second.index.programsFor(channel.id))
    }

    @Test
    fun buildWithCache_incrementallyAppendsNewChannels() {
        val channelTwo = channel.copy(id = 2L, name = "BBC Two", epgId = "bbc2.uk")
        val first = ProgrammeIndex.buildWithCache(null, listOf(channel), programs)
        val second = ProgrammeIndex.buildWithCache(
            first.cache,
            listOf(channel, channelTwo),
            programs
        )
        assertEquals(true, second.incrementalChannels)
        assertEquals(listOf("News", "Late"), second.index.programsFor(channel.id).map { it.title })
        assertTrue(second.index.programsFor(channelTwo.id).isEmpty())
    }

    @Test
    fun buildWithCache_fullRebuildUnderTwoHundredMsForTypicalGuideWindow() {
        val channels = (1L..200L).map { id ->
            channel.copy(id = id, epgId = "ch$id", name = "Channel $id")
        }
        val windowPrograms = (1L..2000L).map { id ->
            Program(
                id,
                "ch${id % 200 + 1}",
                "Program $id",
                "",
                id * 1000L,
                id * 1000L + 3_600_000L,
                ProgramGenre.GENERAL,
                null,
                playlistId = 1L
            )
        }
        val startNs = System.nanoTime()
        val result = ProgrammeIndex.buildWithCache(null, channels, windowPrograms)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        assertEquals(200, result.index.channelCount)
        assertTrue("index build took ${elapsedMs}ms", elapsedMs < 200)
    }

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
            Program(1L, "CNN", "Live", "", 0L, 1L, ProgramGenre.NEWS, null, playlistId = 1L)
        )
        val index = ProgrammeIndex.build(listOf(nameChannel), namePrograms)

        assertEquals("Live", index.programsFor(nameChannel.id).single().title)
    }

    @Test
    fun programsFor_isolatesIdenticalEpgIdsAcrossPlaylists() {
        val playlistOneChannel = channel.copy(id = 1L, playlistId = 1L, epgId = "cnn.us")
        val playlistTwoChannel = channel.copy(id = 2L, playlistId = 2L, epgId = "cnn.us")
        val mixedPrograms = listOf(
            Program(10L, "cnn.us", "Playlist 1 News", "", 100L, 200L, ProgramGenre.NEWS, null, playlistId = 1L),
            Program(20L, "cnn.us", "Playlist 2 News", "", 100L, 200L, ProgramGenre.NEWS, null, playlistId = 2L)
        )
        val index = ProgrammeIndex.build(listOf(playlistOneChannel, playlistTwoChannel), mixedPrograms)

        assertEquals("Playlist 1 News", index.programsFor(playlistOneChannel.id).single().title)
        assertEquals("Playlist 2 News", index.programsFor(playlistTwoChannel.id).single().title)
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
