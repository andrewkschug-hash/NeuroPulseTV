package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodCatalogEmptyReason
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.vodEmptyMessage
import com.grid.tv.domain.model.vodEmptyTitle
import com.grid.tv.ui.component.VodCatalogOnboardingInputs
import com.grid.tv.ui.component.VodCatalogOnboardingTab
import com.grid.tv.ui.component.shouldShowVodCatalogEmptyState
import com.grid.tv.ui.component.shouldShowVodCatalogOnboarding

/**
 * Single place that derives [VodHubSurfaceState] from repository / paging inputs.
 * All composables and focus logic should consume the result instead of re-evaluating booleans.
 */
object VodHubSurfaceStateResolver {

    fun resolveAllTab(
        contentFilter: VodContentFilter,
        onboardingInputs: VodCatalogOnboardingInputs,
        showOnboarding: Boolean,
        wallRowCount: Int,
        catalogLoading: Boolean,
        catalogProgress: com.grid.tv.domain.model.VodCatalogProgress,
        combinedCatalogCount: Int,
        hasContinueWatching: Boolean,
        languageFilterActive: Boolean,
        continueWatchingOnly: Boolean,
    ): VodHubSurfaceState {
        if (contentFilter != VodContentFilter.ALL) {
            return VodHubSurfaceState.Ready()
        }
        if (showOnboarding && wallRowCount == 0) {
            return VodHubSurfaceState.Loading(
                onboardingInputs = onboardingInputs,
                compactOnboarding = false,
            )
        }
        if (wallRowCount == 0) {
            if (continueWatchingOnly) {
                return VodHubSurfaceState.Empty(
                    title = "",
                    message = "",
                    canRetry = false,
                    variant = VodHubSurfaceState.Empty.EmptyVariant.ALL_CONTINUE_WATCHING_ONLY,
                    onboardingInputs = onboardingInputs,
                )
            }
            if (shouldShowVodCatalogEmptyState(
                    catalogLoading = catalogLoading,
                    progress = catalogProgress,
                    tab = VodCatalogOnboardingTab.ALL,
                    catalogTotalCount = combinedCatalogCount,
                    hasContinueWatching = hasContinueWatching,
                )
            ) {
                return VodHubSurfaceState.Empty(
                    title = "Nothing to watch yet",
                    message = "Connect a playlist to add movies and series to your library.",
                    canRetry = true,
                    variant = VodHubSurfaceState.Empty.EmptyVariant.ALL_CATALOG,
                    onboardingInputs = onboardingInputs,
                )
            }
            if (languageFilterActive && combinedCatalogCount > 0) {
                return VodHubSurfaceState.Empty(
                    title = "No titles match your language preferences.",
                    message = "Try selecting additional languages, enable untagged content, or clear the language filter.",
                    canRetry = true,
                    variant = VodHubSurfaceState.Empty.EmptyVariant.ALL_LANGUAGE_FILTER,
                    onboardingInputs = onboardingInputs,
                )
            }
        }
        return VodHubSurfaceState.Ready(
            wallRowCount = wallRowCount,
            showOnboardingStrip = showOnboarding && wallRowCount > 0,
            onboardingInputs = if (showOnboarding && wallRowCount > 0) onboardingInputs else null,
        )
    }

