package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodWallRowTest {

    @Test
    fun buildVodWallRows_allTab_ordersContinueWatchingThenRecentlyAddedBeforeCategories() {
        val movie = VodItem(
            id = 1L,
            streamId = 1L,
            playlistId = 1L,
            title = "Movie",
            streamUrl = "http://example.com/movie",
            posterUrl = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            rating = null,
            duration = null,
            categoryId = "1",
        )
        val show = SeriesShow(
            id = 2L,
            name = "Show",
            coverUrl = null,
            categoryId = "2",
            playlistId = 1L,
        )
        val rows = buildVodWallRows(
            filter = VodContentFilter.ALL,
            continueWatching = listOf(
                ContinueWatchingItem(
                    contentKey = "cw1",
                    contentType = ContinueWatchingContentType.MOVIE,
                    title = "Half Watched",
                    posterUrl = null,
                    streamUrl = "http://example.com",
                    positionMs = 30_000L,
                    durationMs = 120_000L,
                    lastWatchedAt = 1L,
                ),
            ),
            trendingMovies = listOf(movie),
            recommendedMovies = listOf(movie),
            movieBrowseRows = listOf(
                VodBrowseRow("recent", "Recently Added", movies = listOf(movie)),
                VodBrowseRow("100", "Action", movies = listOf(movie)),
            ),
            seriesBrowseRows = listOf(
                VodBrowseRow("all", "All Series", series = listOf(show)),
                VodBrowseRow("200", "Drama", series = listOf(show)),
            ),
        )

        assertTrue(rows.first().id == "continue_watching")
        assertEquals("movie_recent", rows[1].id)
        assertEquals("Recently Added", rows[1].title)
        val categoryIndex = rows.indexOfFirst { it.id == "movie_100" }
        val trendingIndex = rows.indexOfFirst { it.id == "trending" }
        assertTrue(categoryIndex in 2 until trendingIndex)
    }

    @Test
    fun splitHomeWallRows_separatesLeadRowsFromCategoryRows() {
        val rows = listOf(
            VodWallRow("continue_watching", "Continue Watching", emptyList()),
            VodWallRow("movie_recent", "Recently Added", emptyList()),
            VodWallRow("movie_100", "Action", emptyList()),
        )
        val (lead, tail) = splitHomeWallRows(rows)
        assertEquals(listOf("continue_watching", "movie_recent"), lead.map { it.id })
        assertEquals(listOf("movie_100"), tail.map { it.id })
    }

    @Test
    fun lazyColumnIndexForWallRow_placesHeroAfterLeadRows() {
        assertEquals(0, lazyColumnIndexForWallRow(0, leadRowCount = 2, heroVisible = true))
        assertEquals(3, lazyColumnIndexForWallRow(2, leadRowCount = 2, heroVisible = true))
    }
}
