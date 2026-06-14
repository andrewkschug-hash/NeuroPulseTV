package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuropulse.tv.ui.component.requestFocusSafelyAfterLayout
import com.neuropulse.tv.ui.component.EpgChipFilterBar
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.GridNavTabs
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import com.neuropulse.tv.ui.viewmodel.VodHubViewModel
import kotlinx.coroutines.delay

private enum class VodFocusZone { TOP_BAR, CONTENT }

private val TopBarProfileIndex get() = GridNavTabs.size

@Composable
fun VodHubScreen(
    initialTab: Int = 0,
    initialSeriesId: Long? = null,
    profileInitials: String = "?",
    onPlayMovie: (String, String) -> Unit,
    onPlayUrl: (String, String) -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var focusZone by remember { mutableStateOf(VodFocusZone.CONTENT) }
    var topBarRow by remember { mutableIntStateOf(0) }
    var topBarFocusIndex by remember {
        mutableIntStateOf(GridNavTabs.indexOf(EpgNavTab.Vod).coerceAtLeast(0))
    }
    var tabFocusIndex by remember { mutableIntStateOf(tab) }
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
    hiltViewModel<VodHubViewModel>()
    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val activeRecordingTitle by recordingViewModel.activeRecordingTitle.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        homeViewModel.livePlayerManager.setMode(com.neuropulse.tv.player.LivePlayerManager.Mode.MINI)
    }

    val topNavFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusZone) {
        when (focusZone) {
            VodFocusZone.TOP_BAR -> topNavFocusRequester.requestFocusSafelyAfterLayout()
            VodFocusZone.CONTENT -> contentFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(tab) {
        tabFocusIndex = tab
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
        focusZone = VodFocusZone.TOP_BAR
        topBarRow = 1
        tabFocusIndex = tab
    }

    fun handleTopBarKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                    profileMenuOpen = false
                    false
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
                        focusZone = VodFocusZone.CONTENT
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

            EpgChipFilterBar(
                labels = listOf("Movies", "Series"),
                activeIndex = tab,
                focusedIndex = tabFocusIndex,
                barFocused = focusZone == VodFocusZone.TOP_BAR && topBarRow == 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                when (tab) {
                    0 -> MoviesBrowserScreen(
                        onPlayMovie = onPlayMovie,
                        embedded = true,
                        contentFocusRequester = contentFocusRequester,
                        onMoveFocusUp = { moveFocusToTabs() }
                    )
                    else -> SeriesBrowserScreen(
                        initialSeriesId = initialSeriesId,
                        onPlayUrl = onPlayUrl,
                        embedded = true,
                        contentFocusRequester = contentFocusRequester,
                        onMoveFocusUp = { moveFocusToTabs() }
                    )
                }
            }
        }
    }
}
