package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.ui.component.VodCatalogOnboardingInputs
import com.grid.tv.ui.component.VodCatalogOnboardingTab
import com.grid.tv.ui.component.countNonPersonalWallRows
import com.grid.tv.ui.component.isSeriesCatalogStillLoading
import com.grid.tv.ui.component.shouldShowVodCatalogEmptyState
import com.grid.tv.ui.component.shouldShowVodCatalogOnboarding
import com.grid.tv.util.VodPerfLogger

object VodHubUiStateBuilder {

    fun build(inputs: VodHubUiBuildInputs): VodUiState =
        VodPerfLogger.trace("buildVodUiState", "catalog=${inputs.catalogSample.size}") {
            buildInternal(inputs)
        }

    private fun buildInternal(inputs: VodHubUiBuildInputs): VodUiState {
        val languageFilterActive = inputs.preferredLanguages.isNotEmpty()
        val showInlineSearch = inputs.contentFilter == VodContentFilter.SEARCH
        val searchBlank = inputs.searchQuery.isBlank()
        val partitions = inputs.catalogPartitions

        val wallRows = if (inputs.searchQuery.isNotBlank()) {
            emptyList()
        } else {
            partitions.wallRowsFor(inputs.contentFilter)
        }
        val wallRowsRevision = if (inputs.searchQuery.isNotBlank()) {
            "search"
        } else {
            partitions.wallRowsRevisionFor(inputs.contentFilter)
        }

        val sidebarMovie = partitions.movieSidebarBundle.displayCategories
        val sidebarSeries = partitions.seriesSidebarBundle.displayCategories
        val genreLabels = when (inputs.contentFilter) {
            VodContentFilter.MOVIES -> listOf("All") + sidebarMovie.map { it.name }
            VodContentFilter.SERIES -> listOf("All") + sidebarSeries.map { it.name }
            else -> emptyList()
        }
        val selectedGenreIndex = resolveSelectedGenreIndex(
            contentFilter = inputs.contentFilter,
            selectedMovieCategoryId = inputs.selectedMovieCategoryId,
            selectedMovieCategoryPlaylistId = inputs.selectedMovieCategoryPlaylistId,
            sidebarMovieCategories = sidebarMovie,
            selectedSeriesCategoryId = inputs.selectedSeriesCategoryId,
            selectedSeriesCategoryPlaylistId = inputs.selectedSeriesCategoryPlaylistId,
            sidebarSeriesCategories = sidebarSeries
        )

        val combinedCatalogCount = inputs.catalogTotalCount + inputs.seriesCatalogTotalCount
        val onboardingInputs = VodCatalogOnboardingInputs(
            catalogLoading = inputs.catalogLoading,
            progress = inputs.catalogProgress,
            tab = when (inputs.contentFilter) {
                VodContentFilter.MOVIES -> VodCatalogOnboardingTab.MOVIES
                VodContentFilter.SERIES -> VodCatalogOnboardingTab.SERIES
                else -> VodCatalogOnboardingTab.ALL
            },
            browseRowCount = when (inputs.contentFilter) {
                VodContentFilter.MOVIES -> partitions.movieBrowseRows.size
                VodContentFilter.SERIES -> partitions.seriesBrowseRows.size
                VodContentFilter.ALL -> partitions.movieBrowseRows.size + partitions.seriesBrowseRows.size
                VodContentFilter.SEARCH -> 0
            },
            categoryCount = when (inputs.contentFilter) {
                VodContentFilter.MOVIES -> inputs.movieCategories.size
                VodContentFilter.SERIES -> inputs.seriesCategories.size
                VodContentFilter.ALL -> inputs.movieCategories.size + inputs.seriesCategories.size
                VodContentFilter.SEARCH -> 0
            },
            wallRowCount = if (inputs.contentFilter == VodContentFilter.ALL && searchBlank) wallRows.size else 0,
            nonPersonalWallRowCount = if (inputs.contentFilter == VodContentFilter.ALL && searchBlank) {
                countNonPersonalWallRows(wallRows)
            } else {
                0
            },
            wallItemCount = if (inputs.contentFilter == VodContentFilter.ALL && searchBlank) {
                wallRows.sumOf { it.items.size }
            } else {
                0
            },
            pagedItemCount = when (inputs.contentFilter) {
                VodContentFilter.MOVIES -> inputs.movieFilteredTotalCount
                VodContentFilter.SERIES -> inputs.seriesFilteredTotalCount
                VodContentFilter.ALL, VodContentFilter.SEARCH -> 0
            },
            catalogTotalCount = combinedCatalogCount
        )

        val showOnboarding = inputs.contentFilter != VodContentFilter.SEARCH &&
            shouldShowVodCatalogOnboarding(onboardingInputs)

        val showCatalogEmptyState = inputs.contentFilter == VodContentFilter.ALL &&
            !showOnboarding &&
            !showInlineSearch &&
            wallRows.isEmpty() &&
            shouldShowVodCatalogEmptyState(
                catalogLoading = inputs.catalogLoading,
                progress = inputs.catalogProgress,
                tab = VodCatalogOnboardingTab.ALL,
                catalogTotalCount = combinedCatalogCount,
                hasContinueWatching = inputs.continueWatching.isNotEmpty()
            )

        val showLanguageFilteredEmpty = inputs.contentFilter == VodContentFilter.ALL &&
            !showOnboarding &&
            !showInlineSearch &&
            wallRows.isEmpty() &&
            !showCatalogEmptyState &&
            combinedCatalogCount > 0 &&
            languageFilterActive

        val surfaceState = VodHubSurfaceStateResolver.resolveAllTab(
            contentFilter = inputs.contentFilter,
            onboardingInputs = onboardingInputs,
            showOnboarding = showOnboarding,
            wallRowCount = if (inputs.contentFilter == VodContentFilter.ALL && searchBlank) wallRows.size else 0,
            catalogLoading = inputs.catalogLoading,
            catalogProgress = inputs.catalogProgress,
            combinedCatalogCount = combinedCatalogCount,
            hasContinueWatching = inputs.continueWatching.isNotEmpty(),
            languageFilterActive = languageFilterActive,
            continueWatchingOnly = false,
        )

        return VodUiState(
            isLoading = inputs.catalogLoading,
            showOnboarding = showOnboarding,
            surfaceState = surfaceState,
            showCatalogEmptyState = showCatalogEmptyState,
            showLanguageFilteredEmpty = showLanguageFilteredEmpty,
            languageFilterActive = languageFilterActive,
            catalog = inputs.catalogSample,
            filteredCatalog = inputs.filteredCatalog,
            wallRows = wallRows,
            wallRowsRevision = wallRowsRevision,
            preferredLanguages = inputs.preferredLanguages,
            includeUntagged = inputs.includeUntagged,
            availableLanguages = inputs.availableLanguages,
            continueWatching = inputs.continueWatching,
            recommendedForYou = inputs.recommendedForYou,
            trendingNow = inputs.trendingNow,
            contentFilter = inputs.contentFilter,
            searchQuery = inputs.searchQuery,
            sidebar = VodSidebarUiState(
                movieCategories = sidebarMovie,
                seriesCategories = sidebarSeries,
                movieFilterIdsByRepresentativeId = partitions.movieSidebarBundle.filterIdsByRepresentativeId,
                seriesFilterIdsByRepresentativeId = partitions.seriesSidebarBundle.filterIdsByRepresentativeId,
                genreLabels = genreLabels,
                selectedGenreIndex = selectedGenreIndex,
                selectedMovieCategoryId = inputs.selectedMovieCategoryId,
                selectedMovieCategoryPlaylistId = inputs.selectedMovieCategoryPlaylistId,
                selectedSeriesCategoryId = inputs.selectedSeriesCategoryId,
                selectedSeriesCategoryPlaylistId = inputs.selectedSeriesCategoryPlaylistId
            ),
            catalogUi = VodCatalogUiState(
                catalogSampleCount = inputs.catalogSample.size,
                filteredCatalogCount = inputs.filteredCatalog.size,
                catalogTotalCount = inputs.catalogTotalCount,
                seriesCatalogTotalCount = inputs.seriesCatalogTotalCount,
                combinedCatalogCount = combinedCatalogCount,
                movieFilteredTotalCount = inputs.movieFilteredTotalCount,
                seriesFilteredTotalCount = inputs.seriesFilteredTotalCount,
                catalogLoading = inputs.catalogLoading,
                catalogProgress = inputs.catalogProgress,
                isSeriesStillLoading = isSeriesCatalogStillLoading(
                    catalogLoading = inputs.catalogLoading,
                    progress = inputs.catalogProgress,
                ),
            ),
            hero = VodHeroUiState(
                featuredCarousel = inputs.featuredCarousel
            ),
            onboardingInputs = onboardingInputs,
            enrichmentMap = inputs.enrichmentMap,
            vodProgress = inputs.vodProgress,
            movieBrowseRows = partitions.movieBrowseRows,
            seriesBrowseRows = partitions.seriesBrowseRows,
            catalogPartitions = partitions
        )
    }

    private fun resolveSelectedGenreIndex(
        contentFilter: VodContentFilter,
        selectedMovieCategoryId: String?,
        selectedMovieCategoryPlaylistId: Long?,
        sidebarMovieCategories: List<com.grid.tv.domain.model.VodCategory>,
        selectedSeriesCategoryId: String?,
        selectedSeriesCategoryPlaylistId: Long?,
        sidebarSeriesCategories: List<com.grid.tv.domain.model.VodCategory>
    ): Int = when (contentFilter) {
        VodContentFilter.MOVIES -> {
            if (selectedMovieCategoryId == null) {
                0
            } else {
                sidebarMovieCategories.indexOfFirst {
                    it.id == selectedMovieCategoryId &&
                        it.playlistId == selectedMovieCategoryPlaylistId
                }.takeIf { it >= 0 }?.plus(1) ?: 0
            }
        }
        VodContentFilter.SERIES -> {
            if (selectedSeriesCategoryId == null) {
                0
            } else {
                sidebarSeriesCategories.indexOfFirst {
                    it.id == selectedSeriesCategoryId &&
                        it.playlistId == selectedSeriesCategoryPlaylistId
                }.takeIf { it >= 0 }?.plus(1) ?: 0
            }
        }
        else -> 0
    }
}
