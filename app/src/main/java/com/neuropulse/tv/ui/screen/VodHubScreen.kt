package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.VodPlaybackHelper
import com.neuropulse.tv.ui.component.ContinueWatchingRow
import com.neuropulse.tv.ui.component.EpgChipFilterBar
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.GridNavTabs
import com.neuropulse.tv.ui.component.VodHubHeader
import com.neuropulse.tv.ui.component.requestFocusSafelyAfterLayout
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import com.neuropulse.tv.ui.viewmodel.VodHubViewModel
import kotlinx.coroutines.delay

private enum class VodFocusZone { TOP_BAR, CONTINUE, SEARCH, TABS, CONTENT }

private val TopBarProfileIndex get() = GridNavTabs.size

@Composable
fun VodHubScreen(
    initialTab: Int = 0,
    initialSeriesId: Long? = null,
    profileInitials: String = "?",
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
    val searchQuery by hubViewModel.searchQuery.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        homeViewModel.livePlayerManager.setMode(com.neuropulse.tv.player.LivePlayerManager.Mode.MINI)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> when {
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
                        else -> {
                            onBack()
                            true
                        }
                    }
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF141420), EpgColors.Background)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VodHubHeader(
                    title = "On Demand",
                    subtitle = if (tab == 0) {
                        "Movies and specials from your provider"
                    } else {
                        "Browse shows by season and episode"
                    }
                )

                if (continueWatchingItems.isNotEmpty()) {
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = hubViewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
                        },
                    placeholder = {
                        Text(
                            text = if (tab == 0) "Search movies…" else "Search series…",
                            fontFamily = DmSansFamily
                        )
                    },
                    singleLine = true
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                when (tab) {
                    0 -> MoviesBrowserScreen(
                        onPlayMovie = onPlayMovie,
                        embedded = true,
                        hubSearchQuery = searchQuery,
                        contentFocusRequester = contentFocusRequester,
                        onMoveFocusUp = { moveFocusToTabs() }
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
