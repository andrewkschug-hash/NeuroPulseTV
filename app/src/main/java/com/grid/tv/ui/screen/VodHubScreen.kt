package com.grid.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GridNavTabs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.grid.tv.ui.component.NetflixCategoryRow
import com.grid.tv.ui.component.NetflixMovieRow
import com.grid.tv.ui.component.NetflixSeriesRow
import com.grid.tv.ui.component.NetflixSeriesRow
import com.grid.tv.ui.component.NetflixPosterCard
import com.grid.tv.ui.component.NetflixUnderlineTabs
import com.grid.tv.ui.component.toGridCardModel
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.component.VodHeroSection
import com.grid.tv.ui.component.VodSearchOverlay
import com.grid.tv.ui.component.netflixMovieBrowseRows
import com.grid.tv.ui.component.netflixSeriesBrowseRows
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.MoviesViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SeriesViewModel
import com.grid.tv.ui.viewmodel.VodHubViewModel
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.quitAppToHome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VodFocusZone { TOP_BAR, TABS, CONTENT, SEARCH_OVERLAY }

@Composable
fun VodHubScreen(
    initialTab: Int = 0,
    initialSeriesId: Long? = null,
    profileInitials: String = "?",
    profileAvatarColor: String = com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR,
    onPlayMovie: (String, String, Boolean) -> Unit,
    onPlayUrl: (String, String, Boolean) -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onBack: () -> Unit = {},
    hubViewModel: VodHubViewModel = hiltViewModel(),
    moviesViewModel: MoviesViewModel = hiltViewModel(),
    seriesViewModel: SeriesViewModel = hiltViewModel()
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var focusZone by remember { mutableStateOf(VodFocusZone.CONTENT) }
    var topBarRow by remember { mutableIntStateOf(0) }
    var topBarFocusIndex by remember {
        mutableIntStateOf(GridNavTabs.indexOf(EpgNavTab.Vod).coerceAtLeast(0))
    }
    var tabFocusIndex by remember { mutableIntStateOf(tab) }
    var vodSearchFocused by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val recordingViewModel: RecordingViewModel = hiltViewModel()
    val homeViewModel: HomeEpgViewModel = hiltViewModel()
    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()
    val continueWatchingItems by hubViewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val heroMovie by hubViewModel.heroMovie.collectAsStateWithLifecycle()
    val heroEnrichment by hubViewModel.heroEnrichment.collectAsStateWithLifecycle()
    val featuredCarousel by hubViewModel.featuredCarousel.collectAsStateWithLifecycle()
    val heroIndex by hubViewModel.heroIndex.collectAsStateWithLifecycle()
    val enrichmentMap by hubViewModel.enrichmentMap.collectAsStateWithLifecycle()
    val vodProgress by hubViewModel.vodProgress.collectAsStateWithLifecycle()
    val searchQuery by hubViewModel.searchQuery.collectAsStateWithLifecycle()
    val movieBrowseRows by moviesViewModel.browseRows.collectAsStateWithLifecycle()
    val seriesBrowseRows by seriesViewModel.browseRows.collectAsStateWithLifecycle()
    val movieSearchResults by moviesViewModel.pagedCards.collectAsStateWithLifecycle()
    val seriesSearchResults by seriesViewModel.pagedCards.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery, tab) {
        moviesViewModel.setSearchQuery(if (tab == 0) searchQuery else "")
        seriesViewModel.setSearchQuery(if (tab == 1) searchQuery else "")
    }

    LaunchedEffect(initialSeriesId, continueWatchingItems) {
        if (initialSeriesId == null) return@LaunchedEffect
        val cw = continueWatchingItems.firstOrNull {
            it.contentType == ContinueWatchingContentType.SERIES && it.seriesId == initialSeriesId
        }
        seriesViewModel.selectShow(initialSeriesId, cw?.seasonNumber)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000)
            if (featuredCarousel.size > 1) {
                hubViewModel.advanceHeroCarousel()
            }
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.livePlayerManager.setMode(com.grid.tv.player.LivePlayerManager.Mode.IDLE)
    }

    val topNavFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val searchOverlayFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusZone, showSearchOverlay) {
        when {
            showSearchOverlay -> {
                focusZone = VodFocusZone.SEARCH_OVERLAY
                searchOverlayFocusRequester.requestFocusSafelyAfterLayout()
            }
            focusZone == VodFocusZone.TOP_BAR -> topNavFocusRequester.requestFocusSafelyAfterLayout()
            focusZone == VodFocusZone.TABS -> tabsFocusRequester.requestFocusSafelyAfterLayout()
            focusZone == VodFocusZone.CONTENT -> contentFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(tab) {
        tabFocusIndex = tab
    }

    fun resumeItem(item: ContinueWatchingItem) {
        VodPlaybackHelper.stageContinueWatching(item)
        onPlayUrl(item.title, item.streamUrl, true)
    }

    fun activateNavTab(tabItem: EpgNavTab) {
        when (tabItem) {
            EpgNavTab.Guide, EpgNavTab.Home, EpgNavTab.Search -> onNavigateHome()
            EpgNavTab.Vod, EpgNavTab.Movies -> onNavigateVod(0)
            EpgNavTab.Series -> onNavigateVod(1)
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> onOpenFavorites()
            EpgNavTab.Settings -> onNavigateSettings()
        }
    }

    fun applyTab(index: Int) {
        tab = index
        tabFocusIndex = index
        focusZone = VodFocusZone.CONTENT
        onNavigateVod(index)
    }

    fun openSearchOverlay() {
        showSearchOverlay = true
        vodSearchFocused = false
    }

    fun closeSearchOverlay() {
        showSearchOverlay = false
        focusZone = VodFocusZone.TOP_BAR
        topBarRow = 0
    }

    fun ratingFor(movie: VodItem): String? =
        hubViewModel.displayRating(movie, hubViewModel.enrichmentFor(movie, enrichmentMap))

    fun posterFor(movie: VodItem): String? {
        val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
        return enrichment?.posterUrl ?: movie.posterUrl
    }

    fun playMovie(movie: VodItem) {
        hubViewModel.enrichOnBrowse(movie)
        scope.launch {
            val resume = moviesViewModel.shouldResume(movie.toGridCardModel(), vodProgress)
            VodPlaybackHelper.stageMovie(movie)
            onPlayMovie(movie.title, movie.streamUrl, resume)
        }
    }

    fun handleTabsKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                tabFocusIndex = (tabFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                tabFocusIndex = (tabFocusIndex + 1).coerceAtMost(1)
                true
            }
            Key.DirectionUp -> {
                focusZone = VodFocusZone.TOP_BAR
                topBarRow = 1
                true
            }
            Key.DirectionDown -> {
                focusZone = VodFocusZone.CONTENT
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                applyTab(tabFocusIndex)
                true
            }
            else -> false
        }
    }

    fun handleTopBarKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                else -> false
            }
        }
        return when (event.key) {
            Key.DirectionLeft -> {
                when (topBarRow) {
                    1 -> tabFocusIndex = (tabFocusIndex - 1).coerceAtLeast(0)
                    else -> topBarFocusIndex = (topBarFocusIndex - 1).coerceAtLeast(0)
                }
                true
            }
            Key.DirectionRight -> {
                when (topBarRow) {
                    1 -> tabFocusIndex = (tabFocusIndex + 1).coerceAtMost(1)
                    else -> {
                        if (topBarFocusIndex == TopBarProfileIndex) {
                            vodSearchFocused = true
                            topBarFocusIndex = -1
                        } else if (vodSearchFocused) {
                            topBarFocusIndex = TopBarProfileIndex
                            vodSearchFocused = false
                        } else {
                            topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
                        }
                    }
                }
                true
            }
            Key.DirectionDown -> {
                when (topBarRow) {
                    0 -> {
                        topBarRow = 1
                        tabFocusIndex = tab
                        vodSearchFocused = false
                    }
                    else -> {
                        focusZone = VodFocusZone.CONTENT
                        topBarRow = 0
                        vodSearchFocused = false
                    }
                }
                true
            }
            Key.DirectionUp -> when (topBarRow) {
                1 -> {
                    topBarRow = 0
                    true
                }
                else -> false
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when {
                    vodSearchFocused -> {
                        openSearchOverlay()
                        true
                    }
                    topBarRow == 1 -> {
                        applyTab(tabFocusIndex)
                        true
                    }
                    topBarFocusIndex in GridNavTabs.indices -> {
                        activateNavTab(GridNavTabs[topBarFocusIndex])
                        true
                    }
                    topBarFocusIndex == TopBarProfileIndex -> {
                        profileMenuOpen = true
                        profileMenuFocusIndex = 0
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    fun consumeVodLocalBack(): Boolean = when {
        showSearchOverlay -> {
            closeSearchOverlay()
            true
        }
        profileMenuOpen -> {
            profileMenuOpen = false
            true
        }
        focusZone == VodFocusZone.CONTENT -> {
            focusZone = VodFocusZone.TABS
            true
        }
        focusZone == VodFocusZone.TABS -> {
            focusZone = VodFocusZone.TOP_BAR
            topBarRow = 1
            true
        }
        focusZone == VodFocusZone.TOP_BAR && topBarRow == 1 -> {
            topBarRow = 0
            true
        }
        else -> false
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeVodLocalBack
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VodNetflixColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> consumeVodLocalBack()
                    else -> false
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
                selectedTab = EpgNavTab.Vod,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == VodFocusZone.TOP_BAR &&
                    topBarRow == 0 &&
                    topBarFocusIndex in GridNavTabs.indices,
                profileFocused = focusZone == VodFocusZone.TOP_BAR &&
                    topBarRow == 0 &&
                    topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
                profileAvatarColor = profileAvatarColor,
                profileMenuExpanded = profileMenuOpen,
                profileMenuFocusIndex = profileMenuFocusIndex,
                onProfileClick = {
                    focusZone = VodFocusZone.TOP_BAR
                    topBarRow = 0
                    topBarFocusIndex = TopBarProfileIndex
                    profileMenuOpen = true
                    profileMenuFocusIndex = 0
                },
                onSwitchAccounts = {
                    profileMenuOpen = false
                    onNavigateProfile()
                },
                onOpenSettings = {
                    profileMenuOpen = false
                    onNavigateSettings()
                },
                onQuitApp = { context.quitAppToHome() },
                onProfileMenuDismiss = { profileMenuOpen = false },
                onTabSelected = { tabItem ->
                    focusZone = VodFocusZone.TOP_BAR
                    topBarRow = 0
                    topBarFocusIndex = GridNavTabs.indexOf(tabItem).coerceAtLeast(0)
                    activateNavTab(tabItem)
                },
                isRecording = isRecording,
                activeRecordingTitle = activeRecordingTitle,
                miniPlayer = {},
                vodSearchFocused = vodSearchFocused,
                onVodSearchClick = { openSearchOverlay() },
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == VodFocusZone.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            NetflixUnderlineTabs(
                labels = listOf("Movies", "Series"),
                activeIndex = tab,
                focusedIndex = tabFocusIndex,
                barFocused = focusZone == VodFocusZone.TABS,
                onTabSelected = { applyTab(it) },
                modifier = Modifier
                    .focusRequester(tabsFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        focusZone == VodFocusZone.TABS && handleTabsKey(it)
                    }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (tab == 0 && searchQuery.isBlank()) {
                    heroMovie?.let { hero ->
                        item(key = "hero") {
                            VodHeroSection(
                                movie = hero,
                                enrichment = heroEnrichment,
                                carouselSize = featuredCarousel.size,
                                carouselIndex = heroIndex,
                                onPlay = { playMovie(hero) },
                                onMoreInfo = { playMovie(hero) }
                            )
                        }
                    }
                }

                if (continueWatchingItems.isNotEmpty() && searchQuery.isBlank()) {
                    item(key = "continue_watching") {
                        NetflixCategoryRow(title = "Continue Watching") {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(continueWatchingItems, key = { it.contentKey }) { item ->
                                    NetflixPosterCard(
                                        title = item.title,
                                        posterUrl = item.posterUrl,
                                        progressFraction = item.progressFraction
                                            .takeIf { it in 0.01f..0.98f },
                                        onClick = { resumeItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (searchQuery.isNotBlank()) {
                    item(key = "search_results") {
                        if (tab == 0) {
                            val movies = movieSearchResults.mapNotNull { card ->
                                moviesViewModel.findMovie(card.playlistId, card.streamId)
                            }
                            NetflixCategoryRow(title = "Search Results") {
                                NetflixMovieRow(
                                    movies = movies,
                                    progressByStreamId = vodProgress,
                                    posterUrlFor = ::posterFor,
                                    onPlayMovie = ::playMovie,
                                    firstItemFocusRequester = contentFocusRequester
                                )
                            }
                        } else {
                            val shows = seriesSearchResults.mapNotNull { card ->
                                seriesViewModel.findShow(card.showId)
                            }
                            NetflixCategoryRow(title = "Search Results") {
                                NetflixSeriesRow(
                                    shows = shows,
                                    onOpenSeries = { show ->
                                        seriesViewModel.selectShow(show.id, null)
                                    },
                                    firstItemFocusRequester = contentFocusRequester
                                )
                            }
                        }
                    }
                } else if (tab == 0) {
                    netflixMovieBrowseRows(
                        rows = movieBrowseRows,
                        progressByStreamId = vodProgress,
                        posterUrlFor = ::posterFor,
                        onPlayMovie = ::playMovie,
                        firstRowFocusRequester = contentFocusRequester
                    )
                } else {
                    netflixSeriesBrowseRows(
                        rows = seriesBrowseRows,
                        onOpenSeries = { show ->
                            seriesViewModel.selectShow(show.id, null)
                        },
                        firstRowFocusRequester = contentFocusRequester
                    )
                }
            }
        }

        if (showSearchOverlay) {
            VodSearchOverlay(
                query = searchQuery,
                placeholder = if (tab == 0) "Search movies…" else "Search series…",
                onQueryChange = hubViewModel::setSearchQuery,
                onDismiss = ::closeSearchOverlay,
                focusRequester = searchOverlayFocusRequester,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        closeSearchOverlay()
                        true
                    } else {
                        false
                    }
                }
            )
        }

        if (tab == 1) {
            SeriesBrowserScreen(
                initialSeriesId = initialSeriesId,
                onPlayUrl = onPlayUrl,
                embedded = true,
                hubSearchQuery = searchQuery,
                overlayDetail = true,
                viewModel = seriesViewModel,
                hubViewModel = hubViewModel
            )
        }
    }
}
