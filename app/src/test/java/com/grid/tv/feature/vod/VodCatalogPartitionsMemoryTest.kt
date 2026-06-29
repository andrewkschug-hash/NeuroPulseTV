package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.domain.model.categoryBrowseRowId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCatalogPartitionsMemoryTest {

    @Test
    fun trimForMemoryPressure_allTab_dropsTabSpecificWallRows() {
        val partitions = samplePartitions()
        val trimmed = partitions.trimForMemoryPressure(VodContentFilter.ALL)

        assertTrue(trimmed.allStaticWallRows.isNotEmpty())
        assertTrue(trimmed.movieWallRows.isEmpty())
        assertTrue(trimmed.seriesWallRows.isEmpty())
        assertEquals(
            partitions.allPersonalizedWallRows.sumOf { it.items.size } +
                partitions.allStaticWallRows.sumOf { it.items.size },
            trimmed.estimatedWallItemCount(),
        )
    }

    @Test
    fun trimForMemoryPressure_moviesTab_keepsMovieWallRowsOnly() {
        val partitions = samplePartitions()
        val trimmed = partitions.trimForMemoryPressure(VodContentFilter.MOVIES)

        assertTrue(trimmed.movieWallRows.isNotEmpty())
        assertTrue(trimmed.allStaticWallRows.isEmpty())
        assertTrue(trimmed.seriesWallRows.isEmpty())
        assertTrue(trimmed.seriesBrowseRows.isEmpty())
    }

    @Test
    fun ensureWallRowsFor_buildsMovieRowsOnDemand() {
        val partitions = samplePartitions().copy(movieWallRows = emptyList())
        val ensured = partitions.ensureWallRowsFor(VodContentFilter.MOVIES)

        assertTrue(ensured.movieWallRows.isNotEmpty())
    }

    private fun samplePartitions(): VodCatalogPartitions {
        val movieRow = VodBrowseRow(
            id = categoryBrowseRowId(1L, "1006"),
            title = "Action",
            movies = List(3) { index ->
                com.grid.tv.domain.model.VodItem(
                    id = index.toLong(),
                    streamId = index.toLong(),
                    playlistId = 1L,
                    title = "Movie $index",
                    streamUrl = "http://example.com/$index",
                    posterUrl = null,
                    plot = null,
                    cast = null,
                    director = null,
                    genre = null,
                    rating = null,
                    duration = null,
                    categoryId = "1006",
                )
            },
        )
        val seriesRow = VodBrowseRow(
            id = categoryBrowseRowId(1L, "2001"),
            title = "Drama",
            series = List(2) { index ->
                com.grid.tv.domain.model.SeriesShow(
                    id = index.toLong(),
                    name = "Show $index",
                    coverUrl = null,
                    categoryId = "2001",
                    playlistId = 1L,
                )
            },
        )
        return buildVodCatalogPartitions(
            VodCatalogPartitionInputs(
                movieBrowseRows = listOf(movieRow),
                seriesBrowseRows = listOf(seriesRow),
                movieCategories = emptyList(),
                seriesCategories = emptyList(),
                continueWatching = emptyList(),
                trendingMovies = emptyList(),
                recommendedMovies = emptyList(),
            ),
            prefetchTabWallRows = true,
        )
    }
}
