package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.ui.component.VodCatalogOnboardingInputs
import com.grid.tv.ui.component.VodCatalogOnboardingTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodHubSurfaceStateResolverTest {

    private fun onboardingInputs(
        tab: VodCatalogOnboardingTab = VodCatalogOnboardingTab.ALL,
        catalogTotalCount: Int = 0,
    ) = VodCatalogOnboardingInputs(
        catalogLoading = false,
        progress = VodCatalogProgress(),
        tab = tab,
        browseRowCount = 0,
        categoryCount = 0,
        catalogTotalCount = catalogTotalCount,
    )

    @Test
    fun allTab_loadingWhenOnboardingAndNoWallRows() {
        val state = VodHubSurfaceStateResolver.resolveAllTab(
            contentFilter = VodContentFilter.ALL,
            onboardingInputs = onboardingInputs(catalogTotalCount = 500),
            showOnboarding = true,
            wallRowCount = 0,
            catalogLoading = true,
            catalogProgress = VodCatalogProgress(isLoading = true),
            combinedCatalogCount = 500,
            hasContinueWatching = false,
            languageFilterActive = false,
            continueWatchingOnly = false,
        )
        assertTrue(state is VodHubSurfaceState.Loading)
    }

    @Test
    fun allTab_readyWithOnboardingStripWhenWallPresent() {
        val inputs = onboardingInputs()
        val state = VodHubSurfaceStateResolver.resolveAllTab(
            contentFilter = VodContentFilter.ALL,
            onboardingInputs = inputs,
            showOnboarding = true,
            wallRowCount = 3,
            catalogLoading = false,
            catalogProgress = VodCatalogProgress(moviesPhaseFinished = true, seriesPhaseFinished = true),
            combinedCatalogCount = 100,
            hasContinueWatching = false,
            languageFilterActive = false,
            continueWatchingOnly = false,
        )
        assertTrue(state is VodHubSurfaceState.Ready)
        val ready = state as VodHubSurfaceState.Ready
        assertTrue(ready.showOnboardingStrip)
        assertEquals(3, ready.wallRowCount)
    }

    @Test
    fun browseTab_loadingUntilCategoriesReady() {
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
                pagedItemCount = 8,
                pagingRefreshing = false,
                selectedCategoryId = null,
            )
        )
        assertTrue(state is VodHubSurfaceState.Loading)
    }

    @Test
    fun browseTab_readyWhenGridPopulated() {
        val state = VodHubSurfaceStateResolver.resolveBrowseTab(
            VodHubBrowseSurfaceInputs(
                tab = VodCatalogOnboardingTab.MOVIES,
                catalogLoading = false,
                catalogProgress = VodCatalogProgress(moviesPhaseFinished = true),
                catalogStatus = VodCatalogStatus(),
                catalogTotalCount = 120,
                filteredTotalCount = 120,
                browseRowCount = 0,
                categoryCount = 2,
                pagedItemCount = 20,
                pagingRefreshing = false,
                selectedCategoryId = null,
            )
        )
        assertTrue(state is VodHubSurfaceState.Ready)
        assertEquals(20, (state as VodHubSurfaceState.Ready).gridItemCount)
    }

    @Test
    fun focusContentMode_readyWhenGridHasItems() {
        val mode = VodHubSurfaceStateResolver.focusContentMode(
            surfaceState = VodHubSurfaceState.Ready(gridItemCount = 12),
            gridItemCount = 12,
            catalogTotal = 100,
        )
        assertEquals(VodHubFocusContentMode.Ready, mode)
    }

    @Test
    fun isBrowseGridLoading_falseWhenPagedItemsExist() {
        val loading = VodHubSurfaceStateResolver.isBrowseGridLoading(
            VodHubBrowseGridFocusInputs(
                contentFilter = VodContentFilter.SERIES,
                surfaceState = VodHubSurfaceState.Ready(gridItemCount = 4),
                gridItemCount = 4,
                movieCatalogTotal = 0,
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
