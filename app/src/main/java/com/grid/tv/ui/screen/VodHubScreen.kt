package com.grid.tv.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.feature.vod.personalization.matchContinueWatchingSeries
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.domain.model.categoryKey
import com.grid.tv.ui.component.EpgGuideHeader
import com.grid.tv.ui.component.GuideNavDrawer
import com.grid.tv.ui.component.GuideNavDrawerItem
import com.grid.tv.ui.component.GuideNavDrawerItems
import com.grid.tv.ui.component.GuideNavDrawerProfileFocusIndex
import com.grid.tv.ui.component.ProfileMenuDropdown
import com.grid.tv.ui.component.guideNavDrawerItemFocusIndex
import com.grid.tv.ui.component.MovieDetailOverlay
import com.grid.tv.ui.component.NetflixContentWallRow
import com.grid.tv.ui.component.resolveMovieOverview
import com.grid.tv.ui.component.VodGenreSidePanel
import com.grid.tv.ui.component.runtimeLabelForMovie
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.VodAmbientBackdrop
import com.grid.tv.ui.component.VodContentFilterTabBar
import com.grid.tv.domain.model.VodSidebarGenreNormalizer
import com.grid.tv.ui.component.VodPosterFocusLayout
import com.grid.tv.ui.component.VodHubLanguageFilterFocusIndex
import com.grid.tv.ui.component.VodHubTabFilters
import com.grid.tv.ui.component.vodHubTabFilterIndex
import com.grid.tv.ui.component.VodLanguagePreferenceDialog
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodCatalogOnboardingPanel
import com.grid.tv.ui.component.rememberVodCatalogOnboardingVisible
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.feature.vod.VodHubFoldMetrics
import com.grid.tv.feature.vod.VodHubHeroIsland
import com.grid.tv.feature.vod.VodHubHeroAmbientPoster
import com.grid.tv.feature.vod.handleVodHubHeroKeyEvent
import com.grid.tv.feature.vod.rememberVodHubFoldScroller
import com.grid.tv.feature.vod.resolveVodWallFocus
import com.grid.tv.feature.vod.vodWallItemKey
import com.grid.tv.ui.component.movieMetaSubtitle
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.di.PlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.grid.tv.ui.component.animateScrollToItemIfNeeded
import com.grid.tv.ui.component.animateScrollVodWallRowIntoView
import com.grid.tv.ui.component.scrollVodWallToTop
import com.grid.tv.ui.component.TvLazyFocusScrollDirection
import androidx.activity.compose.BackHandler
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import com.grid.tv.ui.component.SearchOverlay
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.ui.viewmodel.VodHubViewModel
import com.grid.tv.util.TvImeKeyDispatcher
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.quitAppToHome
import com.grid.tv.util.VodCatalogLogger
import com.grid.tv.util.VodPerfLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val vodImmediateWallRowIds = setOf("continue_watching", "trending", "recommended")

