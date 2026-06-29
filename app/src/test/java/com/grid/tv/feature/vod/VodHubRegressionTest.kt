package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.ui.component.VodCatalogOnboardingTab
import com.grid.tv.ui.component.shouldShowVodCatalogOnboarding
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated regression guards for VOD hub readiness — mirrors manual QA scenarios.
 */
class VodHubRegressionTest {

    @Test
    fun allTab_neverBlankWhileCatalogExistsWithoutWall() {
        val onboarding = com.grid.tv.ui.component.VodCatalogOnboardingInputs(
            catalogLoading = true,
            progress = VodCatalogProgress(isLoading = true),
            tab = VodCatalogOnboardingTab.ALL,
            browseRowCount = 0,
            categoryCount = 0,
            catalogTotalCount = 500,
        )
        val showOnboarding = shouldShowVodCatalogOnboarding(onboarding)
        val state = VodHubSurfaceStateResolver.resolveAllTab(
            contentFilter = VodContentFilter.ALL,
            onboardingInputs = onboarding,
            showOnboarding = showOnboarding,
            wallRowCount = 0,
            catalogLoading = true,
            catalogProgress = VodCatalogProgress(isLoading = true),
            combinedCatalogCount = 500,
            hasContinueWatching = false,
            languageFilterActive = false,
            continueWatchingOnly = false,
        )
        assertTrue(
            "ALL with catalog but no wall must show Loading, not blank Ready",
            state is VodHubSurfaceState.Loading,
        )
    }

    @Test
    fun moviesTab_requiresGenresBeforeReady() {
        val state = VodHubSurfaceStateResolver.resolveBrowseTab(
            VodHubBrowseSurfaceInputs(
                tab = VodCatalogOnboardingTab.MOVIES,
                catalogLoading = false,
                catalogProgress = VodCatalogProgress(moviesPhaseFinished = true),
                catalogStatus = VodCatalogStatus(),
                catalogTotalCount = 0,
                filteredTotalCount = 0,
                browseRowCount = 0,
                categoryCount = 0,
                pagedItemCount = 12,
                pagingRefreshing = false,
                selectedCategoryId = null,
            )
        )
        assertTrue(state is VodHubSurfaceState.Loading)
    }

    @Test
    fun seriesTab_doesNotWaitForMoviePhaseInPipelineGate() {
        val onboarding = com.grid.tv.ui.component.VodCatalogOnboardingInputs(
            catalogLoading = false,
            progress = VodCatalogProgress(
                moviesPhaseFinished = false,
                seriesPhaseFinished = false,
                isLoading = true,
            ),
            tab = VodCatalogOnboardingTab.SERIES,
            browseRowCount = 0,
            categoryCount = 0,
            catalogTotalCount = 0,
        )
        assertTrue(shouldShowVodCatalogOnboarding(onboarding))
    }

    @Test
    fun seriesTab_readyWithPagedItemsWhileMoviesStillLoading() {
        val state = VodHubSurfaceStateResolver.resolveBrowseTab(
            VodHubBrowseSurfaceInputs(
                tab = VodCatalogOnboardingTab.SERIES,
                catalogLoading = true,
                catalogProgress = VodCatalogProgress(
                    moviesPhaseFinished = false,
                    seriesPhaseFinished = true,
                    isLoading = true,
                ),
                catalogStatus = VodCatalogStatus(),
                catalogTotalCount = 40,
                filteredTotalCount = 40,
                browseRowCount = 2,
                categoryCount = 1,
                pagedItemCount = 16,
                pagingRefreshing = false,
                selectedCategoryId = null,
            )
        )
        assertTrue(state is VodHubSurfaceState.Ready)
    }

    @Test
    fun emptyProvider_exitsLoadingToEmpty() {
        val state = VodHubSurfaceStateResolver.resolveBrowseTab(
            VodHubBrowseSurfaceInputs(
                tab = VodCatalogOnboardingTab.SERIES,
                catalogLoading = false,
                catalogProgress = VodCatalogProgress(seriesPhaseFinished = true),
                catalogStatus = VodCatalogStatus(),
                catalogTotalCount = 0,
                filteredTotalCount = 0,
                browseRowCount = 0,
                categoryCount = 0,
                pagedItemCount = 0,
                pagingRefreshing = false,
                selectedCategoryId = null,
            )
        )
        assertTrue(state is VodHubSurfaceState.Empty)
    }

    @Test
    fun focusRestoresAfterPagingWhenCatalogHasItems() {
        val mode = VodHubSurfaceStateResolver.focusContentMode(
            surfaceState = VodHubSurfaceState.Ready(gridItemCount = 0),
            gridItemCount = 0,
            catalogTotal = 120,
        )
        assertTrue(mode == VodHubFocusContentMode.Loading)
        val readyMode = VodHubSurfaceStateResolver.focusContentMode(
            surfaceState = VodHubSurfaceState.Ready(gridItemCount = 8),
            gridItemCount = 8,
            catalogTotal = 120,
        )
        assertTrue(readyMode == VodHubFocusContentMode.Ready)
    }

    @Test
    fun refreshingPhaseWhileReadyAndCatalogLoading() {
        val phase = VodHubSurfaceState.Ready(gridItemCount = 20).lifecyclePhase(catalogLoading = true)
        assertTrue(phase == VodHubSurfacePhase.Refreshing)
        val ready = VodHubSurfaceState.Ready(gridItemCount = 20).lifecyclePhase(catalogLoading = false)
        assertTrue(ready == VodHubSurfacePhase.Ready)
    }

    @Test
    fun browseGridLoading_falseWhenSeriesOnDiskDespiteMoviePhase() {
        val loading = VodHubSurfaceStateResolver.isBrowseGridLoading(
            VodHubBrowseGridFocusInputs(
                contentFilter = VodContentFilter.SERIES,
                surfaceState = VodHubSurfaceState.Ready(),
                gridItemCount = 0,
                movieCatalogTotal = 5000,
                seriesCatalogTotal = 80,
                catalogLoading = true,
                catalogProgress = VodCatalogProgress(
                    moviesPhaseFinished = true,
                    seriesPhaseFinished = false,
                    isLoading = true,
                ),
            )
        )
        assertFalse(loading)
    }
}