    fun resolveBrowseTab(inputs: VodHubBrowseSurfaceInputs): VodHubSurfaceState {
        val onboardingInputs = VodCatalogOnboardingInputs(
            catalogLoading = inputs.catalogLoading,
            progress = inputs.catalogProgress,
            tab = inputs.tab,
            browseRowCount = inputs.browseRowCount,
            categoryCount = inputs.categoryCount,
            pagedItemCount = inputs.pagedItemCount,
            catalogTotalCount = inputs.catalogTotalCount,
        )
        val showOnboarding = shouldShowVodCatalogOnboarding(onboardingInputs)
        if (showOnboarding) {
            return VodHubSurfaceState.Loading(
                onboardingInputs = onboardingInputs,
                showCatalogProgressBar = false,
            )
        }

        val isMovies = inputs.tab == VodCatalogOnboardingTab.MOVIES
        val tabLoading = when (inputs.tab) {
            VodCatalogOnboardingTab.MOVIES ->
                inputs.catalogProgress.isLoading && !inputs.catalogProgress.isMoviesPhaseComplete
            VodCatalogOnboardingTab.SERIES ->
                inputs.catalogProgress.isLoading &&
                    inputs.catalogProgress.isMoviesPhaseComplete &&
                    !inputs.catalogProgress.isSeriesPhaseComplete
            else -> false
        }
        val emptyReason = if (isMovies) {
            inputs.catalogStatus.moviesEmptyReason(
                filteredCount = inputs.filteredTotalCount,
                catalogTotal = inputs.catalogTotalCount,
                categoryId = inputs.selectedCategoryId,
                searchQuery = inputs.searchQuery,
            )
        } else {
            inputs.catalogStatus.seriesEmptyReason(
                filteredCount = inputs.filteredTotalCount,
                catalogTotal = inputs.catalogTotalCount,
                category = inputs.selectedCategoryId ?: "All",
                searchQuery = inputs.searchQuery,
            )
        }
        val showEmptyGrid = inputs.pagedItemCount == 0 &&
            !tabLoading &&
            !inputs.pagingRefreshing &&
            when (inputs.tab) {
                VodCatalogOnboardingTab.MOVIES -> inputs.catalogProgress.moviesPhaseFinished
                VodCatalogOnboardingTab.SERIES ->
                    inputs.catalogProgress.seriesPhaseFinished ||
                        emptyReason != VodCatalogEmptyReason.NOT_LOADED
                else -> true
            }

        if (showEmptyGrid) {
            val canRetry = emptyReason != VodCatalogEmptyReason.FILTERED_EMPTY
            if (emptyReason == VodCatalogEmptyReason.FETCH_FAILED ||
                emptyReason == VodCatalogEmptyReason.PARSE_FAILED
            ) {
                return VodHubSurfaceState.Error(
                    title = emptyReason.vodEmptyTitle(isMovies = isMovies),
                    message = emptyReason.vodEmptyMessage(inputs.catalogStatus, isMovies = isMovies),
                    canRetry = canRetry,
                    onboardingInputs = onboardingInputs,
                )
            }
            return VodHubSurfaceState.Empty(
                title = emptyReason.vodEmptyTitle(isMovies = isMovies),
                message = emptyReason.vodEmptyMessage(inputs.catalogStatus, isMovies = isMovies),
                canRetry = canRetry,
                reason = emptyReason,
                variant = VodHubSurfaceState.Empty.EmptyVariant.BROWSE_GRID,
                onboardingInputs = onboardingInputs,
            )
        }

        return VodHubSurfaceState.Ready(
            gridItemCount = inputs.pagedItemCount,
            categoryCount = inputs.categoryCount,
            browseRowCount = inputs.browseRowCount,
        )
    }

    /** Mirrors legacy [isBrowseGridLoading] in VodHubScreen — used for focus warm-up only. */
    fun isBrowseGridLoading(inputs: VodHubBrowseGridFocusInputs): Boolean {
        return when (inputs.contentFilter) {
            VodContentFilter.MOVIES ->
                if (inputs.gridItemCount > 0 || inputs.movieCatalogTotal > 0) {
                    false
                } else {
                    inputs.catalogLoading || !inputs.catalogProgress.isMoviesPhaseComplete
                }
            VodContentFilter.SERIES ->
                if (inputs.gridItemCount > 0 || inputs.seriesCatalogTotal > 0) {
                    false
                } else {
                    inputs.catalogLoading ||
                        (inputs.catalogProgress.isMoviesPhaseComplete &&
                            !inputs.catalogProgress.isSeriesPhaseComplete)
                }
            else -> false
        }
    }

    fun focusContentMode(
        surfaceState: VodHubSurfaceState,
        gridItemCount: Int,
        catalogTotal: Int,
    ): VodHubFocusContentMode = when (surfaceState) {
        is VodHubSurfaceState.Loading -> VodHubFocusContentMode.Loading
        is VodHubSurfaceState.Empty -> VodHubFocusContentMode.Empty
        is VodHubSurfaceState.Error -> VodHubFocusContentMode.Error
        is VodHubSurfaceState.Ready -> when {
            gridItemCount > 0 -> VodHubFocusContentMode.Ready
            catalogTotal > 0 -> VodHubFocusContentMode.Loading
            surfaceState.wallRowCount > 0 -> VodHubFocusContentMode.Ready
            else -> VodHubFocusContentMode.Empty
        }
    }

    fun allowsGridFocus(mode: VodHubFocusContentMode): Boolean =
        mode == VodHubFocusContentMode.Ready

    fun shouldAwaitBrowseGrid(mode: VodHubFocusContentMode, catalogTotal: Int): Boolean =
        mode == VodHubFocusContentMode.Loading && catalogTotal > 0
}
