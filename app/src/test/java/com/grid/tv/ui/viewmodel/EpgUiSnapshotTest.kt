package com.grid.tv.ui.viewmodel

import com.grid.tv.domain.epg.ProgrammeIndex
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EpgUiSnapshotTest {

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
        Program(1L, "bbc1.uk", "News", "", 100L, 200L, ProgramGenre.NEWS, null, playlistId = 1L)
    )

    @Test
    fun build_reusesPreviousInstanceWhenFingerprintMatches() {
        val first = EpgUiSnapshot.build(
            channels = listOf(channel),
            programs = programs,
            programmeIndex = ProgrammeIndex.build(listOf(channel), programs),
            windowStart = 1000L,
            windowDurationMs = 3_600_000L
        )
        val second = EpgUiSnapshot.build(
            channels = listOf(channel),
            programs = programs,
            programmeIndex = ProgrammeIndex.build(listOf(channel), programs),
            windowStart = 1000L,
            windowDurationMs = 3_600_000L,
            previous = first
        )
        assertSame(first, second)
        assertEquals(first.generation, second.generation)
    }

    @Test
    fun build_incrementsGenerationWhenChannelsChange() {
        val first = EpgUiSnapshot.build(
            channels = listOf(channel),
            programs = programs,
            programmeIndex = ProgrammeIndex.EMPTY,
            windowStart = 1000L,
            windowDurationMs = 3_600_000L
        )
        val second = EpgUiSnapshot.build(
            channels = listOf(channel, channel.copy(id = 2L, name = "Two")),
            programs = programs,
            programmeIndex = ProgrammeIndex.EMPTY,
            windowStart = 1000L,
            windowDurationMs = 3_600_000L,
            previous = first
        )
        assertEquals(first.generation + 1L, second.generation)
    }
}
