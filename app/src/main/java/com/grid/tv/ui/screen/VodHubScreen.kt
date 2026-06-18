package com.grid.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.grid.tv.ui.component.isTextFieldActivateKey
import com.grid.tv.ui.component.showTextFieldKeyboard
import com.grid.tv.ui.component.TvTextInputActivationEffect
import com.grid.tv.util.TvImeKeyDispatcher
import com.grid.tv.util.TvRemoteKeyboard
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.lockFocusWhileTyping
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.ui.component.ContinueWatchingRow
import com.grid.tv.ui.component.EpgChipFilterBar
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.PersonalizedVodRow
import com.grid.tv.ui.component.VodHeroSection
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.util.quitAppToHome
import com.grid.tv.ui.viewmodel.VodHubViewModel
import kotlinx.coroutines.delay

private enum class VodFocusZone { TOP_BAR, CONTINUE, SEARCH, TABS, CONTENT }

private val TopBarProfileIndex get() = GridNavTabs.size

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
    hubViewModel: VodHubViewModel = hiltViewModel()
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var focusZone by remember { mutableStateOf(VodFocusZone.CONTENT) }
    var topBarRow by remember { mutableIntStateOf(0) }
    var topBarFocusIndex by remember {
        mutableIntStateOf(GridNavTabs.indexOf(EpgNavTab.Vod).coerceAtLeast(0))
    }
    var tabFocusIndex by remember { mutableIntStateOf(tab) }
    var continueFocusIndex by remember { mutableIntStateOf(0) }
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
    val recommendedForYou by hubViewModel.recommendedForYou.collectAsStateWithLifecycle()
    val trendingNow by hubViewModel.trendingNow.collectAsStateWithLifecycle()
    val heroMovie by hubViewModel.heroMovie.collectAsStateWithLifecycle()
    val heroEnrichment by hubViewModel.heroEnrichment.collectAsStateWithLifecycle()
    val featuredCarousel by hubViewModel.featuredCarousel.collectAsStateWithLifecycle()
    val heroIndex by hubViewModel.heroIndex.collectAsStateWithLifecycle()
    val enrichmentMap by hubViewModel.enrichmentMap.collectAsStateWithLifecycle()
    val vodProgress by hubViewModel.vodProgress.collectAsStateWithLifecycle()
    val searchQuery by hubViewModel.searchQuery.collectAsStateWithLifecycle()

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
    val continueFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusZone, continueWatchingItems.isNotEmpty()) {
        when (focusZone) {
            VodFocusZone.TOP_BAR -> topNavFocusRequester.requestFocusSafelyAfterLayout()
            VodFocusZone.CONTINUE -> if (continueWatchingItems.isNotEmpty()) {
                continueFocusRequester.requestFocusSafelyAfterLayout()
            }
            VodFocusZone.SEARCH -> searchFocusRequester.requestFocusSafelyAfterLayout()
            VodFocusZone.TABS -> tabsFocusRequester.requestFocusSafelyAfterLayout()
            VodFocusZone.CONTENT -> contentFocusRequester.requestFocusSafelyAfterLayout()
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
    }

    fun moveFocusToTabs() {
        focusZone = VodFocusZone.TABS
        tabFocusIndex = tab
    }

    fun moveFocusToSearch() {
        focusZone = VodFocusZone.SEARCH
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
                moveFocusToSearch()
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

    fun moveFocusToContinue() {
        if (continueWatchingItems.isNotEmpty()) {
            focusZone = VodFocusZone.CONTINUE
        } else {
            focusZone = VodFocusZone.TOP_BAR
            topBarRow = 0
        }
    }

    fun handleContinueKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || continueWatchingItems.isEmpty()) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                continueFocusIndex = (continueFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                continueFocusIndex = (continueFocusIndex + 1).coerceAtMost(continueWatchingItems.lastIndex)
                true
            }
            Key.DirectionUp -> {
                focusZone = VodFocusZone.TOP_BAR
                topBarRow = 0
                true
            }
            Key.DirectionDown -> {
                moveFocusToSearch()
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                continueWatchingItems.getOrNull(continueFocusIndex)?.let(::resumeItem)
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
                    else -> topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
                }
                true
            }
            Key.DirectionDown -> {
                when (topBarRow) {
                    0 -> {
                        topBarRow = 1
                        tabFocusIndex = tab
                    }
                    else -> {
                        focusZone = if (continueWatchingItems.isNotEmpty()) {
                            VodFocusZone.CONTINUE
                        } else {
                            VodFocusZone.SEARCH
                        }
                        topBarRow = 0
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
                when (topBarRow) {
                    1 -> applyTab(tabFocusIndex)
                    else -> when (topBarFocusIndex) {
                        in GridNavTabs.indices -> activateNavTab(GridNavTabs[topBarFocusIndex])
                        TopBarProfileIndex -> {
                            profileMenuOpen = true
                            profileMenuFocusIndex = 0
                        }
                    }
                }
                true
            }
            else -> false
        }
    }

    fun consumeVodLocalBack(): Boolean = when {
        profileMenuOpen -> {
            profileMenuOpen = false
            true
        }
        focusZone == VodFocusZone.CONTENT -> {
            moveFocusToTabs()
            true
        }
        focusZone == VodFocusZone.TABS -> {
            moveFocusToSearch()
            true
        }
        focusZone == VodFocusZone.SEARCH -> {
            moveFocusToContinue()
            true
        }
        focusZone == VodFocusZone.CONTINUE -> {
            focusZone = VodFocusZone.TOP_BAR
            true
        }
        else -> false
    }

    fun ratingFor(movie: com.grid.tv.domain.model.VodItem): String? =
        hubViewModel.displayRating(movie, hubViewModel.enrichmentFor(movie, enrichmentMap))

    fun posterFor(movie: com.grid.tv.domain.model.VodItem): String? {
        val enrichment = hubViewModel.enrichmentFor(movie, enrichmentMap)
        return enrichment?.posterUrl ?: movie.posterUrl
    }

    fun playMovie(movie: com.grid.tv.domain.model.VodItem) {
        hubViewModel.enrichOnBrowse(movie)
        onPlayMovie(movie.title, movie.streamUrl, false)
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::consumeVodLocalBack
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
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
                    topBarFocusIndex <= GridNavTabs.lastIndex,
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
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == VodFocusZone.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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

                if (continueWatchingItems.isNotEmpty()) {
                    item(key = "continue_watching") {
                        ContinueWatchingRow(
                            items = continueWatchingItems,
                            focusedIndex = continueFocusIndex,
                            rowFocused = focusZone == VodFocusZone.CONTINUE,
                            onSelect = ::resumeItem,
                            modifier = Modifier
                                .focusRequester(continueFocusRequester)
                                .focusable()
                                .onPreviewKeyEvent {
                                    focusZone == VodFocusZone.CONTINUE && handleContinueKey(it)
                                }
                        )
                    }
                }

                if (recommendedForYou.isNotEmpty()) {
                    item(key = "recommended") {
                        PersonalizedVodRow(
                            title = "Recommended For You",
                            movies = recommendedForYou.take(16),
                            progressByStreamId = vodProgress,
                            onPlayMovie = ::playMovie,
                            onSeeAll = {
                                applyTab(0)
                                focusZone = VodFocusZone.CONTENT
                            },
                            ratingForMovie = ::ratingFor,
                            posterUrlForMovie = ::posterFor
                        )
                    }
                }

                if (trendingNow.isNotEmpty()) {
                    item(key = "trending") {
                        PersonalizedVodRow(
                            title = "Trending Now",
                            movies = trendingNow.take(16),
                            progressByStreamId = vodProgress,
                            onPlayMovie = ::playMovie,
                            onSeeAll = {
                                applyTab(0)
                                focusZone = VodFocusZone.CONTENT
                            },
                            ratingForMovie = ::ratingFor,
                            posterUrlForMovie = ::posterFor
                        )
                    }
                }

                item(key = "search_tabs") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VodHubSearchField(
                            value = searchQuery,
                            onValueChange = hubViewModel::setSearchQuery,
                            placeholder = if (tab == 0) "Search movies…" else "Search series…",
                            focusRequester = searchFocusRequester,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    focusZone = VodFocusZone.SEARCH
                                    when (event.key) {
                                        Key.DirectionUp -> {
                                            moveFocusToContinue()
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            moveFocusToTabs()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        )

                        EpgChipFilterBar(
                            labels = listOf("Movies", "Series"),
                            activeIndex = tab,
                            focusedIndex = tabFocusIndex,
                            barFocused = focusZone == VodFocusZone.TABS,
                            modifier = Modifier
                                .focusRequester(tabsFocusRequester)
                                .focusable()
                                .onPreviewKeyEvent {
                                    focusZone == VodFocusZone.TABS && handleTabsKey(it)
                                }
                        )
                    }
                }

                item(key = "browse") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp)
                            .padding(horizontal = 24.dp)
                    ) {
                        when (tab) {
                            0 -> MoviesBrowserScreen(
                                onPlayMovie = onPlayMovie,
                                embedded = true,
                                hubSearchQuery = searchQuery,
                                contentFocusRequester = contentFocusRequester,
                                onMoveFocusUp = { moveFocusToTabs() },
                                onMovieBrowse = { hubViewModel.enrichOnBrowse(it) }
                            )
                            else -> SeriesBrowserScreen(
                                initialSeriesId = initialSeriesId,
                                onPlayUrl = onPlayUrl,
                                embedded = true,
                                hubSearchQuery = searchQuery,
                                contentFocusRequester = contentFocusRequester,
                                onMoveFocusUp = { moveFocusToTabs() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodHubSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
) {
    val shape = RoundedCornerShape(8.dp)
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var inputActive by remember { mutableStateOf(false) }
    val borderColor = if (isFocused || inputActive) EpgColors.Accent else EpgColors.BorderSubtle
    val backgroundColor = if (isFocused || inputActive) {
        EpgColors.Accent.copy(alpha = 0.14f)
    } else {
        Color(0xFF13131A)
    }

    fun openKeyboard() {
        if (!inputActive) {
            inputActive = true
            TvTextInputSession.begin()
        }
        scope.launch { showTextFieldKeyboard(keyboard, view, focusRequester) }
    }

    fun dismissInput() {
        if (inputActive) {
            inputActive = false
            TvTextInputSession.end()
            keyboard?.hide()
            TvRemoteKeyboard.dismissKeyboard(view)
        }
    }

    DisposableEffect(Unit) {
        onDispose { dismissInput() }
    }

    LaunchedEffect(isFocused) {
        if (!isFocused) dismissInput()
    }

    TvTextInputActivationEffect(active = isFocused || inputActive, onActivate = { openKeyboard() })

    fun handleFieldKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (inputActive) {
            return when {
                event.key == Key.Back || event.key == Key.Escape -> {
                    dismissInput()
                    true
                }
                TvImeKeyDispatcher.isImeNavigationKey(event.key) -> false
                else -> false
            }
        }
        return when {
            isTextFieldActivateKey(event) -> {
                openKeyboard()
                true
            }
            else -> onPreviewKeyEvent(event)
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = !inputActive,
        textStyle = TextStyle(
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        ),
        singleLine = true,
        modifier = modifier
            .height(48.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .lockFocusWhileTyping(inputActive)
            .onPreviewKeyEvent(::handleFieldKey)
            .onKeyEvent(::handleFieldKey)
            .background(backgroundColor, shape)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = if (isFocused) EpgColors.TextSecondary else EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 15.sp
                    )
                }
                inner()
            }
        }
    )
}
