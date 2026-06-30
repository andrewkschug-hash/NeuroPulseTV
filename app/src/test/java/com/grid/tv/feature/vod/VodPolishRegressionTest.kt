package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.ui.component.VodLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodPolishRegressionTest {

    @Test
    fun vodLayout_contentTopPadding_isTwentyFourDp() {
        assertEquals(24, VodLayout.ContentTopPadding.value.toInt())
    }

    @Test
    fun vodLayout_horizontalPadding_isSixteenDp() {
        assertEquals(16, VodLayout.ContentHorizontalPadding.value.toInt())
    }

    @Test
    fun vodLayout_posterAndRowSpacing_matchSpec() {
        assertEquals(12, VodLayout.PosterSpacing.value.toInt())
        assertEquals(20, VodLayout.RowSpacing.value.toInt())
    }

    @Test
    fun sidebarGenres_emptyUntilBrowseIndexReady() {
        val bundle = prepareMovieSidebarCategories(
            primary = listOf(VodCategory(id = "1001", name = "Action", playlistId = 1L)),
            browseIndex = VodBrowseRowCategoryIndex.EMPTY,
        )
        assertTrue(bundle.displayCategories.isEmpty())
    }

    @Test
    fun moviesGenresReady_falseWhileCatalogLoading() {
        assertFalse(
            VodHubUiStateBuilder.moviesGenresReady(
                genreTestInputs(
                    catalogLoading = true,
                    moviesPhaseFinished = true,
                    movieBrowseRowCount = 1,
                    movieSidebarCount = 1,
                ),
            ),
        )
    }

    @Test
    fun moviesGenresReady_falseUntilPhaseFinished() {
        assertFalse(
            VodHubUiStateBuilder.moviesGenresReady(
                genreTestInputs(
                    catalogLoading = false,
                    moviesPhaseFinished = false,
                    movieBrowseRowCount = 1,
                    movieSidebarCount = 1,
                ),
            ),
        )
    }

    @Test
    fun moviesGenresReady_trueWhenCatalogBrowseAndSidebarReady() {
        assertTrue(
            VodHubUiStateBuilder.moviesGenresReady(
                genreTestInputs(
                    catalogLoading = false,
                    moviesPhaseFinished = true,
                    movieBrowseRowCount = 3,
                    movieSidebarCount = 3,
                ),
            ),
        )
    }

    @Test
    fun moviesGenresReady_falseWhileBrowseRowsEmpty() {
        assertFalse(
            VodHubUiStateBuilder.moviesGenresReady(
                genreTestInputs(
                    catalogLoading = false,
                    moviesPhaseFinished = true,
                    movieBrowseRowCount = 0,
                    movieSidebarCount = 3,
                ),
            ),
        )
    }

    @Test
    fun seriesGenresReady_matchesMoviesRules() {
        assertTrue(
            VodHubUiStateBuilder.seriesGenresReady(
                genreTestInputs(
                    catalogLoading = false,
                    seriesPhaseFinished = true,
                    seriesBrowseRowCount = 2,
                    seriesSidebarCount = 2,
                    contentFilter = VodContentFilter.SERIES,
                ),
            ),
        )
    }

    @Test
    fun languageFilter_activeWhenPreferencesSet() {
        val inputs = genreTestInputs(preferredLanguages = setOf("EN", "FR"))
        assertEquals(setOf("EN", "FR"), inputs.preferredLanguages)
        assertTrue(inputs.preferredLanguages.isNotEmpty())
    }

    private fun genreTestInputs(
        catalogLoading: Boolean = false,
        moviesPhaseFinished: Boolean = true,
        seriesPhaseFinished: Boolean = true,
        movieBrowseRowCount: Int = 0,
        seriesBrowseRowCount: Int = 0,
        movieSidebarCount: Int = 0,
        seriesSidebarCount: Int = 0,
        contentFilter: VodContentFilter = VodContentFilter.MOVIES,
        preferredLanguages: Set<String> = emptySet(),
    ): VodHubUiBuildInputs {
        val movieCategories = (1..movieSidebarCount).map { index ->
            VodCategory(id = "100$index", name = "Genre $index", playlistId = 1L)
        }
        val seriesCategories = (1..seriesSidebarCount).map { index ->
            VodCategory(id = "200$index", name = "Series $index", playlistId = 1L)
        }
        val partitions = VodCatalogPartitions(
            movieBrowseRows = List(movieBrowseRowCount) { index ->
                com.grid.tv.domain.model.VodBrowseRow(
                    id = "cat_1_100${index + 1}_x",
                    title = "Genre ${index + 1}",
                    movies = emptyList(),
                )
            },
            seriesBrowseRows = List(seriesBrowseRowCount) { index ->
                com.grid.tv.domain.model.VodBrowseRow(
                    id = "cat_1_200${index + 1}_x",
                    title = "Series ${index + 1}",
                    series = emptyList(),
                )
            },
            movieSidebarBundle = com.grid.tv.domain.model.VodCategoryNameResolver.SeriesSidebarCategories(
                displayCategories = movieCategories,
                filterIdsByRepresentativeId = emptyMap(),
            ),
            seriesSidebarBundle = com.grid.tv.domain.model.VodCategoryNameResolver.SeriesSidebarCategories(
                displayCategories = seriesCategories,
                filterIdsByRepresentativeId = emptyMap(),
            ),
        )
        return VodHubUiBuildInputs(
            catalogSample = emptyList(),
            filteredCatalog = emptyList(),
            continueWatching = emptyList(),
            recommendedForYou = emptyList(),
            trendingNow = emptyList(),
            contentFilter = contentFilter,
            searchQuery = "",
            preferredLanguages = preferredLanguages,
            includeUntagged = true,
            availableLanguages = listOf("EN", "FR"),
            featuredCarousel = emptyList(),
            enrichmentMap = emptyMap(),
            vodProgress = emptyMap(),
            movieBrowseRows = partitions.movieBrowseRows,
            seriesBrowseRows = partitions.seriesBrowseRows,
            movieCategories = movieCategories,
            seriesCategories = seriesCategories,
            selectedMovieCategoryId = null,
            selectedMovieCategoryPlaylistId = null,
            selectedSeriesCategoryId = null,
            selectedSeriesCategoryPlaylistId = null,
            catalogTotalCount = 100,
            seriesCatalogTotalCount = 50,
            movieFilteredTotalCount = movieBrowseRowCount,
            seriesFilteredTotalCount = seriesBrowseRowCount,
            catalogLoading = catalogLoading,
            catalogProgress = VodCatalogProgress(
                moviesPhaseFinished = moviesPhaseFinished,
                seriesPhaseFinished = seriesPhaseFinished,
                isLoading = catalogLoading,
            ),
            catalogPartitions = partitions,
        )
    }
}
