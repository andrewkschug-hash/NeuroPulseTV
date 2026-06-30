package com.grid.tv.data.db.mapper

import com.grid.tv.data.db.entity.VodCatalogEpisodeEntity
import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesEpisodeMappersTest {

    @Test
    fun toSeriesDetail_groupsBySeasonAndMapsFields() {
        val entities = listOf(
            episode(season = 1, episode = 2, title = "Ep 2"),
            episode(season = 1, episode = 1, title = "Ep 1"),
            episode(season = 2, episode = 1, title = "S2E1")
        )
        val detail = entities.toSeriesDetail()
        assertEquals("Series plot", detail.plot)
        assertEquals(2, detail.seasons.size)
        assertEquals(listOf(1, 2), detail.seasons.map { it.number })
        assertEquals(listOf("Ep 1", "Ep 2"), detail.seasons[0].episodes.map { it.title })
        assertEquals("http://stream/2", detail.seasons[0].episodes[1].streamUrl)
    }

    @Test
    fun toEpisodeEntities_roundTripsCoreFields() {
        val detail = SeriesDetail(
            plot = "Plot",
            seasons = listOf(
                SeriesSeason(
                    number = 1,
                    episodes = listOf(
                        SeriesEpisode(
                            id = 99L,
                            title = "Pilot",
                            extension = "mkv",
                            streamUrl = "http://x",
                            plot = "p",
                            duration = "45m",
                            episodeNumber = 1
                        )
                    )
                )
            )
        )
        val entities = detail.toEpisodeEntities(playlistId = 3L, seriesId = 7L, seriesTitle = "Show")
        assertEquals(1, entities.size)
        val entity = entities.single()
        assertEquals(3L, entity.playlistId)
        assertEquals(7L, entity.seriesId)
        assertEquals("mkv", entity.extension)
        assertEquals("http://x", entity.streamUrl)
        assertEquals("Plot", entity.seriesPlot)
        assertTrue(entity.fetchedAt > 0L)
    }

    private fun episode(season: Int, episode: Int, title: String) = VodCatalogEpisodeEntity(
        playlistId = 1L,
        seriesId = 2L,
        seriesTitle = "Show",
        seasonNumber = season,
        episodeNumber = episode,
        episodeId = episode.toLong(),
        episodeTitle = title,
        addedAt = 1L,
        extension = "mp4",
        streamUrl = "http://stream/$episode",
        plot = null,
        duration = "30m",
        seriesPlot = "Series plot",
        fetchedAt = 100L
    )
}
