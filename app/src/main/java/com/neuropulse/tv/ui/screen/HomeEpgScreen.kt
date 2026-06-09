package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.feature.epg.EpgPlaceholderData
import com.neuropulse.tv.feature.epg.ChannelCategoryPresets
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.di.PlayerEntryPoint
import com.neuropulse.tv.player.LivePlayerManager
import dagger.hilt.android.EntryPointAccessors
import com.neuropulse.tv.ui.component.EpgChannelCell
import com.neuropulse.tv.ui.component.EpgDetailPanel
import com.neuropulse.tv.ui.component.EpgEmptyState
import com.neuropulse.tv.ui.component.EpgLayout
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgProgramCell
import com.neuropulse.tv.ui.component.EpgJumpToLiveButton
import com.neuropulse.tv.ui.component.EpgTimelineHeader
import com.neuropulse.tv.domain.model.SearchResultItem
import com.neuropulse.tv.domain.model.SearchResultType
import com.neuropulse.tv.ui.component.CategoryFilterMenu
import com.neuropulse.tv.ui.component.ContinueWatchingRow
import com.neuropulse.tv.ui.component.categoryFilterForMenuIndex
import com.neuropulse.tv.ui.component.categoryFilterMenuItemCount
import com.neuropulse.tv.ui.component.currentCategoryMenuIndex
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.GridNavTabs
import com.neuropulse.tv.ui.component.MiniNowPlayingPlayer
import com.neuropulse.tv.ui.component.RecordingPrecheckDialog
import com.neuropulse.tv.ui.component.SearchOverlay
import com.neuropulse.tv.ui.component.StorageLocationPicker
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.EpgGuidePosition
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import com.neuropulse.tv.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

private enum class EpgFocusZone { TOP_BAR, CONTINUE_WATCHING, GRID, DETAIL }

private val TopBarProfileIndex get() = GridNavTabs.size
private val TopBarCategoryFilterIndex get() = GridNavTabs.size + 1

