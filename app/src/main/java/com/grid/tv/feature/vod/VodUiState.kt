package com.grid.tv.feature.vod

import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.ui.component.VodCatalogOnboardingInputs
import com.grid.tv.ui.component.VodCatalogOnboardingTab

/** Sidebar genre chips and category filter mapping for the VOD hub. */
data class VodSidebarUiState(
    val movieCategories: List<VodCategory> = emptyList(),
    val seriesCategories: List<VodCategory> = emptyList(),
    val movieFilterIdsByRepresentativeId: Map<String, Set<String>> = emptyMap(),
    val seriesFilterIdsByRepresentativeId: Map<String, Set<String>> = emptyMap(),
    val genreLabels: List<String> = emptyList(),
    val selectedGenreIndex: Int = 0,
    val selectedMovieCategoryId: String? = null,
    val selectedMovieCategoryPlaylistId: Long? = null,
    val selectedSeriesCategoryId: String? = null,
    val selectedSeriesCategoryPlaylistId: Long? = null
)

/** Stable hero carousel data — index is volatile; see [VodHubViewModel.heroIndex]. */
data class VodHeroUiState(
    val featuredCarousel: List<VodItem> = emptyList()
)

data class VodCatalogUiState(
    val catalogSampleCount: Int = 0,
    val filteredCatalogCount: Int = 0,
    val catalogTotalCount: Int = 0,
    val seriesCatalogTotalCount: Int = 0,
    val combinedCatalogCount: Int = 0,
    val movieFilteredTotalCount: Int = 0,
    val seriesFilteredTotalCount: Int = 0,
    val catalogLoading: Boolean = false,
    val catalogProgress: VodCatalogProgress = VodCatalogProgress()
)

/**
 * Stable VOD hub content snapshot. Built in [VodHubViewModel.contentState].
 * Volatile session state (hero index, focus, scroll, paging) stays outside this model.
 */
data class VodUiState(
    val isLoading: Boolean = false,
    val showOnboarding: Boolean = false,
    val showCatalogEmptyState: Boolean = false,
    val showLanguageFilteredEmpty: Boolean = false,
    val languageFilterActive: Boolean = false,

    val catalog: List<VodItem> = emptyList(),
    val filteredCatalog: List<VodItem> = emptyList(),
    val wallRows: List<VodWallRow> = emptyList(),
    val wallRowsRevision: String = "",

    val preferredLanguages: Set<String> = emptySet(),
    val includeUntagged: Boolean = true,
    val availableLanguages: List<String> = emptyList(),

    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val recommendedForYou: List<VodItem> = emptyList(),
    val trendingNow: List<VodItem> = emptyList(),

    val contentFilter: VodContentFilter = VodContentFilter.ALL,
    val searchQuery: String = "",

    val sidebar: VodSidebarUiState = VodSidebarUiState(),
    val catalogUi: VodCatalogUiState = VodCatalogUiState(),
    val hero: VodHeroUiState = VodHeroUiState(),
    val onboardingInputs: VodCatalogOnboardingInputs = VodCatalogOnboardingInputs(
        catalogLoading = false,
        progress = VodCatalogProgress(),
        tab = VodCatalogOnboardingTab.ALL,
        browseRowCount = 0,
        categoryCount = 0
    ),

    val enrichmentMap: Map<String, TitleEnrichmentEntity> = emptyMap(),
    val vodProgress: Map<Pair<Long, Long>, Long> = emptyMap(),
    val movieBrowseRows: List<VodBrowseRow> = emptyList(),
    val seriesBrowseRows: List<VodBrowseRow> = emptyList(),
    val catalogPartitions: VodCatalogPartitions = VodCatalogPartitions.EMPTY
)

/** Raw inputs gathered from repository / internal flows before building [VodUiState]. */
data class VodHubUiBuildInputs(
    val catalogSample: List<VodItem>,
    val filteredCatalog: List<VodItem>,
    val continueWatching: List<ContinueWatchingItem>,
    val recommendedForYou: List<VodItem>,
    val trendingNow: List<VodItem>,
    val contentFilter: VodContentFilter,
    val searchQuery: String,
    val preferredLanguages: Set<String>,
    val includeUntagged: Boolean,
    val availableLanguages: List<String>,
    val featuredCarousel: List<VodItem>,
    val enrichmentMap: Map<String, TitleEnrichmentEntity>,
    val vodProgress: Map<Pair<Long, Long>, Long>,
    val movieBrowseRows: List<VodBrowseRow>,
    val seriesBrowseRows: List<VodBrowseRow>,
    val movieCategories: List<VodCategory>,
    val seriesCategories: List<VodCategory>,
    val selectedMovieCategoryId: String?,
    val selectedMovieCategoryPlaylistId: Long?,
    val selectedSeriesCategoryId: String?,
    val selectedSeriesCategoryPlaylistId: Long?,
    val catalogTotalCount: Int,
    val seriesCatalogTotalCount: Int,
    val movieFilteredTotalCount: Int,
    val seriesFilteredTotalCount: Int,
    val catalogLoading: Boolean,
    val catalogProgress: VodCatalogProgress,
    val catalogPartitions: VodCatalogPartitions,
)
