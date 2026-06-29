package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodCatalogEmptyReason
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.ui.component.VodCatalogOnboardingInputs
import com.grid.tv.ui.component.VodCatalogOnboardingTab

/**
 * Authoritative readiness for a VOD hub surface (All wall, Movies grid, Series grid).
 *
 * State machine (manual catalog refresh is the only path back into [Loading] from [Ready]):
 * ```
 * Loading → Ready
 * Loading → Empty
 * Loading → Error
 * Ready   → Loading   (manual refresh only)
 * Ready   → Error     (unexpected ingest failure)
 * ```
 */
sealed interface VodHubSurfaceState {

    val onboardingInputs: VodCatalogOnboardingInputs?

    /** Onboarding / ingest / paging warm-up — primary content is not yet interactable. */
    data class Loading(
        override val onboardingInputs: VodCatalogOnboardingInputs,
        /** Progress bar under tabs (Movies/Series ingest banner). */
        val showCatalogProgressBar: Boolean = false,
        /** ALL tab: compact onboarding strip above partial wall content. */
        val compactOnboarding: Boolean = false,
    ) : VodHubSurfaceState

    /** Wall or browse grid is interactable. */
    data class Ready(
        val gridItemCount: Int = 0,
        val categoryCount: Int = 0,
        val browseRowCount: Int = 0,
        val wallRowCount: Int = 0,
        /** ALL tab only — onboarding strip shown above wall rows. */
        val showOnboardingStrip: Boolean = false,
        override val onboardingInputs: VodCatalogOnboardingInputs? = null,
    ) : VodHubSurfaceState

    data class Empty(
        val title: String,
        val message: String,
        val canRetry: Boolean,
        val reason: VodCatalogEmptyReason = VodCatalogEmptyReason.NONE,
        val variant: EmptyVariant = EmptyVariant.BROWSE_GRID,
        override val onboardingInputs: VodCatalogOnboardingInputs? = null,
    ) : VodHubSurfaceState {
        enum class EmptyVariant {
            BROWSE_GRID,
            ALL_CATALOG,
            ALL_LANGUAGE_FILTER,
            ALL_CONTINUE_WATCHING_ONLY,
        }
    }

    data class Error(
        val title: String,
        val message: String,
        val canRetry: Boolean,
        override val onboardingInputs: VodCatalogOnboardingInputs? = null,
    ) : VodHubSurfaceState
}

/** Focus-layer projection of [VodHubSurfaceState] — avoids inspecting ingest flags in the focus controller. */
enum class VodHubFocusContentMode {
    /** Do not restore grid focus; may await paging. */
    Loading,
    /** Grid / wall focus restore is allowed. */
    Ready,
    /** Route focus to empty-state / retry affordance. */
    Empty,
    /** Route focus to error / retry affordance. */
    Error,
}

data class VodHubBrowseSurfaceInputs(
    val tab: VodCatalogOnboardingTab,
    val catalogLoading: Boolean,
    val catalogProgress: com.grid.tv.domain.model.VodCatalogProgress,
    val catalogStatus: VodCatalogStatus,
    val catalogTotalCount: Int,
    val filteredTotalCount: Int,
    val browseRowCount: Int,
    val categoryCount: Int,
    val pagedItemCount: Int,
    val pagingRefreshing: Boolean,
    val selectedCategoryId: String?,
    val searchQuery: String = "",
    val languageFilterActive: Boolean = false,
    val isSeriesStillLoading: Boolean = false,
)

data class VodHubBrowseGridFocusInputs(
    val contentFilter: VodContentFilter,
    val surfaceState: VodHubSurfaceState,
    val gridItemCount: Int,
    val movieCatalogTotal: Int,
    val seriesCatalogTotal: Int,
    val catalogLoading: Boolean,
    val catalogProgress: com.grid.tv.domain.model.VodCatalogProgress,
)