@Composable
fun VodHubScreen(
    initialTab: Int = 0,
    initialSeriesId: Long? = null,
    initialPlaylistId: Long? = null,
    profileInitials: String = "?",
    profileAvatarColor: String = com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR,
    profileDisplayName: String? = null,
    onPlayMovie: (String, String, Boolean) -> Unit,
    onPlayUrl: (String, String, Boolean) -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onWatchChannel: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    hubViewModel: VodHubViewModel = hiltViewModel(),
    moviesViewModel: MoviesViewModel = hiltViewModel(),
    seriesViewModel: SeriesViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    var showGlobalSearchOverlay by remember { mutableStateOf(false) }
    val focusUi = remember { VodHubFocusUiState() }
    val focusController = remember(focusUi) { VodHubFocusController(focusUi) }
    var contentRowIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentColIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedContentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var browseGridFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedMovie by remember { mutableStateOf<VodItem?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val continueWatchingListState = remember { LazyListState() }

    val recordingViewModel: RecordingViewModel = hiltViewModel()
    val context = LocalContext.current
    val livePlayerManager = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .livePlayerManager()
    }

    /** Stable catalog/filter/wall snapshot — does not include hero carousel index. */
    val contentState by hubViewModel.contentState.collectAsStateWithLifecycle()
    val activePlaylistId by hubViewModel.activePlaylistId.collectAsStateWithLifecycle()
    val recommendationFeedbackRevision by hubViewModel.recommendationFeedbackRevision.collectAsStateWithLifecycle()
    var focusBootstrapComplete by remember { mutableStateOf(false) }

    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val imeTypingActive = TvTextInputSession.isActiveState.value
    val globalSearchQuery by searchViewModel.queryText.collectAsStateWithLifecycle()
    val globalSearchResults by searchViewModel.results.collectAsStateWithLifecycle()
    val globalUnifiedSearchResults by searchViewModel.unifiedResults.collectAsStateWithLifecycle()
    val globalSearchBarState by searchViewModel.searchBarState.collectAsStateWithLifecycle()
    val movieFilteredTotalCount by moviesViewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val seriesFilteredTotalCount by seriesViewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val moviesBrowseGridHandle = remember { VodHubBrowseGridHandle() }
    val seriesBrowseGridHandle = remember { VodHubBrowseGridHandle() }
    var searchHasResults by remember { mutableStateOf(false) }

    LaunchedEffect(movieFilteredTotalCount, seriesFilteredTotalCount) {
        hubViewModel.syncBrowseCounts(movieFilteredTotalCount, seriesFilteredTotalCount)
    }

    SideEffect {
        VodPerfLogger.logEmission(
            "VodHubScreen.contentState",
            "wallRows=${contentState.wallRows.size} filter=${contentState.contentFilter} rev=${contentState.wallRowsRevision}"
        )
    }

    val featuredCarousel = contentState.hero.featuredCarousel
    val hasHero = featuredCarousel.isNotEmpty()
    val enrichmentMap = contentState.enrichmentMap
    val vodProgress = contentState.vodProgress
    val searchQuery = contentState.searchQuery
    val contentFilter = contentState.contentFilter
    val preferredVodLanguages = contentState.preferredLanguages
    val includeUntaggedVodContent = contentState.includeUntagged
    val availableVodLanguages = contentState.availableLanguages
    val languageFilterActive = contentState.languageFilterActive
    val movieBrowseRows = contentState.movieBrowseRows
    val seriesBrowseRows = contentState.seriesBrowseRows
    val catalogProgress = contentState.catalogUi.catalogProgress
    val catalogLoading = contentState.catalogUi.catalogLoading
    val catalogTotalCount = contentState.catalogUi.catalogTotalCount
    val seriesCatalogTotalCount = contentState.catalogUi.seriesCatalogTotalCount
    val combinedCatalogCount = contentState.catalogUi.combinedCatalogCount
    val catalogSampleCount = contentState.catalogUi.catalogSampleCount
    val filteredCatalogCount = contentState.catalogUi.filteredCatalogCount
    val recommendedForYou = contentState.recommendedForYou
    val trendingNow = contentState.trendingNow
    val wallRows = contentState.wallRows
    val wallRowsRevision = contentState.wallRowsRevision
    val immediateWallRows = remember(wallRows) {
        wallRows.filter { it.id in vodImmediateWallRowIds }
    }
    val deferredWallRows = remember(wallRows) {
        wallRows.filter { it.id !in vodImmediateWallRowIds }
            .sortedWith(
                compareBy({ VodSidebarGenreNormalizer.sidebarSortRank(it.title) }, { it.title.lowercase() })
            )
    }
    var loadedDeferredWallCount by rememberSaveable(wallRowsRevision) { mutableIntStateOf(0) }
    val displayWallRows = remember(contentFilter, wallRows, immediateWallRows, deferredWallRows, loadedDeferredWallCount) {
        if (contentFilter != VodContentFilter.ALL) {
            wallRows
        } else {
            immediateWallRows + deferredWallRows.take(loadedDeferredWallCount)
        }
    }
    val onboardingInputs = contentState.onboardingInputs
    val showCatalogEmptyState = contentState.showCatalogEmptyState
    val showLanguageFilteredEmpty = contentState.showLanguageFilteredEmpty
    val sidebarMovieCategories = contentState.sidebar.movieCategories
    val sidebarSeriesCategories = contentState.sidebar.seriesCategories
    val movieCategoryFilterIdsByRepresentativeId = contentState.sidebar.movieFilterIdsByRepresentativeId
    val seriesCategoryFilterIdsByRepresentativeId = contentState.sidebar.seriesFilterIdsByRepresentativeId
    val genreLabels = contentState.sidebar.genreLabels
    val selectedGenreIndex = contentState.sidebar.selectedGenreIndex
    val selectedMovieCategoryId = contentState.sidebar.selectedMovieCategoryId
    val selectedMovieCategoryPlaylistId = contentState.sidebar.selectedMovieCategoryPlaylistId
    val selectedSeriesCategoryId = contentState.sidebar.selectedSeriesCategoryId
    val selectedSeriesCategoryPlaylistId = contentState.sidebar.selectedSeriesCategoryPlaylistId
    val continueWatchingItems = contentState.continueWatching

    LaunchedEffect(
        catalogTotalCount,
        movieFilteredTotalCount,
        seriesFilteredTotalCount,
        movieBrowseRows.size,
        seriesBrowseRows.size,
        preferredVodLanguages,
        contentFilter
    ) {
        if (contentFilter == VodContentFilter.SEARCH) return@LaunchedEffect
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubMoviesPaging", movieFilteredTotalCount)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubSeriesPaging", seriesFilteredTotalCount)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubMovieBrowseRows", movieBrowseRows.size)
        com.grid.tv.util.VodCatalogLogger.uiItemsRendered("VodHubSeriesBrowseRows", seriesBrowseRows.size)
        if (catalogTotalCount > 0 &&
            movieFilteredTotalCount == 0 &&
            movieBrowseRows.isEmpty() &&
            contentFilter != VodContentFilter.SERIES
        ) {
            com.grid.tv.util.VodCatalogLogger.catalogStageFailure(
                stage = if (preferredVodLanguages.isNotEmpty()) "filter" else "ui",
                reason = if (preferredVodLanguages.isNotEmpty()) {
                    "language_filter=${preferredVodLanguages.joinToString(",")}"
                } else {
                    "paging_and_browse_empty"
                },
                dbMovies = catalogTotalCount,
                filtered = movieFilteredTotalCount
            )
        }
    }

    val hasBrowseResults = searchHasResults
    val showInlineSearch = contentFilter == VodContentFilter.SEARCH
    val selectedShowId by seriesViewModel.selectedShowId.collectAsStateWithLifecycle()
    val selectedShow by seriesViewModel.selectedShow.collectAsStateWithLifecycle()
    val seriesDetailOpen = selectedShowId != null
    val movieDetailOpen = selectedMovie != null
    val showGenrePanel = contentFilter == VodContentFilter.MOVIES ||
        contentFilter == VodContentFilter.SERIES
    val showBrowseGrid = searchQuery.isNotBlank() ||
        contentFilter == VodContentFilter.MOVIES ||
        contentFilter == VodContentFilter.SERIES
    val showHeroIsland = contentFilter == VodContentFilter.ALL && searchQuery.isBlank() && !showBrowseGrid
    val requestHeroPlayFocus = focusUi.focusZone == VodFocusZone.HERO &&
        hasHero &&
        searchQuery.isBlank() &&
        !showInlineSearch
    val scope = rememberCoroutineScope()
    val columnListState = rememberLazyListState()
    val moviesBrowseGridState = rememberLazyGridState()
    val seriesBrowseGridState = rememberLazyGridState()
    val density = LocalDensity.current
    val heroExpandedPx = with(density) { VodHubFoldMetrics.HeroExpandedHeight.toPx() }
    val vodFoldScroller = rememberVodHubFoldScroller(columnListState, heroExpandedPx)
    val vodWallScrollSafePaddingPx = remember(density) {
        with(density) {
            (
                VodPosterFocusLayout.categoryRowTopPadding +
                    24.dp +
                    VodPosterFocusLayout.categoryTitleBottomGap +
                    VodPosterFocusLayout.netflixEdgePaddingVertical
                ).roundToPx()
        }
    }
    val vodWallRowHeightPx = remember(density) {
        with(density) { VodPosterFocusLayout.estimatedWallRowHeight.roundToPx() }
    }

    LaunchedEffect(initialTab) {
        hubViewModel.setContentFilter(
            when (initialTab) {
                1 -> VodContentFilter.SERIES
                else -> VodContentFilter.ALL
            }
        )
    }

    LaunchedEffect(contentFilter, searchQuery) {
        moviesViewModel.setHubSearchMode(
            when (contentFilter) {
                VodContentFilter.MOVIES -> false
                VodContentFilter.SEARCH -> searchQuery.isBlank()
                else -> true
            }
        )
        seriesViewModel.setHubSearchMode(
            when (contentFilter) {
                VodContentFilter.SERIES -> false
                VodContentFilter.SEARCH -> searchQuery.isBlank()
                else -> true
            }
        )
        when (contentFilter) {
            VodContentFilter.SEARCH -> {
                moviesViewModel.setSearchQuery(searchQuery)
                seriesViewModel.setSearchQuery(searchQuery)
            }
            VodContentFilter.SERIES -> {
                seriesViewModel.setSearchQuery(searchQuery)
                moviesViewModel.setSearchQuery("")
            }
            VodContentFilter.MOVIES -> {
                moviesViewModel.setSearchQuery(searchQuery)
                seriesViewModel.setSearchQuery("")
            }
            VodContentFilter.ALL -> {
                moviesViewModel.setSearchQuery("")
                seriesViewModel.setSearchQuery("")
            }
        }
    }

    LaunchedEffect(initialSeriesId, initialPlaylistId, continueWatchingItems) {
        if (initialSeriesId == null) return@LaunchedEffect
        val playlistHint = initialPlaylistId ?: 0L
        val cw = matchContinueWatchingSeries(continueWatchingItems, initialSeriesId, playlistHint)
        seriesViewModel.selectShow(
            showId = initialSeriesId,
            playlistId = initialPlaylistId ?: cw?.playlistId ?: 0L,
            preferredSeason = cw?.seasonNumber
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000)
            if (featuredCarousel.size > 1 && focusUi.focusZone != VodFocusZone.HERO) {
                hubViewModel.advanceHeroCarousel()
            }
        }
    }

    LaunchedEffect(Unit) {
        com.grid.tv.util.PlaybackDiagnostics.logDeviceProfile(context)
        livePlayerManager.stopGuidePreview()
        livePlayerManager.setMode(com.grid.tv.player.LivePlayerManager.Mode.IDLE)
    }

    val showVodOnboarding = contentFilter != VodContentFilter.SEARCH &&
        rememberVodCatalogOnboardingVisible(onboardingInputs)
    val showVodOnboardingFull = showVodOnboarding && wallRows.isEmpty()
    val showVodOnboardingStrip = showVodOnboarding && wallRows.isNotEmpty() && !showVodOnboardingFull

    fun browseGridKeyAtIndex(index: Int): String? = when (contentFilter) {
        VodContentFilter.MOVIES -> moviesBrowseGridHandle.contentKeyAt(index)
        VodContentFilter.SERIES -> seriesBrowseGridHandle.contentKeyAt(index)
        else -> null
    }

    fun activeBrowseGridState(): androidx.compose.foundation.lazy.grid.LazyGridState? =
        when (contentFilter) {
            VodContentFilter.MOVIES -> moviesBrowseGridState
            VodContentFilter.SERIES -> seriesBrowseGridState
            else -> null
        }

    fun browseGridItemCount(): Int = when (contentFilter) {
        VodContentFilter.MOVIES -> moviesBrowseGridHandle.itemCount
        VodContentFilter.SERIES -> seriesBrowseGridHandle.itemCount
        VodContentFilter.ALL, VodContentFilter.SEARCH -> 0
    }

    fun applyGenreSelection(index: Int, filter: VodContentFilter = contentFilter) {
        focusUi.genreFocusIndex = index.coerceIn(0, genreLabels.lastIndex.coerceAtLeast(0))
        focusUi.rememberGenreFor(filter)
        when (filter) {
            VodContentFilter.MOVIES -> {
                if (index == 0) {
                    hubViewModel.setMovieCategory(null)
                    moviesViewModel.setCategory(null)
                } else {
                    val category = sidebarMovieCategories.getOrNull(index - 1)
                    val filterIds = category?.let {
                        movieCategoryFilterIdsByRepresentativeId[
                            categoryKey(it.playlistId, it.id)
                        ]
                    }
                    hubViewModel.setMovieCategory(category?.id, filterIds, category?.playlistId)
                    moviesViewModel.setCategory(category?.id, filterIds, category?.playlistId)
                }
            }
            VodContentFilter.SERIES -> {
                if (index == 0) {
                    hubViewModel.setSeriesCategory(null)
                    seriesViewModel.setCategory(null)
                } else {
                    val category = sidebarSeriesCategories.getOrNull(index - 1)
                    val filterIds = category?.let {
                        seriesCategoryFilterIdsByRepresentativeId[
                            categoryKey(it.playlistId, it.id)
                        ]
                    }
                    hubViewModel.setSeriesCategory(category?.id, filterIds, category?.playlistId)
                    seriesViewModel.setCategory(category?.id, filterIds, category?.playlistId)
                }
            }
            else -> Unit
        }
    }

    fun syncFocusedWallItemKey() {
        val key = displayWallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex)?.key
        focusedContentKey = key
        hubViewModel.rememberFocusedContentKey(key)
        if (contentFilter == VodContentFilter.ALL) {
            focusUi.rememberWallFor(
                VodContentFilter.ALL,
                VodWallFocusMemory(contentRowIndex, contentColIndex, key)
            )
        }
        if (contentFilter.storesBrowseGridMemory()) {
            val count = browseGridItemCount()
            activeBrowseGridState()?.let { state ->
                val index = if (count > 0) browseGridFocusIndex.coerceIn(0, count - 1) else 0
                val existing = focusUi.gridMemoryFor(contentFilter)
                focusUi.rememberGridFor(
                    contentFilter,
                    snapshotGridMemory(
                        state,
                        index,
                        if (count > 0) browseGridKeyAtIndex(index) else existing.contentKey
                    )
                )
            }
        }
    }

    fun restoreWallFocusAfterRebuild(focusBefore: String?) {
        val started = System.nanoTime()
        val (row, col) = resolveVodWallFocus(
            wallRows = displayWallRows,
            savedContentKey = focusBefore,
            fallbackRow = contentRowIndex,
            fallbackCol = contentColIndex
        )
        if (row == contentRowIndex && col == contentColIndex) {
            VodPerfLogger.logStage("focusRestore.noop", (System.nanoTime() - started) / 1_000_000)
            return
        }
        contentRowIndex = row
        contentColIndex = col
        val keyAfter = displayWallRows.getOrNull(row)?.items?.getOrNull(col)?.key
        focusedContentKey = keyAfter
        hubViewModel.rememberFocusedContentKey(keyAfter)
        focusUi.rememberWallFor(
            VodContentFilter.ALL,
            VodWallFocusMemory(row, col, keyAfter)
        )
        VodPerfLogger.logStage(
            "focusRestore",
            (System.nanoTime() - started) / 1_000_000,
            "before=$focusBefore after=$keyAfter row=$row col=$col"
        )
    }

    LaunchedEffect(wallRows.size) {
        val maxRow = displayWallRows.lastIndex.coerceAtLeast(0)
        if (displayWallRows.isEmpty()) return@LaunchedEffect
        contentRowIndex = contentRowIndex.coerceIn(0, maxRow)
        val maxCol = displayWallRows.getOrNull(contentRowIndex)?.items?.lastIndex ?: 0
        contentColIndex = contentColIndex.coerceIn(0, maxCol.coerceAtLeast(0))
    }

    LaunchedEffect(deferredWallRows.size, contentFilter) {
        if (contentFilter == VodContentFilter.ALL && deferredWallRows.isNotEmpty() && loadedDeferredWallCount == 0) {
            loadedDeferredWallCount = minOf(2, deferredWallRows.size)
        }
    }

    LaunchedEffect(contentRowIndex, focusUi.focusZone, contentFilter, deferredWallRows.size, immediateWallRows.size) {
        if (contentFilter != VodContentFilter.ALL || showBrowseGrid || focusUi.focusZone != VodFocusZone.CONTENT) return@LaunchedEffect
        val neededDeferred = (contentRowIndex - immediateWallRows.size + 2)
            .coerceIn(0, deferredWallRows.size)
        if (neededDeferred > loadedDeferredWallCount) {
            loadedDeferredWallCount = neededDeferred
        }
    }

    LaunchedEffect(wallRows.size, hasHero, searchQuery) {
        if (searchQuery.isBlank() && wallRows.isEmpty() && focusUi.focusZone == VodFocusZone.CONTENT) {
            focusUi.focusZone = if (hasHero) VodFocusZone.HERO else VodFocusZone.FILTER_PANEL
        }
    }

    LaunchedEffect(focusUi.focusZone, contentRowIndex, showHeroIsland, showBrowseGrid, searchQuery, displayWallRows.size, showInlineSearch) {
        if (searchQuery.isNotBlank() || showBrowseGrid || showInlineSearch) return@LaunchedEffect
        when (focusUi.focusZone) {
            VodFocusZone.HERO -> {
                columnListState.animateScrollVodWallRowIntoView(
                    index = 0,
                    direction = TvLazyFocusScrollDirection.UP,
                    safePaddingPx = vodWallScrollSafePaddingPx,
                    fallbackItemHeightPx = vodWallRowHeightPx
                )
            }
            VodFocusZone.CONTENT -> {
                val heroOffset = if (showHeroIsland) 1 else 0
                columnListState.animateScrollVodWallRowIntoView(
                    index = (contentRowIndex + heroOffset).coerceAtLeast(0),
                    direction = focusUi.contentScrollDirection,
                    safePaddingPx = vodWallScrollSafePaddingPx,
                    fallbackItemHeightPx = vodWallRowHeightPx
                )
            }
            VodFocusZone.FILTER_PANEL, VodFocusZone.NAV_DRAWER -> {
                columnListState.scrollVodWallToTop()
            }
            else -> Unit
        }
    }

    val navDrawerFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val heroPlayFocusRequester = remember { FocusRequester() }
    val heroMoreInfoFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val browseGridFocusRequester = remember { FocusRequester() }

    LaunchedEffect(wallRowsRevision, preferredVodLanguages, includeUntaggedVodContent) {
        val focusBefore = focusedContentKey
            ?: vodWallItemKey(wallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex))
        restoreWallFocusAfterRebuild(focusBefore)
        if (focusUi.focusZone == VodFocusZone.CONTENT && displayWallRows.isNotEmpty() && !showBrowseGrid && searchQuery.isBlank()) {
            contentFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    val filterPanelFocusRequester = remember { FocusRequester() }
    val genrePanelFocusRequester = remember { FocusRequester() }
    val inlineSearchFocusRequester = remember { FocusRequester() }
    val movieWatchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(contentFilter) {
        focusUi.filterFocusIndex = vodHubTabFilterIndex(contentFilter)
        focusUi.restoreGenreFrom(contentFilter)
    }

    fun saveCurrentFilterMemory() {
        when (contentFilter) {
            VodContentFilter.MOVIES, VodContentFilter.SERIES -> {
                focusUi.rememberGenreFor(contentFilter)
                val count = browseGridItemCount()
                activeBrowseGridState()?.let { state ->
                    val index = if (count > 0) browseGridFocusIndex.coerceIn(0, count - 1) else 0
                    val existing = focusUi.gridMemoryFor(contentFilter)
                    focusUi.rememberGridFor(
                        contentFilter,
                        snapshotGridMemory(
                            state,
                            index,
                            if (count > 0) browseGridKeyAtIndex(index) else existing.contentKey
                        )
                    )
                }
            }
            VodContentFilter.ALL -> {
                focusUi.rememberWallFor(
                    VodContentFilter.ALL,
                    VodWallFocusMemory(contentRowIndex, contentColIndex, focusedContentKey)
                )
            }
            else -> Unit
        }
    }

    fun restoreFilterMemory(filter: VodContentFilter) {
        when (filter) {
            VodContentFilter.MOVIES, VodContentFilter.SERIES -> {
                val genreIndex = focusUi.genreIndexFor(filter)
                focusUi.genreFocusIndex = genreIndex
                applyGenreSelection(genreIndex, filter)
                val memory = focusUi.gridMemoryFor(filter)
                val count = when (filter) {
                    VodContentFilter.MOVIES -> moviesBrowseGridHandle.itemCount
                    VodContentFilter.SERIES -> seriesBrowseGridHandle.itemCount
                    else -> 0
                }
                val keyResolver: (Int) -> String? = when (filter) {
                    VodContentFilter.MOVIES -> moviesBrowseGridHandle::contentKeyAt
                    VodContentFilter.SERIES -> seriesBrowseGridHandle::contentKeyAt
                    else -> { _ -> null }
                }
                val gridState = when (filter) {
                    VodContentFilter.MOVIES -> moviesBrowseGridState
                    VodContentFilter.SERIES -> seriesBrowseGridState
                    else -> null
                }
                val resolved = resolveBrowseGridFocusIndex(
                    itemCount = count,
                    saved = memory,
                    keyAtIndex = keyResolver,
                    firstVisibleIndex = gridState?.firstVisibleItemIndex ?: 0,
                )
                browseGridFocusIndex = resolved
                focusUi.rememberGridFor(
                    filter,
                    memory.copy(
                        itemIndex = resolved,
                        contentKey = keyResolver(resolved)
                    )
                )
            }
            VodContentFilter.ALL -> {
                val memory = focusUi.wallMemoryFor(VodContentFilter.ALL)
                val (row, col) = resolveVodWallFocus(
                    wallRows = displayWallRows,
                    savedContentKey = memory.contentKey,
                    fallbackRow = memory.rowIndex,
                    fallbackCol = memory.colIndex
                )
                contentRowIndex = row
                contentColIndex = col
                syncFocusedWallItemKey()
            }
            else -> Unit
        }
    }

    LaunchedEffect(activePlaylistId) {
        if (focusBootstrapComplete || activePlaylistId <= 0L) return@LaunchedEffect
        val snapshot = hubViewModel.readPersistedFocus(activePlaylistId)
        if (snapshot != null) {
            hydrateVodHubFocus(focusUi, snapshot)
            val filter = runCatching { VodContentFilter.valueOf(snapshot.contentFilter) }
                .getOrDefault(VodContentFilter.ALL)
            hubViewModel.setContentFilter(filter)
            focusUi.filterFocusIndex = snapshot.filterFocusIndex
            restoreFilterMemory(filter)
            val zone = runCatching { VodFocusZone.valueOf(snapshot.focusZone) }
                .getOrDefault(VodFocusZone.FILTER_PANEL)
            when {
                zone == VodFocusZone.CONTENT &&
                    (filter == VodContentFilter.MOVIES || filter == VodContentFilter.SERIES) -> {
                    focusUi.focusZone = VodFocusZone.CONTENT
                    focusController.focusBrowseGridRestored()
                }
                zone == VodFocusZone.GENRE_PANEL &&
                    (filter == VodContentFilter.MOVIES || filter == VodContentFilter.SERIES) -> {
                    focusUi.focusZone = VodFocusZone.GENRE_PANEL
                    focusUi.restoreGenreFrom(filter)
                    genrePanelFocusRequester.requestFocusSafelyAfterLayout()
                }
                else -> {
                    focusUi.focusZone = VodFocusZone.FILTER_PANEL
                    filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                }
            }
            VodHubFocusLogger.restore(activePlaylistId, filter)
        } else {
            focusUi.focusZone = VodFocusZone.FILTER_PANEL
            focusUi.filterFocusIndex = vodHubTabFilterIndex(contentFilter)
            filterPanelFocusRequester.requestFocusSafelyAfterLayout()
        }
        focusBootstrapComplete = true
    }

    LaunchedEffect(
        activePlaylistId,
        contentFilter,
        focusUi.filterFocusIndex,
        focusUi.genreFocusIndex,
        browseGridFocusIndex,
        contentRowIndex,
        contentColIndex,
        focusedContentKey,
        focusUi.focusZone,
        focusBootstrapComplete,
    ) {
        if (!focusBootstrapComplete || activePlaylistId <= 0L) return@LaunchedEffect
        delay(400)
        if (showBrowseGrid && browseGridItemCount() <= 0) {
            hubViewModel.writePersistedFocus(
                activePlaylistId,
                snapshotVodHubFocus(focusUi, contentFilter, focusUi.focusZone),
            )
            return@LaunchedEffect
        }
        saveCurrentFilterMemory()
        hubViewModel.writePersistedFocus(
            activePlaylistId,
            snapshotVodHubFocus(focusUi, contentFilter, focusUi.focusZone),
        )
        VodHubFocusLogger.persist(activePlaylistId, contentFilter)
    }

    LaunchedEffect(
        focusUi.focusZone,
        showInlineSearch,
        focusUi.vodSearchFocused,
        requestHeroPlayFocus,
        movieDetailOpen,
        seriesDetailOpen,
        showBrowseGrid,
        hasBrowseResults,
        searchQuery,
        imeTypingActive
    ) {
        if (imeTypingActive) return@LaunchedEffect
        when {
            movieDetailOpen -> movieWatchFocusRequester.requestFocusSafelyAfterLayout()
            seriesDetailOpen -> Unit
            showInlineSearch && focusUi.vodSearchFocused ->
                inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                showInlineSearch &&
                hasBrowseResults &&
                !focusUi.vodSearchFocused &&
                !focusUi.gridFocusPending ->
                browseGridFocusRequester.requestFocusSafelyAfterLayout()
            requestHeroPlayFocus -> Unit
            focusUi.focusZone == VodFocusZone.FILTER_PANEL ->
                filterPanelFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.GENRE_PANEL ->
                genrePanelFocusRequester.requestFocusSafelyAfterLayout()
            focusUi.focusZone == VodFocusZone.NAV_DRAWER ||
                (focusUi.focusZone == VodFocusZone.CONTENT &&
                    !showInlineSearch &&
                    (showBrowseGrid || wallRows.isEmpty()) &&
                    !focusUi.gridFocusPending) ->
                rootFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun resumeItem(item: ContinueWatchingItem) {
        VodPlaybackHelper.stageContinueWatching(item)
        val resumeMs = VodPlaybackHelper.resumePositionFor(item)
        onPlayUrl(item.title, item.streamUrl, resumeMs > 0L)
    }

    fun openMovieDetail(movie: VodItem) {
        hubViewModel.enrichOnBrowse(movie)
        selectedMovie = movie
    }

    fun closeMovieDetail() {
        selectedMovie = null
    }

    fun playMovie(movie: VodItem) {
        hubViewModel.enrichOnBrowse(movie)
        scope.launch {
            val resume = moviesViewModel.shouldResume(movie.toGridCardModel(), vodProgress)
            VodPlaybackHelper.stageMovie(movie)
            onPlayMovie(movie.title, movie.streamUrl, resume)
        }
    }

    fun watchSelectedMovie() {
        val movie = selectedMovie ?: return
        closeMovieDetail()
        playMovie(movie)
    }

    fun focusGenrePanelFromFilter() = focusController.focusGenrePanelFromFilter()

    fun focusFilterPanelFromGenre() = focusController.focusFilterPanelFromGenre()

    fun focusBrowseGridFromSidebar() = focusController.focusBrowseGridRestored()

    fun focusBrowseResultsFromTopBar() {
        focusUi.focusZone = VodFocusZone.CONTENT
        contentRowIndex = 0
        contentColIndex = 0
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusInlineSearchField() {
        focusUi.vodSearchFocused = true
        focusUi.focusZone = VodFocusZone.CONTENT
        scope.launch {
            inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun focusSearchResults() {
        if (!hasBrowseResults) return
        focusUi.vodSearchFocused = false
        focusUi.focusZone = VodFocusZone.CONTENT
        scope.launch {
            browseGridFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    fun openGlobalSearch() {
        showGlobalSearchOverlay = true
        focusUi.focusZone = VodFocusZone.FILTER_PANEL
    }

    fun openNavDrawer() {
        when (focusUi.focusZone) {
            VodFocusZone.FILTER_PANEL -> focusUi.rememberFilterFocus()
            VodFocusZone.GENRE_PANEL, VodFocusZone.CONTENT -> saveCurrentFilterMemory()
            else -> Unit
        }
        focusUi.rememberNavDrawerFocus()
        focusUi.navDrawerOpen = true
        focusUi.navDrawerFocusIndex = focusUi.lastNavDrawerFocusIndex
        focusUi.focusZone = VodFocusZone.NAV_DRAWER
        com.grid.tv.ui.screen.VodHubFocusLogger.sidebar("open")
    }

    fun closeNavDrawer(restoreFilter: Boolean = true) {
        if (restoreFilter) {
            focusController.closeNavDrawerToContentZone()
        } else {
            focusUi.navDrawerOpen = false
        }
    }

    fun selectVodDrawerItem(item: GuideNavDrawerItem) {
        when (item) {
            GuideNavDrawerItem.Search -> {
                closeNavDrawer(restoreFilter = false)
                openGlobalSearch()
            }
            GuideNavDrawerItem.LiveView -> {
                closeNavDrawer(restoreFilter = false)
                onNavigateHome()
            }
            GuideNavDrawerItem.Vod -> closeNavDrawer()
            GuideNavDrawerItem.Favorites -> {
                closeNavDrawer(restoreFilter = false)
                onOpenFavorites()
            }
            GuideNavDrawerItem.Recordings -> {
                closeNavDrawer(restoreFilter = false)
                onNavigateRecordings()
            }
        }
    }

    fun handleNavDrawerKey(event: KeyEvent): Boolean = focusController.handleNavDrawerKey(event)

    fun dismissGlobalSearch() {
        showGlobalSearchOverlay = false
        searchViewModel.clearQuery()
    }

    fun handleGlobalSearchResult(result: SearchResultItem) {
        when (result.type) {
            SearchResultType.ACTOR -> {
                val actor = result.actorName ?: return
                searchViewModel.recordSelection(actor)
                searchViewModel.applyTrendingOrRecent(actor)
                return
            }
            SearchResultType.GENRE -> {
                val genre = result.genreName ?: return
                searchViewModel.recordSelection(genre)
                searchViewModel.applyTrendingOrRecent(genre)
                return
            }
            else -> Unit
        }
        searchViewModel.recordSelection(globalSearchQuery)
        dismissGlobalSearch()
        when (result.type) {
            SearchResultType.CHANNEL, SearchResultType.PROGRAM ->
                result.channelId?.let(onWatchChannel)
            SearchResultType.VOD -> result.vodItem?.let { item ->
                VodPlaybackHelper.stageMovie(item)
                val resume = (vodProgress[item.playlistId to item.streamId] ?: 0L) > 5_000L
                onPlayMovie(item.title, item.streamUrl, resume)
            }
            SearchResultType.SERIES -> result.seriesShow?.let { show ->
                seriesViewModel.selectShow(show.id, show.playlistId, preferredSeason = null, preview = show)
            }
            SearchResultType.EPISODE -> {
                val show = result.seriesShow
                val episode = result.seriesEpisode
                if (show != null && episode != null) {
                    VodPlaybackHelper.stageSeriesEpisode(
                        show = show,
                        seasonNumber = result.seriesSeasonNumber ?: 1,
                        episodeNumber = episode.episodeNumber ?: 1,
                        streamId = episode.id,
                        episodeTitle = episode.title.ifBlank { show.name }
                    )
                    onPlayUrl(
                        episode.title.ifBlank { show.name },
                        episode.streamUrl,
                        true
                    )
                }
            }
            else -> Unit
        }
    }

    fun clearInlineSearch() {
        hubViewModel.setSearchQuery("")
        focusUi.vodSearchFocused = false
        if (contentFilter == VodContentFilter.SEARCH) {
            hubViewModel.setContentFilter(VodContentFilter.ALL)
            focusUi.filterFocusIndex = vodHubTabFilterIndex(VodContentFilter.ALL)
        }
        focusUi.focusZone = VodFocusZone.FILTER_PANEL
    }

    fun focusNavDrawerFromBrowseGrid() {
        if (showInlineSearch) {
            focusInlineSearchField()
        } else {
            openNavDrawer()
        }
    }

    fun openLanguagePreferenceDialog() {
        hubViewModel.refreshAvailableVodLanguages()
        showLanguageDialog = true
    }

    fun applyPreferredLanguages(languages: Set<String>) {
        VodPerfLogger.markInput("languageFilter.ui", "languages=${languages.joinToString(",")}")
        syncFocusedWallItemKey()
        hubViewModel.setPreferredVodLanguages(languages)
    }

    fun commitFilterHighlight(index: Int) {
        if (index == VodHubLanguageFilterFocusIndex) {
            openLanguagePreferenceDialog()
            return
        }
        val filter = VodHubTabFilters.getOrNull(index) ?: VodContentFilter.ALL
        focusUi.filterFocusIndex = index
        focusUi.rememberFilterFocus()
        if (filter == contentFilter) return

        saveCurrentFilterMemory()

        if (filter != VodContentFilter.SEARCH && contentFilter == VodContentFilter.SEARCH) {
            hubViewModel.setSearchQuery("")
            focusUi.vodSearchFocused = false
        }
        hubViewModel.setContentFilter(filter)
        onNavigateVod(
            when (filter) {
                VodContentFilter.SERIES -> 1
                else -> 0
            }
        )
        if (filter == VodContentFilter.SEARCH) {
            focusUi.vodSearchFocused = true
            focusUi.focusZone = VodFocusZone.CONTENT
            scope.launch {
                inlineSearchFocusRequester.requestFocusSafelyAfterLayout()
            }
            return
        }
        restoreFilterMemory(filter)
    }

    fun recoverFromEmptyCatalog(): Boolean {
        val action = resolveVodFocusEmptyRecovery(
            contentFilter = contentFilter,
            genreIndex = focusUi.genreFocusIndex,
            genreCount = genreLabels.size,
            moviesBrowseCount = moviesBrowseGridHandle.itemCount,
            seriesBrowseCount = seriesBrowseGridHandle.itemCount,
            wallRowCount = displayWallRows.size,
        )
        VodHubFocusLogger.emptyRecovery(action.toString())
        return when (action) {
            is VodFocusEmptyRecoveryAction.ApplyGenre -> {
                applyGenreSelection(action.index)
                focusUi.focusZone = VodFocusZone.GENRE_PANEL
                scope.launch { genrePanelFocusRequester.requestFocusSafelyAfterLayout() }
                true
            }
            is VodFocusEmptyRecoveryAction.KeepFilter -> false
            is VodFocusEmptyRecoveryAction.SwitchFilter -> {
                commitFilterHighlight(vodHubTabFilterIndex(action.filter))
                true
            }
            is VodFocusEmptyRecoveryAction.OpenSidebar -> {
                openNavDrawer()
                true
            }
        }
    }

    fun ensureValidFocus() {
        if (seriesDetailOpen || movieDetailOpen || showGlobalSearchOverlay) return
        if (showInlineSearch && focusUi.vodSearchFocused) return
        if (showBrowseGrid && browseGridItemCount() <= 0) {
            if (recoverFromEmptyCatalog()) return
        }
        when {
            focusUi.navDrawerOpen -> return
            focusUi.focusZone == VodFocusZone.CONTENT -> return
            focusUi.focusZone == VodFocusZone.FILTER_PANEL -> return
            focusUi.focusZone == VodFocusZone.GENRE_PANEL -> return
            focusUi.focusZone == VodFocusZone.HERO && hasHero -> return
            showBrowseGrid && browseGridItemCount() > 0 -> {
                focusUi.focusZone = VodFocusZone.CONTENT
                focusController.focusBrowseGridRestored()
            }
            !showBrowseGrid && displayWallRows.isNotEmpty() -> {
                focusUi.focusZone = VodFocusZone.CONTENT
                restoreFilterMemory(VodContentFilter.ALL)
            }
            showGenrePanel -> {
                focusUi.focusZone = VodFocusZone.GENRE_PANEL
                focusUi.restoreGenreFrom(contentFilter)
                scope.launch { genrePanelFocusRequester.requestFocusSafelyAfterLayout() }
            }
            else -> {
                focusUi.focusZone = VodFocusZone.FILTER_PANEL
                scope.launch { filterPanelFocusRequester.requestFocusSafelyAfterLayout() }
            }
        }
    }

    LaunchedEffect(browseGridItemCount(), contentFilter, wallRowsRevision) {
        if (focusUi.focusZone != VodFocusZone.CONTENT) return@LaunchedEffect
        when (contentFilter) {
            VodContentFilter.MOVIES, VodContentFilter.SERIES -> {
                val count = browseGridItemCount()
                if (count <= 0) {
                    ensureValidFocus()
                    return@LaunchedEffect
                }
                val memory = focusUi.gridMemoryFor(contentFilter)
                val keyResolver = when (contentFilter) {
                    VodContentFilter.MOVIES -> moviesBrowseGridHandle::contentKeyAt
                    VodContentFilter.SERIES -> seriesBrowseGridHandle::contentKeyAt
                    else -> { _: Int -> null }
                }
                val resolved = resolveBrowseGridFocusIndex(
                    itemCount = count,
                    saved = memory,
                    keyAtIndex = keyResolver,
                    firstVisibleIndex = activeBrowseGridState()?.firstVisibleItemIndex ?: 0,
                )
                browseGridFocusIndex = resolved.coerceIn(0, count - 1)
            }
            VodContentFilter.ALL -> {
                if (!showBrowseGrid) {
                    restoreWallFocusAfterRebuild(focusedContentKey)
                }
            }
            else -> Unit
        }
    }

    fun applyGenre(index: Int) {
        VodPerfLogger.markInput("genreSelect.ui", "index=$index filter=$contentFilter")
        applyGenreSelection(index)
        focusUi.focusZone = VodFocusZone.CONTENT
        focusController.focusBrowseGridRestored()
    }

    fun ratingFor(movie: VodItem): String? =
        hubViewModel.displayRating(movie, hubViewModel.enrichmentFor(movie, enrichmentMap))

    fun posterFor(movie: VodItem): String? {
        val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
        return enrichment?.posterUrl?.takeIf { it.isNotBlank() } ?: movie.posterUrl?.takeIf { it.isNotBlank() }
    }

    fun metaFor(movie: VodItem): String? = movieMetaSubtitle(movie, ratingFor(movie))

    fun overviewForMovie(movie: VodItem): String? =
        resolveMovieOverview(movie, hubViewModel.enrichmentFor(movie, enrichmentMap))

    fun overviewForSeries(show: SeriesShow): String? {
        val enrichment = if (show.playlistId > 0L) {
            val key = com.grid.tv.feature.enrichment.TitleEnrichmentRepository.xtreamSeriesKey(
                show.playlistId,
                show.id
            )
            enrichmentMap[key]
        } else {
            null
        }
        return enrichment?.overview?.takeIf { it.isNotBlank() }
            ?: show.plot?.takeIf { it.isNotBlank() }
            ?: show.genre?.takeIf { it.isNotBlank() }
    }

    fun openContinueWatchingDetail(item: ContinueWatchingItem) {
        when (item.contentType) {
            ContinueWatchingContentType.MOVIE -> {
                scope.launch {
                    val movie = item.streamId?.let { streamId ->
                        moviesViewModel.resolveMovie(item.playlistId, streamId)
                    } ?: VodItem(
                        id = 0L,
                        title = item.title,
                        streamId = item.streamId ?: 0L,
                        streamUrl = item.streamUrl,
                        posterUrl = item.posterUrl,
                        plot = null,
                        cast = null,
                        director = null,
                        genre = null,
                        rating = null,
                        duration = null,
                        playlistId = item.playlistId
                    )
                    openMovieDetail(movie)
                }
            }
            ContinueWatchingContentType.SERIES -> {
                item.seriesId?.let { seriesId ->
                    seriesViewModel.selectShow(
                        showId = seriesId,
                        playlistId = item.playlistId,
                        preferredSeason = item.seasonNumber,
                        preview = SeriesShow(
                            id = seriesId,
                            name = item.title,
                            coverUrl = item.posterUrl,
                            playlistId = item.playlistId
                        )
                    )
                }
            }
        }
    }

    fun activateWallItem(item: VodWallItem) {
        when (item) {
            is VodWallItem.ContinueItem -> openContinueWatchingDetail(item.item)
            is VodWallItem.MovieItem -> openMovieDetail(item.movie)
            is VodWallItem.SeriesItem -> seriesViewModel.selectShow(
                item.show.id,
                item.show.playlistId,
                preferredSeason = null,
                preview = item.show
            )
        }
    }

    SideEffect {
        focusController.bind(
            VodHubFocusDeps(
                scope = scope,
                contentFilter = contentFilter,
                searchQuery = searchQuery,
                showGenrePanel = showGenrePanel,
                showBrowseGrid = showBrowseGrid,
                showInlineSearch = showInlineSearch,
                hasHero = hasHero,
                hasBrowseResults = hasBrowseResults,
                genreLabels = genreLabels,
                wallRows = wallRows,
                displayWallRows = displayWallRows,
                loadedDeferredWallCount = loadedDeferredWallCount,
                deferredWallRowsSize = deferredWallRows.size,
                navDrawerOpen = focusUi.navDrawerOpen,
                filterPanelFocusRequester = filterPanelFocusRequester,
                genrePanelFocusRequester = genrePanelFocusRequester,
                browseGridFocusRequester = browseGridFocusRequester,
                heroPlayFocusRequester = heroPlayFocusRequester,
                inlineSearchFocusRequester = inlineSearchFocusRequester,
                navDrawerFocusRequester = navDrawerFocusRequester,
                browseGridFocusIndex = browseGridFocusIndex,
                setBrowseGridFocusIndex = { browseGridFocusIndex = it },
                contentRowIndex = contentRowIndex,
                setContentRowIndex = { contentRowIndex = it },
                contentColIndex = contentColIndex,
                setContentColIndex = { contentColIndex = it },
                browseGridItemCount = ::browseGridItemCount,
                browseGridKeyAtIndex = ::browseGridKeyAtIndex,
                activeBrowseGridState = ::activeBrowseGridState,
                syncFocusedWallItemKey = ::syncFocusedWallItemKey,
                commitFilterHighlight = ::commitFilterHighlight,
                applyGenre = ::applyGenre,
                activateWallItem = ::activateWallItem,
                openLanguagePreferenceDialog = ::openLanguagePreferenceDialog,
                openNavDrawer = ::openNavDrawer,
                closeNavDrawer = ::closeNavDrawer,
                selectVodDrawerItem = ::selectVodDrawerItem,
                focusInlineSearchField = ::focusInlineSearchField,
                focusSearchResults = ::focusSearchResults,
                moviesBrowseGridActivate = moviesBrowseGridHandle::activateFocusedIndex,
                seriesBrowseGridActivate = seriesBrowseGridHandle::activateFocusedIndex,
                ensureValidFocus = ::ensureValidFocus,
            )
        )
    }

    fun handleFilterPanelKey(event: KeyEvent): Boolean =
        focusController.handleFilterPanelKey(event)

    fun handleGenrePanelKey(event: KeyEvent): Boolean =
        focusController.handleGenrePanelKey(event)

    fun handleContentKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val paginateDeferred = event.key == Key.DirectionDown &&
            contentFilter == VodContentFilter.ALL &&
            !showBrowseGrid &&
            contentRowIndex >= displayWallRows.lastIndex &&
            loadedDeferredWallCount < deferredWallRows.size
        val handled = focusController.handleContentKey(event)
        if (handled && paginateDeferred) {
            loadedDeferredWallCount += 1
        }
        return handled
    }

    fun handleHeroKey(event: KeyEvent): Boolean =
        handleVodHubHeroKeyEvent(
            event = event,
            carouselSize = featuredCarousel.size,
            onStepCarousel = hubViewModel::stepHeroCarousel,
            onNavigateDown = {
                focusUi.focusZone = VodFocusZone.CONTENT
                contentRowIndex = 0
                contentColIndex = 0
                focusUi.contentScrollDirection = TvLazyFocusScrollDirection.DOWN
            },
            onNavigateUp = {
                focusController.focusFilterPanelFromGenre()
            }
        )

    fun consumeVodLocalBack(): Boolean = when {
        showGlobalSearchOverlay -> {
            dismissGlobalSearch()
            true
        }
        movieDetailOpen -> {
            closeMovieDetail()
            true
        }
        seriesDetailOpen -> {
            seriesViewModel.clearShowSelection()
            true
        }
        showInlineSearch -> {
            clearInlineSearch()
            true
        }
        focusUi.profileMenuOpen -> {
            focusUi.profileMenuOpen = false
            true
        }
        focusUi.focusZone == VodFocusZone.CONTENT || focusUi.focusZone == VodFocusZone.HERO ||
            focusUi.focusZone == VodFocusZone.FILTER_PANEL || focusUi.focusZone == VodFocusZone.GENRE_PANEL -> {
            openNavDrawer()
            true
        }
        focusUi.focusZone == VodFocusZone.NAV_DRAWER -> {
            closeNavDrawer()
            true
        }
        else -> false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeVodLocalBack
    )

    val showHeroBackdrop = focusUi.focusZone == VodFocusZone.HERO &&
        hasHero &&
        searchQuery.isBlank() &&
        !showBrowseGrid
    val contentAmbientPosterUrl = when {
        searchQuery.isNotBlank() || showBrowseGrid || showHeroBackdrop -> null
        focusUi.focusZone == VodFocusZone.CONTENT && wallRows.isNotEmpty() -> {
            when (val item = wallRows.getOrNull(contentRowIndex)?.items?.getOrNull(contentColIndex)) {
                is VodWallItem.ContinueItem -> item.item.posterUrl
                is VodWallItem.MovieItem -> posterFor(item.movie)
                is VodWallItem.SeriesItem -> item.show.coverUrl
                null -> null
            }
        }
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable(
                enabled = !seriesDetailOpen &&
                    !movieDetailOpen &&
                    !imeTypingActive &&
                    !(showInlineSearch && focusUi.vodSearchFocused) &&
                    !showGlobalSearchOverlay &&
                    focusUi.focusZone != VodFocusZone.HERO &&
                    focusUi.focusZone != VodFocusZone.FILTER_PANEL
            )
            .focusProperties {
                if (seriesDetailOpen || movieDetailOpen) {
                    canFocus = false
                }
            }
            .onPreviewKeyEvent { event ->
                if (showGlobalSearchOverlay) return@onPreviewKeyEvent false
                if (seriesDetailOpen || movieDetailOpen) {
                    return@onPreviewKeyEvent false
                }
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) {
                    return@onPreviewKeyEvent false
                }
                if (showInlineSearch && focusUi.vodSearchFocused && !imeTypingActive) {
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        return@onPreviewKeyEvent consumeVodLocalBack()
                    }
                    return@onPreviewKeyEvent false
                }
                if (focusUi.profileMenuOpen && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            focusUi.profileMenuOpen = false
                            return@onPreviewKeyEvent true
                        }
                        else -> Unit
                    }
                }
                if (focusUi.navDrawerOpen && focusUi.focusZone == VodFocusZone.NAV_DRAWER) {
                    if (handleNavDrawerKey(event)) return@onPreviewKeyEvent true
                }
                val handled = when (event.key) {
                    Key.Back, Key.Escape -> consumeVodLocalBack()
                    else -> when (focusUi.focusZone) {
                        VodFocusZone.NAV_DRAWER -> handleNavDrawerKey(event)
                        VodFocusZone.FILTER_PANEL -> handleFilterPanelKey(event)
                        VodFocusZone.GENRE_PANEL -> handleGenrePanelKey(event)
                        VodFocusZone.HERO -> handleHeroKey(event)
                        VodFocusZone.CONTENT -> handleContentKey(event)
                    }
                }
                if (handled) return@onPreviewKeyEvent true
                if (imeTypingActive) return@onPreviewKeyEvent false
                if (focusUi.focusZone in vodManualFocusZones && isVodDirectionalKey(event)) {
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        if (showHeroBackdrop) {
            VodHubHeroAmbientPoster(
                hubViewModel = hubViewModel,
                featuredCarousel = featuredCarousel,
                posterUrlFor = ::posterFor,
                enrichmentMap = enrichmentMap
            )
        } else {
            VodAmbientBackdrop(posterUrl = contentAmbientPosterUrl)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (seriesDetailOpen || movieDetailOpen) {
                        Modifier.focusProperties { canFocus = false }
                    } else {
                        Modifier
                    }
                )
        ) {
            GuideNavDrawer(
                focusedIndex = focusUi.navDrawerFocusIndex,
                drawerActive = focusUi.focusZone == VodFocusZone.NAV_DRAWER,
                drawerFocusRequester = navDrawerFocusRequester,
                profileInitials = profileInitials,
                profileAvatarColor = profileAvatarColor,
                profileFocused = focusUi.profileMenuOpen,
                onProfileClick = {
                    focusUi.profileMenuOpen = true
                    focusUi.profileMenuFocusIndex = 0
                },
                onItemFocused = { index ->
                    focusUi.navDrawerFocusIndex = index
                    focusUi.navDrawerOpen = true
                    focusUi.focusZone = VodFocusZone.NAV_DRAWER
                },
                onItemSelected = ::selectVodDrawerItem,
                onPreviewKey = ::handleNavDrawerKey,
                selectedItem = GuideNavDrawerItem.Vod
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
            EpgGuideHeader(
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                onRecordingIndicatorClick = onNavigateRecordings,
                modifier = Modifier.fillMaxWidth()
            )

            VodContentFilterTabBar(
                selectedFilter = contentFilter,
                focusedFilter = VodHubTabFilters.getOrNull(focusUi.filterFocusIndex) ?: contentFilter,
                barFocused = focusUi.focusZone == VodFocusZone.FILTER_PANEL,
                languageFilterActive = languageFilterActive,
                languageFilterFocused = focusUi.focusZone == VodFocusZone.FILTER_PANEL &&
                    focusUi.filterFocusIndex == VodHubLanguageFilterFocusIndex,
                onLanguageFilterClick = ::openLanguagePreferenceDialog,
                onFilterSelected = { filter ->
                    commitFilterHighlight(vodHubTabFilterIndex(filter))
                },
                tabBarFocusRequester = filterPanelFocusRequester,
                heroPlayFocusRequester = heroPlayFocusRequester,
                sidebarFocusRequester = navDrawerFocusRequester,
                onBarFocused = { focusUi.focusZone = VodFocusZone.FILTER_PANEL },
                onPreviewKey = ::handleFilterPanelKey
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (imeTypingActive) {
                            Modifier.focusProperties { canFocus = false }
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (showGenrePanel && !showInlineSearch && !showVodOnboardingFull) {
                    VodGenreSidePanel(
                        genres = genreLabels,
                        selectedIndex = selectedGenreIndex,
                        focusedIndex = focusUi.genreFocusIndex,
                        panelFocused = focusUi.focusZone == VodFocusZone.GENRE_PANEL,
                        contentGridFocusRequester = browseGridFocusRequester,
                        entryFocusRequester = genrePanelFocusRequester,
                        onFocusedIndexChange = { focusUi.genreFocusIndex = it },
                        onGenreSelected = ::applyGenre,
                        onPreviewKey = ::handleGenrePanelKey,
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    when {
                        showVodOnboardingFull -> {
                            VodCatalogOnboardingPanel(
                                progress = catalogProgress,
                                onboardingInputs = onboardingInputs,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        showInlineSearch -> {
                            VodHubSearchSection(
                                query = searchQuery,
                                moviesViewModel = moviesViewModel,
                                seriesViewModel = seriesViewModel,
                                progressByKey = vodProgress,
                                onQueryChange = hubViewModel::setSearchQuery,
                                onMovieClick = ::openMovieDetail,
                                onSeriesCardClick = { card ->
                                    seriesViewModel.selectShow(card.showId, card.playlistId)
                                },
                                searchFocusRequester = inlineSearchFocusRequester,
                                resultsFocusRequester = browseGridFocusRequester,
                                onFocusSearchField = { focusUi.vodSearchFocused = true },
                                onFocusResults = ::focusSearchResults,
                                onNavigateUpFromResults = ::focusInlineSearchField,
                                onHasResultsChange = { searchHasResults = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        contentFilter == VodContentFilter.MOVIES -> {
                            val moviesGridMemory = focusUi.gridMemoryFor(VodContentFilter.MOVIES)
                            val gridFocused = focusUi.focusZone == VodFocusZone.CONTENT && !showInlineSearch
                            VodHubMoviesBrowseSection(
                                moviesViewModel = moviesViewModel,
                                progressByKey = vodProgress,
                                onItemClick = ::openMovieDetail,
                                gridState = moviesBrowseGridState,
                                gridFocused = gridFocused,
                                focusedItemIndex = if (gridFocused && !focusUi.gridFocusPending) {
                                    browseGridFocusIndex
                                } else {
                                    -1
                                },
                                browseGridHandle = moviesBrowseGridHandle,
                                contentGridFocusRequester = browseGridFocusRequester,
                                onColumnCountChanged = { focusUi.browseGridColumnCount = it },
                                onNavigateUpFromFirstRow = ::focusFilterPanelFromGenre,
                                restoreScrollIndex = -1,
                                restoreScrollOffset = moviesGridMemory.scrollOffset,
                                gridRestoreRequest = focusUi.gridRestoreRequest,
                                onGridRestoreComplete = focusController::onGridRestoreComplete,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        contentFilter == VodContentFilter.SERIES -> {
                            val seriesGridMemory = focusUi.gridMemoryFor(VodContentFilter.SERIES)
                            val gridFocused = focusUi.focusZone == VodFocusZone.CONTENT && !showInlineSearch
                            VodHubSeriesBrowseSection(
                                seriesViewModel = seriesViewModel,
                                progressByKey = vodProgress,
                                onSeriesCardClick = { card ->
                                    seriesViewModel.selectShow(card.showId, card.playlistId)
                                },
                                gridState = seriesBrowseGridState,
                                gridFocused = gridFocused,
                                focusedItemIndex = if (gridFocused && !focusUi.gridFocusPending) {
                                    browseGridFocusIndex
                                } else {
                                    -1
                                },
                                browseGridHandle = seriesBrowseGridHandle,
                                contentGridFocusRequester = browseGridFocusRequester,
                                onColumnCountChanged = { focusUi.browseGridColumnCount = it },
                                onNavigateUpFromFirstRow = ::focusFilterPanelFromGenre,
                                restoreScrollIndex = -1,
                                restoreScrollOffset = seriesGridMemory.scrollOffset,
                                gridRestoreRequest = focusUi.gridRestoreRequest,
                                onGridRestoreComplete = focusController::onGridRestoreComplete,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        wallRows.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                when {
                                    continueWatchingItems.isNotEmpty() -> {
                                        NetflixContentWallRow(
                                            row = VodWallRow(
                                                id = "continue_watching",
                                                title = "Continue Watching",
                                                items = continueWatchingItems.map { VodWallItem.ContinueItem(it) }
                                            ),
                                            rowIndex = 0,
                                            focusedColumn = -1,
                                            rowFocused = false,
                                            listState = continueWatchingListState,
                                            progressByKey = vodProgress,
                                            posterUrlForMovie = ::posterFor,
                                            ratingForMovie = ::ratingFor,
                                            metaForMovie = ::metaFor,
                                            overviewForMovie = ::overviewForMovie,
                                            overviewForSeries = ::overviewForSeries,
                                            onActivateItem = ::activateWallItem
                                        )
                                    }
                                    showLanguageFilteredEmpty -> {
                                        VodEmptyState(
                                            title = "No titles match your language preferences.",
                                            message = "Try selecting additional languages, enable untagged content, or clear the language filter.",
                                            onRetry = ::openLanguagePreferenceDialog
                                        )
                                    }
                                    showCatalogEmptyState -> {
                                        VodEmptyState(
                                            title = "Nothing to watch yet",
                                            message = "Connect a playlist to add movies and series to your library.",
                                            onRetry = { moviesViewModel.refreshCatalog() }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val showHero = showHeroIsland
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (showVodOnboardingStrip) {
                                    VodCatalogOnboardingPanel(
                                        progress = catalogProgress,
                                        onboardingInputs = onboardingInputs,
                                        compact = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                LazyColumn(
                                state = columnListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 64.dp, top = 4.dp)
                            ) {
                                if (showHero) {
                                    item(key = "vod_hero") {
                                        VodHubHeroIsland(
                                            hubViewModel = hubViewModel,
                                            featuredCarousel = featuredCarousel,
                                            enrichmentMap = enrichmentMap,
                                            inputActive = focusUi.focusZone == VodFocusZone.HERO,
                                            requestPlayFocus = requestHeroPlayFocus,
                                            onPlay = { hero ->
                                                hubViewModel.onHeroInteraction(hero)
                                                playMovie(hero)
                                            },
                                            onMoreInfo = { hero ->
                                                hubViewModel.onHeroInteraction(hero)
                                                openMovieDetail(hero)
                                            },
                                            onNavigateDown = {
                                                vodFoldScroller.snapDownFromHero()
                                                focusUi.focusZone = VodFocusZone.CONTENT
                                                contentRowIndex = 0
                                                contentColIndex = 0
                                                focusUi.contentScrollDirection = TvLazyFocusScrollDirection.DOWN
                                            },
                                            onNavigateUp = {
                                                focusUi.focusZone = VodFocusZone.FILTER_PANEL
                                                scope.launch {
                                                    filterPanelFocusRequester.requestFocusSafelyAfterLayout()
                                                }
                                            },
                                            playFocusRequester = heroPlayFocusRequester,
                                            moreInfoFocusRequester = heroMoreInfoFocusRequester,
                                            topTabsFocusRequester = filterPanelFocusRequester,
                                            firstRowCardFocusRequester = contentFocusRequester,
                                            sidebarFocusRequester = navDrawerFocusRequester,
                                            onPlayFocused = { focusUi.focusZone = VodFocusZone.HERO },
                                            onMoreInfoFocused = { focusUi.focusZone = VodFocusZone.HERO }
                                        )
                                    }
                                }
                                itemsIndexed(displayWallRows, key = { _, row -> row.id }) { index, row ->
                                    val rowListState = remember(row.id) { LazyListState() }
                                    val rowFocused = focusUi.focusZone == VodFocusZone.CONTENT && contentRowIndex == index
                                    val focusedColumn = if (rowFocused) contentColIndex else -1
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = VodPosterFocusLayout.estimatedWallRowHeight)
                                            .graphicsLayer { clip = false }
                                    ) {
                                        NetflixContentWallRow(
                                            row = row,
                                            rowIndex = index,
                                            focusedColumn = focusedColumn,
                                            rowFocused = rowFocused,
                                            listState = rowListState,
                                            progressByKey = vodProgress,
                                            posterUrlForMovie = ::posterFor,
                                            ratingForMovie = ::ratingFor,
                                            metaForMovie = ::metaFor,
                                            overviewForMovie = ::overviewForMovie,
                                            overviewForSeries = ::overviewForSeries,
                                            onActivateItem = ::activateWallItem,
                                            firstItemFocusRequester = if (index == 0) contentFocusRequester else null,
                                            heroPlayFocusRequester = heroPlayFocusRequester,
                                            sidebarFocusRequester = navDrawerFocusRequester
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
            }
        }

        selectedMovie?.let { movie ->
            val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
            recommendationFeedbackRevision
            val movieVote = hubViewModel.recommendationVoteFor(movie)
            LaunchedEffect(movie.streamId) {
                hubViewModel.enrichOnBrowse(movie)
                hubViewModel.awaitEnrichment(movie)
                moviesViewModel.resolveMovie(movie.playlistId, movie.streamId)?.let { refreshed ->
                    if (selectedMovie?.streamId == refreshed.streamId) {
                        selectedMovie = refreshed
                    }
                }
            }
            MovieDetailOverlay(
                movie = selectedMovie ?: movie,
                enrichment = hubViewModel.enrichmentFor(selectedMovie ?: movie, enrichmentMap),
                overview = overviewForMovie(selectedMovie ?: movie),
                runtimeLabel = runtimeLabelForMovie(
                    selectedMovie ?: movie,
                    hubViewModel.enrichmentFor(selectedMovie ?: movie, enrichmentMap)
                ),
                onWatchNow = ::watchSelectedMovie,
                onBack = ::closeMovieDetail,
                watchFocusRequester = movieWatchFocusRequester,
                recommendationVote = movieVote,
                onRecommendationVote = { vote -> hubViewModel.voteRecommendation(movie, vote) }
            )
        }

        if (selectedShowId != null) {
            SeriesBrowserScreen(
                initialSeriesId = selectedShowId,
                initialPlaylistId = selectedShow?.playlistId ?: initialPlaylistId,
                onPlayUrl = onPlayUrl,
                embedded = true,
                hubSearchQuery = searchQuery,
                overlayDetail = true,
                viewModel = seriesViewModel,
                hubViewModel = hubViewModel
            )
        }

        if (showLanguageDialog) {
            VodLanguagePreferenceDialog(
                availableLanguages = availableVodLanguages,
                selectedLanguages = preferredVodLanguages,
                onApply = ::applyPreferredLanguages,
                onDismiss = { showLanguageDialog = false }
            )
        }

        if (showGlobalSearchOverlay) {
            BackHandler {
                dismissGlobalSearch()
            }
            SearchOverlay(
                query = globalSearchQuery,
                unifiedResults = globalUnifiedSearchResults,
                flatResults = globalSearchResults,
                searchBarState = globalSearchBarState,
                onQueryChange = searchViewModel::updateQuery,
                onClear = searchViewModel::clearQuery,
                onDismiss = ::dismissGlobalSearch,
                onMicClick = {
                    if (globalSearchBarState == SearchBarState.LISTENING) {
                        searchViewModel.stopVoiceSearch()
                    } else {
                        searchViewModel.beginVoiceSearch()
                    }
                },
                onResultSelected = ::handleGlobalSearchResult,
                onSuggestionSelected = searchViewModel::applyTrendingOrRecent,
                onClearHistory = searchViewModel::clearRecentHistory
            )
        }

        ProfileMenuDropdown(
            expanded = focusUi.profileMenuOpen,
            focusedIndex = focusUi.profileMenuFocusIndex,
            profileDisplayName = profileDisplayName,
            onDismiss = { focusUi.profileMenuOpen = false },
            onSwitchAccounts = {
                focusUi.profileMenuOpen = false
                onNavigateProfile()
            },
            onOpenSettings = {
                focusUi.profileMenuOpen = false
                onNavigateSettings()
            },
            onQuitApp = { context.quitAppToHome() },
            showSwitchAccounts = false,
            anchorFromSidebar = true
        )
    }
}
