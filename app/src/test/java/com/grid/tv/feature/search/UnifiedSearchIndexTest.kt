package com.grid.tv.feature.search

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.SearchResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSearchIndexTest {

    private val index = UnifiedSearchIndex()

    @Test
    fun search_espn_returnsChannelMatchesFirst() {
        index.rebuild(
            UnifiedSearchIndex.Snapshot(
                channels = listOf(
                    channel(1, "ESPN"),
                    channel(2, "ESPN 2"),
                    channel(3, "ESPN Deportes"),
                    channel(4, "CNN")
                )
            )
        )
        val results = index.search("espn")
        assertTrue(results.channels.isNotEmpty())
        assertEquals("ESPN", results.channels.first().primaryTitle)
        assertTrue(results.channels.any { it.primaryTitle == "ESPN 2" })
        assertEquals(SearchResultType.CHANNEL, results.channels.first().type)
    }

    @Test
    fun search_office_matchesSeriesTitles() {
        index.rebuild(
            UnifiedSearchIndex.Snapshot(
                series = listOf(
                    com.grid.tv.domain.model.SeriesShow(1, "The Office", null),
                    com.grid.tv.domain.model.SeriesShow(2, "Office Space", null)
                ),
                movies = listOf(
                    com.grid.tv.domain.model.VodItem(
                        id = 1,
                        title = "Office Space",
                        streamId = 10,
                        streamUrl = "http://x",
                        posterUrl = null,
                        plot = null,
                        cast = null,
                        director = null,
                        genre = "Comedy",
                        rating = null,
                        duration = null
                    )
                )
            )
        )
        val results = index.search("office")
        assertTrue(results.series.any { it.primaryTitle.contains("Office", ignoreCase = true) })
    }

    @Test
    fun search_tomCruise_findsActor() {
        index.rebuild(
            UnifiedSearchIndex.Snapshot(
                actors = listOf(
                    UnifiedSearchIndex.IndexedActor(
                        name = "Tom Cruise",
                        knownTitles = listOf("Top Gun", "Mission: Impossible"),
                        posterUrl = null
                    )
                ),
                movies = listOf(
                    com.grid.tv.domain.model.VodItem(
                        id = 2,
                        title = "Top Gun",
                        streamId = 11,
                        streamUrl = "http://y",
                        posterUrl = null,
                        plot = null,
                        cast = "Tom Cruise",
                        director = null,
                        genre = "Action",
                        rating = null,
                        duration = null
                    )
                )
            )
        )
        val results = index.search("tom cruise")
        assertTrue(results.actors.any { it.primaryTitle == "Tom Cruise" })
    }

    private fun channel(id: Long, name: String) = Channel(
        id = id,
        number = id.toInt(),
        name = name,
        group = "Sports",
        logoUrl = null,
        epgId = "epg-$id",
        streamUrl = "http://stream",
        playlistId = 1,
        isFavorite = false
    )
}