@Composable
fun HomeEpgScreen(
    onWatchChannel: (Long) -> Unit,
    onPlayCatchup: (String, String) -> Unit = { _, _ -> },
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onNavigateSeries: (Long) -> Unit = {},
    onPlayVod: (String, String) -> Unit = { _, _ -> },
    profileInitials: String = "?",
    openFavoritesInitially: Boolean = false,
    viewModel: HomeEpgViewModel,
    recordingViewModel: RecordingViewModel,
    searchViewModel: SearchViewModel
) {
    val context = LocalContext.current
    val sleepTimer = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .sleepTimerController()
    }
    val sleepRemaining by sleepTimer.remainingSec.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val continueWatchingItems by viewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val favoriteGroups by viewModel.favoriteGroups.collectAsStateWithLifecycle()
    val favoriteGroupFilter by viewModel.favoriteGroupFilter.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val channelGroups by viewModel.channelGroups.collectAsStateWithLifecycle()
    val demoFavoriteIds by viewModel.demoFavoriteIds.collectAsStateWithLifecycle()
    val favoriteSavedMessage by viewModel.favoriteSavedMessage.collectAsStateWithLifecycle()
    val profileAccessMessage = viewModel.profileAccessMessage()
    val epgWindow by viewModel.epgPrograms.collectAsStateWithLifecycle()
    val windowStart by viewModel.windowStart.collectAsStateWithLifecycle()
    val windowDurationMs by viewModel.windowDurationMs.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val showStoragePicker by recordingViewModel.showStoragePicker.collectAsStateWithLifecycle()
    val storageOptions by recordingViewModel.storageOptions.collectAsStateWithLifecycle()
    val precheck by recordingViewModel.precheck.collectAsStateWithLifecycle()
    val livePlayerManager = viewModel.livePlayerManager

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.reloadPlaybackSettings()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadPlaybackSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showEmptyState = playlists.isEmpty()
    val usePlaceholder = playlists.isNotEmpty() && channels.isEmpty()
    val displayChannels = remember(
        channels,
        usePlaceholder,
        showEmptyState,
        favoriteGroupFilter,
        demoFavoriteIds,
        categoryFilter
    ) {
        when {
            showEmptyState -> emptyList()
            usePlaceholder -> {
                val all = ChannelCategoryPresets.apply(EpgPlaceholderData.channels(), categoryFilter)
                when (favoriteGroupFilter) {
                    null -> all
                    HomeEpgViewModel.FAVORITES_FILTER -> all.filter { it.id in demoFavoriteIds }
                    else -> emptyList()
                }
            }
            else -> channels
        }
    }
    val displayPrograms = remember(displayChannels, epgWindow, windowStart, windowDurationMs, usePlaceholder, showEmptyState) {
        when {
            showEmptyState -> emptyList()
            usePlaceholder -> EpgPlaceholderData.programs(
                windowStart,
                windowStart + windowDurationMs
            )
            else -> epgWindow
        }
    }

    LaunchedEffect(favoriteSavedMessage) {
        if (favoriteSavedMessage != null) {
            delay(2500)
            viewModel.clearFavoriteSavedMessage()
        }
    }

    LaunchedEffect(channels, continueWatching, usePlaceholder) {
        if (channels.isNotEmpty() && !usePlaceholder) {
            viewModel.tuneLastWatched(context)
        }
    }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
    }

    LaunchedEffect(usePlaceholder, displayChannels) {
        if (usePlaceholder && displayChannels.isNotEmpty()) {
            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
        }
    }

    val liveChannelId by livePlayerManager.activeChannelIdFlow.collectAsStateWithLifecycle()
    val playbackStatus by livePlayerManager.playbackStatus.collectAsStateWithLifecycle()
    val guidePosition by viewModel.guidePosition.collectAsStateWithLifecycle()

    val searchQuery by searchViewModel.queryText.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.results.collectAsStateWithLifecycle()
    var showSearchOverlay by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(EpgNavTab.Home) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }
    var focusZone by remember { mutableStateOf(EpgFocusZone.GRID) }
    var topBarFocusIndex by remember { mutableIntStateOf(0) }
    var focusedContinueIndex by remember { mutableIntStateOf(0) }
    var focusChannelIndex by remember { mutableIntStateOf(0) }
    var focusProgramIndex by remember { mutableIntStateOf(0) }
    var focusOnChannelColumn by remember { mutableStateOf(true) }
    var detailExpanded by remember { mutableStateOf(false) }
    var detailActionIndex by remember { mutableIntStateOf(0) }
    var showFavoritePicker by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showCategoryFilterMenu by remember { mutableStateOf(false) }
    var categoryMenuFocusIndex by remember { mutableIntStateOf(0) }

    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gridFocusRequester = remember { FocusRequester() }
    val topNavFocusRequester = remember { FocusRequester() }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    val timelineWidth = EpgLayout.timelineWidthMs(windowDurationMs)
    val livePlayer = livePlayerManager.activePlayer()

    var didInitialScroll by remember { mutableStateOf(false) }
    var didRestoreGuide by remember { mutableStateOf(false) }

    LaunchedEffect(guidePosition, displayChannels.size, liveChannelId) {
        if (didRestoreGuide || displayChannels.isEmpty()) return@LaunchedEffect
        if (guidePosition.hasSavedPosition) {
            focusChannelIndex = guidePosition.focusChannelIndex.coerceIn(0, displayChannels.lastIndex)
            focusProgramIndex = guidePosition.focusProgramIndex
            focusOnChannelColumn = guidePosition.focusOnChannelColumn
            listState.scrollToItem(focusChannelIndex)
            hScroll.scrollTo(guidePosition.timelineScrollPx.coerceIn(0, hScroll.maxValue))
            didInitialScroll = true
        } else {
            val idx = displayChannels.indexOfFirst { it.id == liveChannelId }
            if (idx >= 0) {
                focusChannelIndex = idx
                listState.scrollToItem(idx)
            }
        }
        didRestoreGuide = true
    }

    LaunchedEffect(displayChannels.size, windowStart, guidePosition.hasSavedPosition) {
        if (!didInitialScroll && displayChannels.isNotEmpty()) {
            if (guidePosition.hasSavedPosition) {
                didInitialScroll = true
            } else {
                val target = ((now - windowStart) * EpgLayout.dpPerMs() - 400f).coerceAtLeast(0f).toInt()
                hScroll.scrollTo(target)
                didInitialScroll = true
            }
        }
    }

    LaunchedEffect(
        focusChannelIndex,
        focusProgramIndex,
        focusOnChannelColumn,
        hScroll.value,
        displayChannels.size,
        didRestoreGuide
    ) {
        if (!didRestoreGuide || displayChannels.isEmpty()) return@LaunchedEffect
        viewModel.saveGuidePosition(
            EpgGuidePosition(
                focusChannelIndex = focusChannelIndex.coerceIn(0, displayChannels.lastIndex),
                focusProgramIndex = focusProgramIndex,
                focusOnChannelColumn = focusOnChannelColumn,
                timelineScrollPx = hScroll.value
            )
        )
    }

    LaunchedEffect(favoriteGroupFilter) {
        selectedTab = when (favoriteGroupFilter) {
            HomeEpgViewModel.FAVORITES_FILTER -> EpgNavTab.Favorites
            else -> if (selectedTab == EpgNavTab.Favorites) EpgNavTab.Home else selectedTab
        }
    }

    LaunchedEffect(openFavoritesInitially) {
        if (openFavoritesInitially) {
            viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
            selectedTab = EpgNavTab.Favorites
        }
    }

    LaunchedEffect(continueWatchingItems.size) {
        if (focusedContinueIndex > continueWatchingItems.lastIndex) {
            focusedContinueIndex = continueWatchingItems.lastIndex.coerceAtLeast(0)
        }
    }

    LaunchedEffect(displayChannels.size) {
        if (displayChannels.isNotEmpty()) {
            gridFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(displayChannels.size, focusZone) {
        when (focusZone) {
            EpgFocusZone.GRID -> if (displayChannels.isNotEmpty()) gridFocusRequester.requestFocus()
            EpgFocusZone.TOP_BAR -> topNavFocusRequester.requestFocus()
            EpgFocusZone.CONTINUE_WATCHING -> if (continueWatchingItems.isNotEmpty()) {
                continueWatchingFocusRequester.requestFocus()
            }
            EpgFocusZone.DETAIL -> detailFocusRequester.requestFocus()
        }
    }

    val focusedChannel = displayChannels.getOrNull(focusChannelIndex)
    val channelPrograms = remember(focusedChannel, displayPrograms) {
        focusedChannel?.epgId?.let { epgId ->
            displayPrograms.filter { it.channelEpgId == epgId }.sortedBy { it.startTime }
        } ?: emptyList()
    }
    val focusedProgram = if (focusOnChannelColumn) {
        channelPrograms.firstOrNull { now in it.startTime..it.endTime }
            ?: channelPrograms.firstOrNull()
    } else {
        channelPrograms.getOrNull(focusProgramIndex)
    }

    fun previewFocusedChannel() {
        if (usePlaceholder) return
        val ch = displayChannels.getOrNull(focusChannelIndex) ?: return
        val fullChannel = channels.find { it.id == ch.id } ?: ch
        if (fullChannel.streamUrl.isBlank()) return
        val title = focusedProgram?.title ?: ch.epgId?.let { epgId ->
            displayPrograms.firstOrNull { it.channelEpgId == epgId && now in it.startTime..it.endTime }?.title
        }
        viewModel.previewChannel(context, fullChannel, title)
    }

    LaunchedEffect(focusChannelIndex, focusedProgram?.id, displayChannels.size, usePlaceholder) {
        if (usePlaceholder) return@LaunchedEffect
        val ch = displayChannels.getOrNull(focusChannelIndex) ?: return@LaunchedEffect
        val cwIdx = continueWatchingItems.indexOfFirst { it.channel.id == ch.id }
        if (cwIdx >= 0) focusedContinueIndex = cwIdx
        previewFocusedChannel()
    }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
    }

    val previewStreamStatus = if (focusedChannel?.id == liveChannelId) {
        playbackStatus
    } else {
        com.neuropulse.tv.player.StreamPlaybackStatus.IDLE
    }

    LaunchedEffect(liveChannelId, playbackStatus) {
        liveChannelId?.let { viewModel.reportPlaybackHealth(it, playbackStatus) }
    }

    fun programsForChannel(channel: Channel): List<Program> =
        channel.epgId?.let { epgId ->
            displayPrograms.filter { it.channelEpgId == epgId }.sortedBy { it.startTime }
        } ?: emptyList()

    fun clampProgramIndex(channelIdx: Int, programIdx: Int): Int {
        val progs = displayChannels.getOrNull(channelIdx)?.let { programsForChannel(it) } ?: emptyList()
        return programIdx.coerceIn(0, (progs.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(displayChannels.size) {
        if (displayChannels.isEmpty()) {
            focusChannelIndex = 0
            focusProgramIndex = 0
            focusOnChannelColumn = false
        } else if (focusChannelIndex > displayChannels.lastIndex) {
            focusChannelIndex = displayChannels.lastIndex
            focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
        }
    }

    fun liveScrollTarget(): Int =
        ((now - windowStart) * EpgLayout.dpPerMs() - 400f).coerceAtLeast(0f).toInt()

    fun scrollToProgram(program: Program?) {
        program ?: return
        val startPx = ((program.startTime - windowStart) * EpgLayout.dpPerMs()).coerceAtLeast(0f)
        val widthPx = (program.endTime - program.startTime) * EpgLayout.dpPerMs()
        val centerPx = startPx + widthPx / 2f
        val viewportHalf = 420f
        val target = (centerPx - viewportHalf).coerceAtLeast(0f).toInt()
        scope.launch { hScroll.animateScrollTo(target.coerceAtMost(hScroll.maxValue)) }
    }

    fun scrollByTimeSlot(direction: Int) {
        val slotPx = (EpgLayout.ThirtyMinWidthDp * direction).toInt()
        scope.launch {
            val target = (hScroll.value + slotPx).coerceIn(0, hScroll.maxValue)
            hScroll.animateScrollTo(target)
        }
    }

    fun scrollToLive() {
        scope.launch { hScroll.animateScrollTo(liveScrollTarget()) }
    }

    fun activateNavTab(tab: EpgNavTab) {
        selectedTab = tab
        when (tab) {
            EpgNavTab.Home -> viewModel.setFavoriteGroupFilter(null)
            EpgNavTab.Search -> showSearchOverlay = true
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> viewModel.setFavoriteGroupFilter(HomeEpgViewModel.FAVORITES_FILTER)
            EpgNavTab.Settings -> onNavigateSettings()
        }
    }

    fun handleSearchResult(result: SearchResultItem) {
        showSearchOverlay = false
        searchViewModel.clearQuery()
        when (result.type) {
            SearchResultType.CHANNEL -> result.channelId?.let { chId ->
                val ch = displayChannels.find { it.id == chId } ?: channels.find { it.id == chId }
                if (ch != null) {
                    if (!usePlaceholder) {
                        scope.launch {
                            livePlayerManager.tuneChannel(context, ch.id, ch.streamUrl)
                            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
                        }
                    }
                    val idx = displayChannels.indexOfFirst { it.id == chId }
                    if (idx >= 0) {
                        focusChannelIndex = idx
                        focusOnChannelColumn = true
                        focusZone = EpgFocusZone.GRID
                    }
                    if (ch.streamUrl.isNotBlank()) {
                        onWatchChannel(ch.id)
                    }
                }
            }
            SearchResultType.PROGRAM -> {
                val chId = result.channelId
                val prog = result.program
                if (chId != null && prog != null) {
                    val chIdx = displayChannels.indexOfFirst { it.id == chId }
                    if (chIdx >= 0) {
                        focusChannelIndex = chIdx
                        val progs = programsForChannel(displayChannels[chIdx])
                        focusProgramIndex = progs.indexOfFirst { it.id == prog.id }.coerceAtLeast(0)
                        focusOnChannelColumn = false
                        scrollToProgram(prog)
                        focusZone = EpgFocusZone.GRID
                    }
                }
            }
            SearchResultType.VOD -> result.vodItem?.let { onPlayVod(it.streamUrl, it.title) }
            SearchResultType.SERIES -> result.seriesShow?.let { onNavigateSeries(it.id) }
        }
    }

    fun executeDetailAction() {
        when (detailActionIndex) {
            0 -> {
                val prog = focusedProgram ?: return
                val ch = focusedChannel ?: return
                if (prog.endTime < now && (prog.catchupUrl != null || ch.catchupDays > 0)) {
                    scope.launch {
                        val url = viewModel.buildCatchupUrl(prog, ch)
                        if (url != null) onPlayCatchup(prog.title, url) else onWatchChannel(ch.id)
                    }
                } else {
                    onWatchChannel(ch.id)
                }
            }
            1 -> focusedProgram?.let { prog ->
                val ch = focusedChannel ?: return@let
                if (prog.startTime <= now) {
                    val duration = (prog.endTime - now).coerceAtLeast(10 * 60 * 1000)
                    recordingViewModel.startImmediateRecording(context, ch, prog.title, duration)
                } else {
                    recordingViewModel.scheduleProgram(ch, prog)
                }
            }
            2 -> {
                val ch = focusedChannel ?: return
                viewModel.saveChannelToFavorites(ch.id, ch.name)
                focusZone = EpgFocusZone.GRID
            }
            3 -> detailExpanded = true
        }
    }

    fun handleTopBarKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (showCategoryFilterMenu) {
            val menuCount = categoryFilterMenuItemCount(channelGroups)
            return when (event.key) {
                Key.DirectionUp -> {
                    categoryMenuFocusIndex = (categoryMenuFocusIndex - 1).coerceAtLeast(0)
                    true
                }
                Key.DirectionDown -> {
                    categoryMenuFocusIndex = (categoryMenuFocusIndex + 1).coerceAtMost(menuCount - 1)
                    true
                }
                Key.Back, Key.Escape -> {
                    showCategoryFilterMenu = false
                    true
                }
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    viewModel.setCategoryFilter(
                        categoryFilterForMenuIndex(categoryMenuFocusIndex, channelGroups)
                    )
                    showCategoryFilterMenu = false
                    true
                }
                else -> false
            }
        }
        if (profileMenuOpen) {
            return when (event.key) {
                Key.DirectionUp -> {
                    profileMenuFocusIndex = (profileMenuFocusIndex - 1).coerceAtLeast(0)
                    true
                }
                Key.DirectionDown -> {
                    profileMenuFocusIndex = (profileMenuFocusIndex + 1).coerceAtMost(1)
                    true
                }
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    profileMenuOpen = false
                    when (profileMenuFocusIndex) {
                        0 -> onNavigateProfile()
                        1 -> onNavigateSettings()
                    }
                    true
                }
                else -> false
            }
        }
        return when (event.key) {
            Key.DirectionLeft -> {
                topBarFocusIndex = (topBarFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarCategoryFilterIndex)
                true
            }
            Key.DirectionDown -> {
                focusZone = if (continueWatchingItems.isNotEmpty()) {
                    EpgFocusZone.CONTINUE_WATCHING
                } else {
                    EpgFocusZone.GRID
                }
                focusOnChannelColumn = true
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (topBarFocusIndex) {
                    in GridNavTabs.indices -> activateNavTab(GridNavTabs[topBarFocusIndex])
                    TopBarProfileIndex -> {
                        profileMenuOpen = true
                        profileMenuFocusIndex = 0
                    }
                    TopBarCategoryFilterIndex -> {
                        categoryMenuFocusIndex = currentCategoryMenuIndex(categoryFilter, channelGroups)
                        showCategoryFilterMenu = true
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleContinueWatchingKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (continueWatchingItems.isEmpty()) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                focusedContinueIndex = (focusedContinueIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                focusedContinueIndex = (focusedContinueIndex + 1)
                    .coerceAtMost(continueWatchingItems.lastIndex)
                true
            }
            Key.DirectionUp -> {
                focusZone = EpgFocusZone.TOP_BAR
                topBarFocusIndex = TopBarCategoryFilterIndex
                true
            }
            Key.DirectionDown -> {
                focusZone = EpgFocusZone.GRID
                focusOnChannelColumn = true
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                continueWatchingItems.getOrNull(focusedContinueIndex)?.let { item ->
                    if (viewModel.isProfileAccessAllowed()) {
                        viewModel.resumeContinueWatching(context, item)
                        onWatchChannel(item.channel.id)
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleDetailKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> {
                if (detailActionIndex > 0) {
                    detailActionIndex -= 1
                } else if (detailActionIndex == 0) {
                    detailActionIndex = -1
                }
                true
            }
            Key.DirectionRight -> {
                if (detailActionIndex < 0) {
                    detailActionIndex = 0
                } else {
                    detailActionIndex = (detailActionIndex + 1).coerceAtMost(3)
                }
                true
            }
            Key.DirectionUp -> {
                focusZone = EpgFocusZone.GRID
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (detailActionIndex < 0) {
                    focusedChannel?.let { onWatchChannel(it.id) }
                } else {
                    executeDetailAction()
                }
                true
            }
            Key.Back, Key.Escape -> {
                focusZone = EpgFocusZone.GRID
                detailExpanded = false
                true
            }
            else -> false
        }
    }

    fun handleGridKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (displayChannels.isEmpty()) return false

        return when (event.key) {
            Key.DirectionDown -> {
                if (focusChannelIndex < displayChannels.lastIndex) {
                    focusChannelIndex += 1
                    if (!focusOnChannelColumn) {
                        focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                    }
                    scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                }
                true
            }
            Key.DirectionUp -> {
                if (focusChannelIndex > 0) {
                    focusChannelIndex -= 1
                    if (!focusOnChannelColumn) {
                        focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                    }
                    scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                } else {
                    focusZone = if (continueWatchingItems.isNotEmpty()) {
                        EpgFocusZone.CONTINUE_WATCHING
                    } else {
                        EpgFocusZone.TOP_BAR
                    }
                    if (focusZone == EpgFocusZone.TOP_BAR) topBarFocusIndex = 0
                }
                true
            }
            Key.DirectionRight -> {
                val channel = displayChannels.getOrNull(focusChannelIndex) ?: return false
                val progs = programsForChannel(channel)
                if (focusOnChannelColumn) {
                    focusOnChannelColumn = false
                    val progsNow = progs.indexOfFirst { now in it.startTime..it.endTime }
                    focusProgramIndex = if (progsNow >= 0) progsNow else clampProgramIndex(focusChannelIndex, 0)
                    scrollToProgram(progs.getOrNull(focusProgramIndex))
                } else if (focusProgramIndex < progs.lastIndex) {
                    focusProgramIndex += 1
                    scrollToProgram(progs[focusProgramIndex])
                } else {
                    val last = progs.lastOrNull()
                    val windowEnd = windowStart + windowDurationMs
                    if (last != null && last.endTime >= windowEnd - 15 * 60 * 1000) {
                        viewModel.extendWindowForward()
                    }
                    scrollByTimeSlot(1)
                }
                true
            }
            Key.DirectionLeft -> {
                val channel = displayChannels.getOrNull(focusChannelIndex) ?: return false
                val progs = programsForChannel(channel)
                if (!focusOnChannelColumn && focusProgramIndex > 0) {
                    focusProgramIndex -= 1
                    scrollToProgram(progs[focusProgramIndex])
                } else if (!focusOnChannelColumn) {
                    focusOnChannelColumn = true
                } else {
                    val first = progs.firstOrNull()
                    if (first != null && first.startTime <= windowStart + 15 * 60 * 1000) {
                        val scrollAdjust = viewModel.extendWindowBackward()
                        if (scrollAdjust > 0) {
                            scope.launch { hScroll.scrollTo(hScroll.value + scrollAdjust) }
                        }
                    }
                    scrollByTimeSlot(-1)
                }
                true
            }
            Key.PageDown -> {
                focusChannelIndex = (focusChannelIndex + 10).coerceAtMost(displayChannels.lastIndex)
                if (!focusOnChannelColumn) focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                true
            }
            Key.PageUp -> {
                focusChannelIndex = (focusChannelIndex - 10).coerceAtLeast(0)
                if (!focusOnChannelColumn) focusProgramIndex = clampProgramIndex(focusChannelIndex, focusProgramIndex)
                scope.launch { listState.animateScrollToItem(focusChannelIndex) }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                previewFocusedChannel()
                if (focusedProgram != null) {
                    focusZone = EpgFocusZone.DETAIL
                    detailActionIndex = -1
                    detailExpanded = true
                } else if (focusOnChannelColumn) {
                    focusedChannel?.let { onWatchChannel(it.id) }
                } else {
                    focusedChannel?.let { onWatchChannel(it.id) }
                }
                true
            }
            Key.Back, Key.Escape -> {
                if (detailExpanded) {
                    detailExpanded = false
                    focusZone = EpgFocusZone.GRID
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    val liveScrollTargetPx = liveScrollTarget()
    val scrolledAwayFromLive = kotlin.math.abs(hScroll.value - liveScrollTargetPx) > 80

    if (showEmptyState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EpgColors.Background),
            contentAlignment = Alignment.Center
        ) {
            EpgEmptyState(onAddPlaylist = onNavigateSettings)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
                selectedTab = selectedTab,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == EpgFocusZone.TOP_BAR && topBarFocusIndex <= GridNavTabs.lastIndex,
                profileFocused = focusZone == EpgFocusZone.TOP_BAR && topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
                profileMenuExpanded = profileMenuOpen,
                profileMenuFocusIndex = profileMenuFocusIndex,
                categoryFilterLabel = categoryFilter.label,
                categoryFilterActive = categoryFilter.isActive,
                categoryFilterFocused = focusZone == EpgFocusZone.TOP_BAR &&
                    topBarFocusIndex == TopBarCategoryFilterIndex,
                onCategoryFilterClick = {
                    focusZone = EpgFocusZone.TOP_BAR
                    topBarFocusIndex = TopBarCategoryFilterIndex
                    categoryMenuFocusIndex = currentCategoryMenuIndex(categoryFilter, channelGroups)
                    showCategoryFilterMenu = true
                },
                onProfileClick = {
                    focusZone = EpgFocusZone.TOP_BAR
                    topBarFocusIndex = TopBarProfileIndex
                    profileMenuOpen = true
                    profileMenuFocusIndex = 0
                },
                onSwitchAccounts = onNavigateProfile,
                onOpenSettings = onNavigateSettings,
                onTabSelected = { tab ->
                    focusZone = EpgFocusZone.TOP_BAR
                    topBarFocusIndex = GridNavTabs.indexOf(tab)
                    activateNavTab(tab)
                },
                miniPlayer = {},
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == EpgFocusZone.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            if (profileAccessMessage != null) {
                androidx.tv.material3.Text(
                    text = profileAccessMessage,
                    color = androidx.compose.ui.graphics.Color(0xFFFFB020),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            ContinueWatchingRow(
                items = continueWatchingItems,
                focusedIndex = focusedContinueIndex,
                rowFocused = focusZone == EpgFocusZone.CONTINUE_WATCHING,
                onSelect = { item ->
                    if (viewModel.isProfileAccessAllowed()) {
                        viewModel.resumeContinueWatching(context, item)
                        onWatchChannel(item.channel.id)
                    }
                },
                modifier = Modifier
                    .focusRequester(continueWatchingFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        focusZone == EpgFocusZone.CONTINUE_WATCHING && handleContinueWatchingKey(it)
                    }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(gridFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        focusZone == EpgFocusZone.GRID && handleGridKey(event)
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(EpgLayout.ChannelColumnWidth)
                                .height(EpgLayout.TimelineHeaderHeight)
                                .background(EpgColors.ChannelColumnBg)
                                .padding(start = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            androidx.tv.material3.Text(
                                text = com.neuropulse.tv.ui.component.formatEpgDay(now),
                                color = EpgColors.TextSecondary,
                                fontFamily = com.neuropulse.tv.ui.theme.DmSansFamily,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(hScroll, enabled = false)
                        ) {
                            EpgTimelineHeader(
                                windowStart = windowStart,
                                windowDurationMs = windowDurationMs,
                                now = now
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayChannels.size) { index ->
                                val channel = displayChannels[index]
                                val programs = programsForChannel(channel)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    EpgChannelCell(
                                        channel = channel,
                                        isFocused = focusZone == EpgFocusZone.GRID &&
                                            focusOnChannelColumn && index == focusChannelIndex,
                                        showBottomSeparator = index < displayChannels.lastIndex,
                                        modifier = Modifier.width(EpgLayout.ChannelColumnWidth)
                                    )
                                    EpgChannelTimelineRow(
                                        channel = channel,
                                        programs = programs,
                                        windowStart = windowStart,
                                        now = now,
                                        channelIndex = index,
                                        focusChannelIndex = focusChannelIndex,
                                        focusProgramIndex = focusProgramIndex,
                                        focusOnChannelColumn = focusOnChannelColumn,
                                        gridFocused = focusZone == EpgFocusZone.GRID,
                                        isRowFocused = focusZone == EpgFocusZone.GRID && index == focusChannelIndex,
                                        confirmedProgramId = if (focusZone == EpgFocusZone.DETAIL) focusedProgram?.id else null,
                                        scheduled = scheduled,
                                        hScrollModifier = Modifier.horizontalScroll(hScroll, enabled = false),
                                        timelineWidth = timelineWidth
                                    )
                                }
                            }
                        }
                    }
                }

                EpgJumpToLiveButton(
                    visible = scrolledAwayFromLive,
                    onClick = { scrollToLive() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 12.dp)
                )
            }

            EpgDetailPanel(
                channel = focusedChannel,
                program = focusedProgram,
                now = now,
                detailActionFocused = if (focusZone == EpgFocusZone.DETAIL) detailActionIndex else -1,
                onActionFocusChange = { detailActionIndex = it },
                onWatch = {
                    detailActionIndex = 0
                    executeDetailAction()
                },
                onFavorite = {
                    focusedChannel?.let { viewModel.saveChannelToFavorites(it.id, it.name) }
                },
                onRecord = {
                    detailActionIndex = 1
                    executeDetailAction()
                },
                onMoreInfo = { detailExpanded = true },
                visible = focusedProgram != null,
                streamStatus = previewStreamStatus,
                previewContent = {
                    MiniNowPlayingPlayer(
                        channel = focusedChannel,
                        program = focusedProgram,
                        player = livePlayer,
                        isFocused = focusZone == EpgFocusZone.DETAIL && detailActionIndex == -1,
                        sleepCountdown = sleepTimer.formatCountdown().takeIf { sleepRemaining > 0 },
                        streamStatus = previewStreamStatus,
                        onFocus = {
                            focusZone = EpgFocusZone.DETAIL
                            detailActionIndex = -1
                        },
                        modifier = Modifier.focusable()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(detailFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == EpgFocusZone.DETAIL) handleDetailKey(it) else false
                    }
            )
        }

        CategoryFilterMenu(
            expanded = showCategoryFilterMenu,
            focusedIndex = categoryMenuFocusIndex,
            playlistGroups = channelGroups,
            onDismiss = { showCategoryFilterMenu = false },
            onSelect = { index ->
                viewModel.setCategoryFilter(categoryFilterForMenuIndex(index, channelGroups))
                showCategoryFilterMenu = false
            }
        )

        if (showFavoritePicker && favoriteGroups.isNotEmpty()) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showFavoritePicker = false },
                title = { androidx.tv.material3.Text("Add to group") },
                text = {
                    Column {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(favoriteGroups.size) { idx ->
                                val group = favoriteGroups[idx]
                                androidx.tv.material3.Button(onClick = {
                                    focusedChannel?.let {
                                        viewModel.addChannelToFavoriteGroup(it.id, group.id)
                                        showFavoritePicker = false
                                    }
                                }) { androidx.tv.material3.Text(group.name) }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.tv.material3.Button(onClick = { showFavoritePicker = false }) {
                        androidx.tv.material3.Text("Close")
                    }
                }
            )
        }

        favoriteSavedMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = EpgLayout.DetailPanelHeight + 16.dp)
                    .background(
                        EpgColors.DetailPanelBg,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                androidx.tv.material3.Text(
                    text = message,
                    color = EpgColors.TextPrimary,
                    fontFamily = com.neuropulse.tv.ui.theme.DmSansFamily,
                    fontSize = 14.sp
                )
            }
        }

        if (showCreateGroup) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCreateGroup = false },
                title = { androidx.tv.material3.Text("New favorite group") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { androidx.tv.material3.Text("Group name") }
                    )
                },
                confirmButton = {
                    androidx.tv.material3.Button(onClick = {
                        if (newGroupName.isNotBlank()) {
                            viewModel.createFavoriteGroup(newGroupName.trim())
                            newGroupName = ""
                            showCreateGroup = false
                        }
                    }) { androidx.tv.material3.Text("Create") }
                }
            )
        }

        if (showStoragePicker) {
            StorageLocationPicker(
                options = storageOptions,
                onSelect = { recordingViewModel.onStorageSelected(it, context) },
                onDismiss = { recordingViewModel.dismissStoragePicker() }
            )
        }

        precheck?.let { check ->
            RecordingPrecheckDialog(
                estimateText = check.estimateText,
                lowStorageWarning = check.lowStorageWarning,
                insufficientSpaceWarning = check.insufficientSpaceWarning,
                onConfirm = { recordingViewModel.confirmImmediateRecording(context) },
                onDismiss = { recordingViewModel.dismissPrecheck() }
            )
        }

        if (showSearchOverlay) {
            SearchOverlay(
                query = searchQuery,
                results = searchResults,
                onQueryChange = searchViewModel::updateQuery,
                onClear = searchViewModel::clearQuery,
                onDismiss = {
                    showSearchOverlay = false
                    searchViewModel.clearQuery()
                    focusZone = EpgFocusZone.GRID
                },
                onResultSelected = { handleSearchResult(it) }
            )
        }
    }
}

@Composable
private fun EpgChannelTimelineRow(
    channel: Channel,
    programs: List<Program>,
    windowStart: Long,
    now: Long,
    channelIndex: Int,
    focusChannelIndex: Int,
    focusProgramIndex: Int,
    focusOnChannelColumn: Boolean,
    gridFocused: Boolean,
    isRowFocused: Boolean,
    confirmedProgramId: Long?,
    scheduled: List<ScheduledRecordingEntity>,
    hScrollModifier: Modifier,
    timelineWidth: Dp
) {
    Box(
        modifier = hScrollModifier
            .width(timelineWidth)
            .height(EpgLayout.RowHeight)
            .background(if (isRowFocused) EpgColors.ChannelRowFocusBg.copy(alpha = 0.35f) else EpgColors.GridBg)
    ) {
        programs.forEachIndexed { programIndex, program ->
            val width = EpgLayout.widthForDurationMs(program.endTime - program.startTime) - EpgLayout.CellGap
            val offset = EpgLayout.offsetForTime(program.startTime, windowStart)
            val isFocused = gridFocused &&
                channelIndex == focusChannelIndex &&
                !focusOnChannelColumn &&
                programIndex == focusProgramIndex
            val isSelected = confirmedProgramId == program.id
            val hasRec = scheduled.any {
                it.channelId == channel.id &&
                    it.programTitle == program.title &&
                    it.startTime == program.startTime &&
                    it.status != RecordingStatus.FAILED.name
            }
            val title = buildString {
                if (hasRec) append("REC ")
                if (program.catchupUrl != null && program.endTime < now) append("⏪ ")
                append(program.title)
            }

            EpgProgramCell(
                program = program.copy(title = title),
                width = width.coerceAtLeast(40.dp),
                now = now,
                isFocused = isFocused,
                isSelected = isSelected,
                modifier = Modifier.offset(x = offset)
            )
        }
    }
}
