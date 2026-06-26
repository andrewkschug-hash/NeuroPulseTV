package com.grid.tv.ui.component

import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodWallRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCatalogOnboardingTest {

    private fun inputs(
        tab: VodCatalogOnboardingTab = VodCatalogOnboardingTab.ALL,
        catalogLoading: Boolean = false,
        progress: VodCatalogProgress = VodCatalogProgress(),
        browseRowCount: Int = 0,
        categoryCount: Int = 0,
        wallRowCount: Int = 0,
        nonPersonalWallRowCount: Int = 0,
        wallItemCount: Int = 0,
        pagedItemCount: Int = 0,
        catalogTotalCount: Int = 0
    ) = VodCatalogOnboardingInputs(
        catalogLoading = catalogLoading,
        progress = progress,
        tab = tab,
        browseRowCount = browseRowCount,
        categoryCount = categoryCount,
        wallRowCount = wallRowCount,
        nonPersonalWallRowCount = nonPersonalWallRowCount,
        wallItemCount = wallItemCount,
        pagedItemCount = pagedItemCount,
        catalogTotalCount = catalogTotalCount
    )

    @Test
    fun skipsOnboardingWhenCatalogAlreadyPopulated() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.ALL,
                    catalogLoading = true,
                    progress = VodCatalogProgress(isLoading = true),
                    catalogTotalCount = 500
                )
            )
        )
    }

    @Test
    fun allTab_ignoresSparsePagingItems() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.ALL,
                    pagedItemCount = 12,
                    wallRowCount = 1,
                    nonPersonalWallRowCount = 1,
                    progress = VodCatalogProgress(isLoading = true)
                )
            )
        )
    }

    @Test
    fun allTab_readyWhenWallHasInteractableCards() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.ALL,
                    wallRowCount = 1,
                    wallItemCount = 12
                )
            )
        )
    }

    @Test
    fun allTab_notReadyWhenRowsExistButEmpty() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.ALL,
                    wallRowCount = 2,
                    wallItemCount = 0,
                    catalogTotalCount = 500,
                    progress = VodCatalogProgress(
                        moviesPhaseFinished = true,
                        seriesPhaseFinished = true
                    )
                )
            )
        )
    }

    @Test
    fun allTab_pipelineCompleteFallback() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.ALL,
                    progress = VodCatalogProgress(
                        moviesPhaseFinished = true,
                        seriesPhaseFinished = true
                    )
                )
            )
        )
    }

    @Test
    fun allTab_staysOnboardingWhileBuildingRecommendations() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.ALL,
                    catalogTotalCount = 1200,
                    progress = VodCatalogProgress(
                        moviesPhaseFinished = true,
                        seriesPhaseFinished = true
                    )
                )
            )
        )
    }

    @Test
    fun allTab_staysOnboardingWhenUiCountLagsProgress() {
        assertTrue(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.ALL,
                    catalogTotalCount = 0,
                    progress = VodCatalogProgress(
                        moviesLoaded = 800,
                        seriesLoaded = 400,
                        moviesPhaseFinished = true,
                        seriesPhaseFinished = true
                    )
                )
            )
        )
    }

    @Test
    fun moviesTab_requiresMeaningfulGridOrBrowseRows() {
        assertTrue(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.MOVIES,
                    pagedItemCount = 4,
                    categoryCount = 3,
                    progress = VodCatalogProgress(isLoading = true)
                )
            )
        )
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.MOVIES,
                    pagedItemCount = 20,
                    categoryCount = 2
                )
            )
        )
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.MOVIES,
                    browseRowCount = 2
                )
            )
        )
    }

    @Test
    fun moviesTab_phaseFinishedWithEightItems() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.MOVIES,
                    pagedItemCount = 8,
                    progress = VodCatalogProgress(moviesPhaseFinished = true)
                )
            )
        )
    }

    @Test
    fun moviesTab_pipelineCompleteFallback() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.MOVIES,
                    progress = VodCatalogProgress(moviesPhaseFinished = true)
                )
            )
        )
    }

    @Test
    fun seriesTab_requiresMeaningfulGridOrBrowseRows() {
        assertFalse(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.SERIES,
                    pagedItemCount = 16,
                    categoryCount = 1
                )
            )
        )
    }

    @Test
    fun removedWeakCategoryOnlyReadiness() {
        assertTrue(
            shouldShowVodCatalogOnboarding(
                inputs(
                    tab = VodCatalogOnboardingTab.MOVIES,
                    categoryCount = 5,
                    progress = VodCatalogProgress(
                        moviesLoaded = 3,
                        isLoading = true
                    )
                )
            )
        )
    }

    @Test
    fun isPersonalHistoryWallRow_detectsContinueWatching() {
        assertTrue(
            isPersonalHistoryWallRow(
                VodWallRow(id = "continue_watching", title = "Continue Watching", items = emptyList())
            )
        )
        assertFalse(
            isPersonalHistoryWallRow(
                VodWallRow(id = "recommended", title = "Recommended For You", items = emptyList())
            )
        )
    }
}
