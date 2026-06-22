package com.grid.tv.feature.vod

import com.grid.tv.domain.model.SeriesDetail
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesEpisodeTitleNormalizerTest {

    private fun episode(
        id: Long,
        title: String,
        episodeNumber: Int? = null
    ) = SeriesEpisode(
        id = id,
        title = title,
        extension = "mp4",
        streamUrl = "http://example/stream/$id",
        plot = null,
        duration = null,
        episodeNumber = episodeNumber
    )

    @Test
    fun dedupesSameEpisodeNumber_prefersEnglishTaggedEntry() {
        val season = SeriesSeason(
            number = 1,
            episodes = listOf(
                episode(1, "FR - Le premier épisode", episodeNumber = 1),
                episode(2, "EN - The First Episode", episodeNumber = 1),
            )
        )
        val normalized = SeriesEpisodeTitleNormalizer.normalizeSeriesDetail(
            SeriesDetail(seasons = listOf(season), plot = null)
        )
        val titles = normalized.seasons.single().episodes.map { it.title }
        assertEquals(1, titles.size)
        assertEquals("The First Episode", titles.single())
    }

    @Test
    fun stripsLanguageMarkers_whenSeasonIsConsistent() {
        val season = SeriesSeason(
            number = 2,
            episodes = listOf(
                episode(10, "EN - Pilot", episodeNumber = 1),
                episode(11, "EN - Aftermath", episodeNumber = 2),
            )
        )
        val normalized = SeriesEpisodeTitleNormalizer.normalizeSeriesDetail(
            SeriesDetail(seasons = listOf(season), plot = null)
        )
        val titles = normalized.seasons.single().episodes.map { it.title }
        assertEquals(listOf("Pilot", "Aftermath"), titles)
    }

    @Test
    fun usesGenericTitles_whenMixedExplicitLanguageCodesInSeason() {
        val season = SeriesSeason(
            number = 1,
            episodes = listOf(
                episode(1, "EN - Pilot", episodeNumber = 1),
                episode(2, "FR - Le deuxième", episodeNumber = 2),
            )
        )
        val normalized = SeriesEpisodeTitleNormalizer.normalizeSeriesDetail(
            SeriesDetail(seasons = listOf(season), plot = null)
        )
        val titles = normalized.seasons.single().episodes.map { it.title }
        assertEquals(
            listOf(
                "S01E01 • Episode 1",
                "S01E02 • Episode 2",
            ),
            titles
        )
    }

    @Test
    fun usesGenericTitles_whenMixedImplicitFrenchAndEnglishWithinSeason() {
        val season = SeriesSeason(
            number = 3,
            episodes = listOf(
                episode(1, "The Heist", episodeNumber = 1),
                episode(2, "La chute", episodeNumber = 2),
            )
        )
        val normalized = SeriesEpisodeTitleNormalizer.normalizeSeriesDetail(
            SeriesDetail(seasons = listOf(season), plot = null)
        )
        val titles = normalized.seasons.single().episodes.map { it.title }
        assertTrue(titles.all { it.startsWith("S03E") && it.contains("Episode") })
        assertEquals(2, titles.size)
    }

    @Test
    fun genericEpisodeTitle_formatsSeasonAndEpisodeNumbers() {
        assertEquals(
            "S01E05 • Episode 5",
            SeriesEpisodeTitleNormalizer.genericEpisodeTitle(seasonNumber = 1, episodeNumber = 5)
        )
    }

    @Test
    fun stripVodLanguageMarkers_removesPrefixAndSuffixTags() {
        assertEquals("Inception", stripVodLanguageMarkers("EN - Inception"))
        assertEquals("Amélie", stripVodLanguageMarkers("FR - Amélie"))
    }
}
