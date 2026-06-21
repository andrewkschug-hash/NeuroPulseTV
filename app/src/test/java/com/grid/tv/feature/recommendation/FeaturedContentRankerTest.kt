package com.grid.tv.feature.recommendation

import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedContentRankerTest {

    private val ranker = FeaturedContentRanker(
        clock = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
    )

    @Test
    fun explicitFeaturedCategoryIsPrioritized() {
        val featured = sampleItem(
            streamId = 1L,
            title = "Spotlight Movie (2020)",
            categoryId = "featured"
        )
        val recent = sampleItem(
            streamId = 2L,
            title = "Fresh Release (2026)",
            categoryId = "movies"
        )
        val selection = ranker.selectFeaturedContent(
            catalog = listOf(recent, featured),
            categories = listOf(
                VodCategory(id = "featured", name = "Featured Tonight", playlistId = 1L),
                VodCategory(id = "movies", name = "Movies", playlistId = 1L)
            ),
            enrichmentByProviderKey = emptyMap(),
            genreAffinities = emptyMap(),
            bannerStats = emptyMap(),
            sessionSeed = 42L
        )

        assertEquals(featured.streamId, selection.carousel.first().streamId)
        assertEquals(0, selection.heroIndex)
    }

    @Test
    fun fallsBackToRecentReleasesWhenNoCuratedTags() {
        val older = sampleItem(streamId = 1L, title = "Old Title (2018)")
        val newest = sampleItem(streamId = 2L, title = "New Title (2026)")
        val middle = sampleItem(streamId = 3L, title = "Middle Title (2024)")

        val selection = ranker.selectFeaturedContent(
            catalog = listOf(older, middle, newest),
            categories = emptyList(),
            enrichmentByProviderKey = emptyMap(),
            genreAffinities = emptyMap(),
            bannerStats = emptyMap(),
            sessionSeed = 7L
        )

        assertTrue(selection.carousel.isNotEmpty())
        assertEquals(newest.streamId, selection.carousel.first().streamId)
    }

    @Test
    fun excludesItemsWithoutPosterOrPoorStreams() {
        val valid = sampleItem(streamId = 1L, title = "Valid Movie (2026)")
        val missingPoster = sampleItem(streamId = 2L, title = "No Art (2026)", posterUrl = null)
        val camRip = sampleItem(streamId = 3L, title = "Bad CAM (2026)")

        val selection = ranker.selectFeaturedContent(
            catalog = listOf(valid, missingPoster, camRip),
            categories = emptyList(),
            enrichmentByProviderKey = emptyMap(),
            genreAffinities = emptyMap(),
            bannerStats = emptyMap(),
            sessionSeed = 99L
        )

        assertEquals(1, selection.carousel.size)
        assertEquals(valid.streamId, selection.carousel.first().streamId)
    }

    private fun sampleItem(
        streamId: Long,
        title: String,
        categoryId: String = "movies",
        posterUrl: String? = "https://example.com/poster.jpg"
    ) = VodItem(
        id = streamId,
        title = title,
        streamId = streamId,
        streamUrl = "http://example.com/movie/$streamId",
        posterUrl = posterUrl,
        plot = null,
        cast = null,
        director = null,
        genre = "Drama",
        rating = "8.0",
        duration = "120",
        categoryId = categoryId,
        addedEpochSec = streamId,
        playlistId = 1L
    )
}
