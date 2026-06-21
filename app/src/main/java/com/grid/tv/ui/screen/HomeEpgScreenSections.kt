package com.grid.tv.ui.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grid.tv.data.db.entity.ScheduledRecordingEntity
import com.grid.tv.domain.epg.EpgProgramReplayState
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.recording.RecordingHealth
import com.grid.tv.feature.recording.RecordingStatus
import com.grid.tv.player.StreamPlaybackStatus
import com.grid.tv.ui.component.ContinueWatchingRow
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgCategoryFilterChip
import com.grid.tv.ui.component.EpgChannelCell
import com.grid.tv.ui.component.EpgEmptyState
import com.grid.tv.ui.component.EpgJumpToLiveButton
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.ui.component.EpgNowLine
import com.grid.tv.ui.component.EpgPreviewSection
import com.grid.tv.ui.component.EpgNoInformationCell
import com.grid.tv.ui.component.EpgProgramCell
import com.grid.tv.ui.component.EpgTimelineHeader
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.component.GuideGroupFilterMenu
import com.grid.tv.ui.component.GuideGroupPickerDialog
import com.grid.tv.ui.component.RecordingPrecheckDialog
import com.grid.tv.ui.component.SearchOverlay
import com.grid.tv.ui.component.StorageLocationPicker
import com.grid.tv.ui.component.EpgNowTicker
import com.grid.tv.ui.component.rememberEpgNowMillis
import com.grid.tv.ui.component.formatEpgDay
import com.grid.tv.ui.component.formatLastChecked
import com.grid.tv.ui.platform.LocalDeviceFormFactor
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.HomeEpgViewModel
import com.grid.tv.ui.viewmodel.RecordingViewModel
import com.grid.tv.ui.viewmodel.SearchViewModel
import com.grid.tv.util.quitAppToHome

@Composable
internal fun HomeEpgScreenLoadingGate(
    isInitializing: Boolean,
    guideSettingsLoaded: Boolean,
    showEmptyState: Boolean,
    onNavigateSettings: () -> Unit
): Boolean {
    if (isInitializing || !guideSettingsLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EpgColors.Background),
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Text(
                text = "Loading guide…",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 16.sp
            )
        }
        return true
    }
    if (showEmptyState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EpgColors.Background),
            contentAlignment = Alignment.Center
        ) {
            EpgEmptyState(onAddPlaylist = onNavigateSettings)
        }
        return true
    }
    return false
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeEpgScreenMainColumn(
    modifier: Modifier = Modifier,
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    deps: HomeEpgGuideDeps,
    context: Context,
    profileInitials: String,
    profileAvatarColor: String,
    profileAccessMessage: String?,
    isRecording: Boolean,
    activeRecordingTitle: String?,
    recordingHealth: RecordingHealth?,
    onNavigateRecordings: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateSettings: () -> Unit,
    previewPlayer: androidx.media3.exoplayer.ExoPlayer?,
    previewStreamStatus: StreamPlaybackStatus?,
    previewSurfaceAttached: Boolean,
    channelGroups: List<String>,
    channelScanStatuses: Map<Long, ChannelScanSnapshot>,
    scheduled: List<ScheduledRecordingEntity>,
    timelineWidth: Dp,
    scrolledAwayFromLive: Boolean,
    showFilteredEmptyState: Boolean,
    hScroll: ScrollState,
    listState: LazyListState,
) {
    val showGroupFilter = ui.selectedTab != EpgNavTab.Favorites
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (ui.showSearchOverlay) {
                    Modifier.focusProperties { canFocus = false }
                } else {
                    Modifier
                }
            )
            .onPreviewKeyEvent {
                if (ui.showSearchOverlay) return@onPreviewKeyEvent false
                if (ui.focusZone == EpgFocusZone.PREVIEW) {
                    controller.handlePreviewKey(it)
                } else {
                    false
                }
            }
    ) {
        EpgTopBar(
            selectedTab = ui.selectedTab,
            focusedNavTabIndex = ui.topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
            navFocused = ui.focusZone == EpgFocusZone.TOP_BAR && ui.topBarFocusIndex <= GridNavTabs.lastIndex,
            profileFocused = ui.focusZone == EpgFocusZone.TOP_BAR && ui.topBarFocusIndex == TopBarProfileIndex,
            profileInitials = profileInitials,
            profileAvatarColor = profileAvatarColor,
            profileMenuExpanded = ui.profileMenuOpen,
            profileMenuFocusIndex = ui.profileMenuFocusIndex,
            onProfileClick = {
                ui.focusZone = EpgFocusZone.TOP_BAR
                ui.topBarFocusIndex = TopBarProfileIndex
                ui.profileMenuOpen = true
                ui.profileMenuFocusIndex = 0
            },
            onSwitchAccounts = {
                ui.profileMenuOpen = false
                onNavigateProfile()
            },
            onOpenSettings = {
                ui.profileMenuOpen = false
                onNavigateSettings()
            },
            onQuitApp = { context.quitAppToHome() },
            onProfileMenuDismiss = { ui.profileMenuOpen = false },
            onTabSelected = { tab ->
                ui.focusZone = EpgFocusZone.TOP_BAR
                ui.topBarFocusIndex = GridNavTabs.indexOf(tab)
                controller.activateNavTab(tab)
            },
            miniPlayer = {},
            isRecording = isRecording,
            activeRecordingTitle = activeRecordingTitle,
            recordingHealth = recordingHealth ?: RecordingHealth.RECORDING,
            onRecordingIndicatorClick = onNavigateRecordings,
            modifier = Modifier
                .focusRequester(deps.topNavFocusRequester)
                .focusProperties {
                    down = when {
                        deps.hasContinueWatching -> deps.continueWatchingFocusRequester
                        deps.showPreviewSection -> deps.previewFocusRequester
                        showGroupFilter -> deps.gridFilterFocusRequester
                        else -> deps.gridFocusRequester
                    }
                }
                .focusable()
                .onFocusChanged { if (it.isFocused) ui.focusZone = EpgFocusZone.TOP_BAR }
                .onPreviewKeyEvent {
                    if (ui.focusZone == EpgFocusZone.TOP_BAR) controller.handleTopBarKey(it) else false
                }
        )

        if (profileAccessMessage != null) {
            androidx.tv.material3.Text(
                text = profileAccessMessage,
                color = androidx.compose.ui.graphics.Color(0xFFFFB020),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (deps.showPreviewSection && deps.previewChannel != null) {
            val previewChannel = deps.previewChannel
            val initialPreviewFavorite = if (previewChannel.id < 0) {
                previewChannel.id in deps.demoFavoriteIds
            } else {
                previewChannel.isFavorite
            }
            val previewIsFavorite by deps.viewModel
                .observeChannelFavorite(previewChannel.id)
                .collectAsStateWithLifecycle(initialValue = initialPreviewFavorite)
            HomeEpgPreviewSection(
                channel = previewChannel,
                program = deps.previewProgram,
                nextProgram = deps.previewNextProgram,
                player = previewPlayer,
                streamStatus = previewStreamStatus,
                detailActionIndex = ui.detailActionIndex,
                previewFocused = ui.focusZone == EpgFocusZone.PREVIEW,
                attachSurface = previewSurfaceAttached,
                isFavorite = previewIsFavorite,
                primaryActionLabel = controller.primaryActionLabel(deps.previewChannel, deps.previewProgram),
                onWatch = {
                    ui.detailActionIndex = 0
                    controller.executeDetailAction()
                },
                onFavorite = {
                    ui.detailActionIndex = 1
                    controller.executeDetailAction()
                },
                onRecord = {
                    ui.detailActionIndex = 2
                    controller.executeDetailAction()
                },
                previewFocusRequester = deps.previewFocusRequester,
                continueWatchingFocusRequester = deps.continueWatchingFocusRequester,
                topNavFocusRequester = deps.topNavFocusRequester,
                gridFilterFocusRequester = deps.gridFilterFocusRequester,
                gridFocusRequester = deps.gridFocusRequester,
                showGroupFilter = showGroupFilter,
                hasContinueWatching = deps.hasContinueWatching,
                onFocused = { ui.focusZone = EpgFocusZone.PREVIEW }
            )
        }

        HomeEpgChannelList(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            gridFocusRequester = deps.gridFocusRequester,
            onGridKey = controller::handleGridKey,
            gridFocused = ui.focusZone == EpgFocusZone.GRID,
            displayChannelsEmpty = deps.displayChannels.isEmpty(),
            hScroll = hScroll,
            windowStart = deps.windowStart,
            windowDurationMs = deps.windowDurationMs,
            guideFilter = deps.guideFilter,
            channelGroups = channelGroups,
            showGroupFilter = showGroupFilter,
            gridFilterFocused = ui.focusZone == EpgFocusZone.GRID_FILTER && !ui.showCategoryFilterMenu,
            gridFilterFocusRequester = deps.gridFilterFocusRequester,
            previewFocusRequester = deps.previewFocusRequester,
            continueWatchingFocusRequester = deps.continueWatchingFocusRequester,
            topNavFocusRequester = deps.topNavFocusRequester,
            showPreviewSection = deps.showPreviewSection,
            hasContinueWatching = deps.hasContinueWatching,
            onOpenCategoryFilter = {
                controller.focusEpgZone(EpgFocusZone.GRID_FILTER)
                controller.openCategoryFilterMenu()
            },
            onGridFilterKey = controller::handleGridFilterKey,
            onGridFocused = { ui.focusZone = EpgFocusZone.GRID },
            onGridFilterFocused = {
                if (!ui.pendingPreviewFocus) {
                    ui.focusZone = EpgFocusZone.GRID_FILTER
                }
            },
            listState = listState,
            displayChannels = deps.displayChannels,
            filteredEmptyMessage = when {
                !showFilteredEmptyState -> null
                deps.guideFilter.isActive -> "No channels match your selected groups"
                else -> "No channels in this favorites group"
            },
            programsForChannel = controller::programsForChannel,
            channelScanStatuses = channelScanStatuses,
            focusChannelIndex = ui.focusChannelIndex,
            focusProgramIndex = ui.focusProgramIndex,
            focusOnChannelColumn = ui.focusOnChannelColumn,
            confirmedProgramId = if (ui.focusZone == EpgFocusZone.PREVIEW) deps.focusedProgram?.id else null,
            scheduled = scheduled,
            timelineWidth = timelineWidth,
            scrolledAwayFromLive = scrolledAwayFromLive,
            onJumpToLive = controller::scrollToLive,
            onChannelClick = controller::openChannelFromTouch,
            onProgramClick = controller::openProgramFromTouch,
            replayStateFor = { channel, program ->
                deps.viewModel.replayState(
                    program,
                    deps.channels.find { it.id == channel.id } ?: channel,
                    System.currentTimeMillis()
                )
            }
        )
    }
}

@Composable
internal fun HomeEpgScreenOverlays(
    modifier: Modifier = Modifier,
    ui: HomeEpgUiState,
    controller: HomeEpgGuideController,
    deps: HomeEpgGuideDeps,
    context: Context,
    channelGroups: List<String>,
    groupChannelCounts: Map<String, Int>,
    favoriteGroups: List<com.grid.tv.domain.model.FavoriteGroup>,
    favoriteSavedMessage: String?,
    recordingViewModel: RecordingViewModel,
    showStoragePicker: Boolean,
    storageOptions: List<com.grid.tv.feature.recording.StorageOption>,
    precheck: com.grid.tv.feature.recording.RecordingPrecheck?,
    searchQuery: String,
    unifiedSearchResults: UnifiedSearchResults,
    searchResults: List<SearchResultItem>,
    searchBarState: SearchBarState,
    searchViewModel: SearchViewModel,
    onRequestVoiceSearch: () -> Unit,
) {
    Box(modifier = modifier) {
        if (ui.showCategoryFilterMenu) {
            BackHandler {
                ui.showCategoryFilterMenu = false
                controller.focusEpgZone(EpgFocusZone.GRID_FILTER)
            }
        }

        GuideGroupFilterMenu(
            expanded = ui.showCategoryFilterMenu,
            channelGroups = channelGroups,
            selectedGroups = deps.guideFilter.selectedGroups,
            expandedCategories = ui.categoryMenuExpandedCategories,
            groupChannelCounts = groupChannelCounts,
            focusedIndex = ui.categoryMenuFocusIndex,
            onFocusedIndexChange = { ui.categoryMenuFocusIndex = it },
            onDismiss = {
                ui.showCategoryFilterMenu = false
                controller.focusEpgZone(EpgFocusZone.GRID_FILTER)
            },
            onToggle = controller::handleCategoryFilterMenuToggle
        )

        if (ui.showGuideGroupPicker && channelGroups.isNotEmpty()) {
            BackHandler {
                ui.showGuideGroupPicker = false
                ui.initialGuidePickerDismissed = true
            }
            GuideGroupPickerDialog(
                channelGroups = channelGroups,
                initialSelection = deps.guideFilter.selectedGroups,
                groupChannelCounts = groupChannelCounts,
                onDismiss = {
                    ui.showGuideGroupPicker = false
                    ui.initialGuidePickerDismissed = true
                },
                onConfirm = { groups ->
                    ui.showGuideGroupPicker = false
                    ui.initialGuidePickerDismissed = true
                    deps.viewModel.saveGuideChannelGroups(groups, markConfigured = true)
                    ui.focusChannelIndex = 0
                }
            )
        }

        if (ui.showFavoritePicker && favoriteGroups.isNotEmpty()) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { ui.showFavoritePicker = false },
                title = { androidx.tv.material3.Text("Add to group") },
                text = {
                    Column {
                        LazyColumn {
                            items(favoriteGroups.size) { idx ->
                                val group = favoriteGroups[idx]
                                GlowFocusButton(onClick = {
                                    deps.focusedChannel?.let {
                                        deps.viewModel.addChannelToFavoriteGroup(it.id, group.id)
                                        ui.showFavoritePicker = false
                                    }
                                }) { androidx.tv.material3.Text(group.name) }
                            }
                        }
                    }
                },
                confirmButton = {
                    GlowFocusButton(onClick = { ui.showFavoritePicker = false }) {
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
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                androidx.tv.material3.Text(
                    text = message,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
            }
        }

        if (ui.showCreateGroup) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { ui.showCreateGroup = false },
                title = { androidx.tv.material3.Text("New favorite group") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = ui.newGroupName,
                        onValueChange = { ui.newGroupName = it },
                        label = { androidx.tv.material3.Text("Group name") }
                    )
                },
                confirmButton = {
                    GlowFocusButton(onClick = {
                        if (ui.newGroupName.isNotBlank()) {
                            deps.viewModel.createFavoriteGroup(ui.newGroupName.trim())
                            ui.newGroupName = ""
                            ui.showCreateGroup = false
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
                precheck = check,
                onQualitySelected = recordingViewModel::updatePrecheckQuality,
                onConfirm = { recordingViewModel.confirmImmediateRecording(context) },
                onDismiss = { recordingViewModel.dismissPrecheck() }
            )
        }

        if (ui.showSearchOverlay) {
            BackHandler {
                ui.showSearchOverlay = false
                searchViewModel.clearQuery()
                ui.focusZone = EpgFocusZone.GRID
            }
            SearchOverlay(
                query = searchQuery,
                unifiedResults = unifiedSearchResults,
                flatResults = searchResults,
                searchBarState = searchBarState,
                onQueryChange = searchViewModel::updateQuery,
                onClear = searchViewModel::clearQuery,
                onDismiss = {
                    ui.showSearchOverlay = false
                    searchViewModel.clearQuery()
                    ui.focusZone = EpgFocusZone.GRID
                },
                onMicClick = {
                    if (searchBarState == SearchBarState.LISTENING) {
                        searchViewModel.stopVoiceSearch()
                    } else {
                        onRequestVoiceSearch()
                    }
                },
                onResultSelected = controller::handleSearchResult,
                onSuggestionSelected = { term ->
                    searchViewModel.applyTrendingOrRecent(term)
                },
                onClearHistory = searchViewModel::clearRecentHistory
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeEpgContinueWatchingRow(
    continueWatchingItems: List<ContinueWatchingItem>,
    focusedContinueIndex: Int,
    continueWatchingFocused: Boolean,
    onContinueSelect: (ContinueWatchingItem) -> Unit,
    continueWatchingFocusRequester: FocusRequester,
    topNavFocusRequester: FocusRequester,
    previewFocusRequester: FocusRequester,
    gridFilterFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    showGroupFilter: Boolean,
    showPreviewSection: Boolean,
    onContinueWatchingKey: (KeyEvent) -> Boolean,
    onFocused: () -> Unit
) {
    if (continueWatchingItems.isEmpty()) return

    ContinueWatchingRow(
        items = continueWatchingItems,
        focusedIndex = focusedContinueIndex,
        rowFocused = continueWatchingFocused,
        onSelect = onContinueSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(continueWatchingFocusRequester)
            .focusProperties {
                up = topNavFocusRequester
                down = when {
                    showPreviewSection -> previewFocusRequester
                    showGroupFilter -> gridFilterFocusRequester
                    else -> gridFocusRequester
                }
            }
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { continueWatchingFocused && onContinueWatchingKey(it) }
    )
}

@Composable
internal fun HomeEpgPreviewSection(
    channel: Channel?,
    program: Program?,
    nextProgram: Program? = null,
    player: androidx.media3.exoplayer.ExoPlayer?,
    streamStatus: StreamPlaybackStatus?,
    detailActionIndex: Int,
    previewFocused: Boolean,
    attachSurface: Boolean,
    isFavorite: Boolean,
    primaryActionLabel: String = "Watch Live",
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    previewFocusRequester: FocusRequester,
    continueWatchingFocusRequester: FocusRequester,
    topNavFocusRequester: FocusRequester,
    gridFilterFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    showGroupFilter: Boolean,
    hasContinueWatching: Boolean,
    onFocused: () -> Unit
) {
    val ch = channel ?: return

    EpgPreviewSection(
        channel = ch,
        program = program,
        nextProgram = nextProgram,
        player = player,
        streamStatus = streamStatus,
        detailActionFocused = detailActionIndex,
        isFavorite = isFavorite,
        previewFocused = previewFocused,
        attachSurface = attachSurface,
        primaryActionLabel = primaryActionLabel,
        onWatch = onWatch,
        onFavorite = onFavorite,
        onRecord = onRecord,
        modifier = Modifier
            .focusRequester(previewFocusRequester)
            .focusProperties {
                up = if (hasContinueWatching) continueWatchingFocusRequester else topNavFocusRequester
                down = when {
                    showGroupFilter -> gridFilterFocusRequester
                    else -> gridFocusRequester
                }
            }
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeEpgChannelList(
    modifier: Modifier = Modifier,
    gridFocusRequester: FocusRequester,
    onGridKey: (KeyEvent) -> Boolean,
    gridFocused: Boolean,
    displayChannelsEmpty: Boolean,
    hScroll: androidx.compose.foundation.ScrollState,
    windowStart: Long,
    windowDurationMs: Long,
    guideFilter: GuideChannelFilter,
    channelGroups: List<String>,
    gridFilterFocused: Boolean,
    gridFilterFocusRequester: FocusRequester,
    previewFocusRequester: FocusRequester,
    continueWatchingFocusRequester: FocusRequester,
    topNavFocusRequester: FocusRequester,
    showPreviewSection: Boolean,
    hasContinueWatching: Boolean,
    showGroupFilter: Boolean,
    onOpenCategoryFilter: () -> Unit,
    onGridFilterKey: (KeyEvent) -> Boolean,
    onGridFocused: () -> Unit,
    onGridFilterFocused: () -> Unit,
    listState: LazyListState,
    displayChannels: List<Channel>,
    filteredEmptyMessage: String? = null,
    programsForChannel: (Channel) -> List<Program>,
    channelScanStatuses: Map<Long, ChannelScanSnapshot>,
    focusChannelIndex: Int,
    focusProgramIndex: Int,
    focusOnChannelColumn: Boolean,
    confirmedProgramId: Long?,
    scheduled: List<ScheduledRecordingEntity>,
    timelineWidth: Dp,
    scrolledAwayFromLive: Boolean,
    onJumpToLive: () -> Unit,
    onChannelClick: (Int, Channel) -> Unit = { _, _ -> },
    onProgramClick: (Int, Int, Program) -> Unit = { _, _, _ -> },
    replayStateFor: (Channel, Program) -> EpgProgramReplayState = { _, _ ->
        EpgProgramReplayState(0, com.grid.tv.ui.component.ProgramTimeState.FUTURE, com.grid.tv.domain.epg.EpgProgramAction.NONE, false, null)
    }
) {
    val gridNow = rememberEpgNowMillis(EpgNowTicker.GRID_INTERVAL_MS)
    val touchGesturesEnabled = LocalDeviceFormFactor.current.enableTouchGestures
    val gridFilterUpTarget = when {
        showPreviewSection -> previewFocusRequester
        hasContinueWatching -> continueWatchingFocusRequester
        else -> topNavFocusRequester
    }
    var filterChipHasFocus by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(EpgLayout.ChannelColumnWidth)
                        .height(EpgLayout.TimelineHeaderHeight)
                        .background(EpgColors.ChannelColumnBg)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.tv.material3.Text(
                            text = formatEpgDay(gridNow),
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 12.sp
                        )
                        if (showGroupFilter) {
                            EpgCategoryFilterChip(
                                label = guideFilter.label,
                                active = guideFilter.isActive,
                                focused = gridFilterFocused || filterChipHasFocus,
                                headerStyle = true,
                                onClick = onOpenCategoryFilter,
                                modifier = Modifier
                                    .focusRequester(gridFilterFocusRequester)
                                    .focusProperties {
                                        up = gridFilterUpTarget
                                        down = if (!displayChannelsEmpty) {
                                            gridFocusRequester
                                        } else {
                                            FocusRequester.Cancel
                                        }
                                    }
                                    .onFocusChanged {
                                        filterChipHasFocus = it.isFocused
                                        if (it.isFocused) onGridFilterFocused()
                                    }
                                    .onPreviewKeyEvent { filterChipHasFocus && onGridFilterKey(it) }
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll, enabled = touchGesturesEnabled)
                ) {
                    EpgTimelineHeader(
                        windowStart = windowStart,
                        windowDurationMs = windowDurationMs
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(gridFocusRequester)
                    .focusProperties {
                        canFocus = !displayChannelsEmpty && gridFocused
                        up = if (focusChannelIndex == 0) {
                            if (showGroupFilter) gridFilterFocusRequester else gridFilterUpTarget
                        } else {
                            FocusRequester.Cancel
                        }
                        down = FocusRequester.Cancel
                    }
                    .then(if (!displayChannelsEmpty) Modifier.focusable() else Modifier)
                    .onFocusChanged { if (it.isFocused) onGridFocused() }
                    .onPreviewKeyEvent { gridFocused && onGridKey(it) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .heightIn(min = EpgLayout.GuideChannelListMinHeight)
                ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayChannels.size, key = { index -> displayChannels[index].id }) { index ->
                                val channel = displayChannels[index]
                                val programs = programsForChannel(channel)
                                val isActiveRow = gridFocused && index == focusChannelIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = EpgLayout.RowHeight)
                                        .height(EpgLayout.RowHeight)
                                        .focusProperties { canFocus = false }
                                ) {
                                    EpgChannelCell(
                                        channel = channel,
                                        isFocused = isActiveRow && focusOnChannelColumn,
                                        isRowActive = isActiveRow,
                                        showBottomSeparator = index < displayChannels.lastIndex,
                                        scanStatus = channelScanStatuses[channel.id]?.status,
                                        lastCheckedLabel = formatLastChecked(
                                            channelScanStatuses[channel.id]?.lastCheckedAt
                                        ),
                                        onClick = if (touchGesturesEnabled) {
                                            { onChannelClick(index, channel) }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.width(EpgLayout.ChannelColumnWidth)
                                    )
                                    EpgChannelTimelineRow(
                                        channel = channel,
                                        programs = programs,
                                        windowStart = windowStart,
                                        windowDurationMs = windowDurationMs,
                                        now = gridNow,
                                        channelIndex = index,
                                        focusChannelIndex = focusChannelIndex,
                                        focusProgramIndex = focusProgramIndex,
                                        focusOnChannelColumn = focusOnChannelColumn,
                                        gridFocused = gridFocused,
                                        isRowFocused = gridFocused && index == focusChannelIndex,
                                        confirmedProgramId = confirmedProgramId,
                                        scheduled = scheduled,
                                        hScrollModifier = Modifier.horizontalScroll(
                                            hScroll,
                                            enabled = touchGesturesEnabled
                                        ),
                                        timelineWidth = timelineWidth,
                                        touchGesturesEnabled = touchGesturesEnabled,
                                        onProgramClick = onProgramClick,
                                        replayStateFor = replayStateFor
                                    )
                                }
                            }
                        }

                        if (displayChannels.isEmpty() && filteredEmptyMessage != null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.tv.material3.Text(
                                    text = filteredEmptyMessage,
                                    color = EpgColors.TextSecondary,
                                    fontFamily = DmSansFamily,
                                    fontSize = 14.sp
                                )
                            }
                        }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = EpgLayout.ChannelColumnWidth)
                    ) {
                        EpgNowLine(
                            windowStart = windowStart,
                            windowDurationMs = windowDurationMs,
                            scrollOffsetPx = hScroll.value,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                EpgJumpToLiveButton(
                    visible = scrolledAwayFromLive,
                    onClick = onJumpToLive,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun EpgChannelTimelineRow(
    channel: Channel,
    programs: List<Program>,
    windowStart: Long,
    windowDurationMs: Long,
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
    timelineWidth: Dp,
    touchGesturesEnabled: Boolean = false,
    onProgramClick: (Int, Int, Program) -> Unit = { _, _, _ -> },
    replayStateFor: (Channel, Program) -> EpgProgramReplayState
) {
    val rowActive = gridFocused && channelIndex == focusChannelIndex
    val rowBg by animateColorAsState(
        targetValue = when {
            rowActive && !focusOnChannelColumn -> EpgColors.ChannelRowFocusBg.copy(alpha = 0.32f)
            isRowFocused -> EpgColors.ChannelRowFocusBg.copy(alpha = 0.2f)
            else -> EpgColors.GridBg
        },
        animationSpec = tween(durationMillis = 120),
        label = "timelineRowBg"
    )
    Box(
        modifier = hScrollModifier
            .width(timelineWidth)
            .height(EpgLayout.RowHeight)
            .background(rowBg)
    ) {
        if (programs.isEmpty()) {
            val isFocused = gridFocused &&
                channelIndex == focusChannelIndex &&
                !focusOnChannelColumn &&
                focusProgramIndex == 0
            EpgNoInformationCell(
                width = timelineWidth - EpgLayout.CellGap,
                isFocused = isFocused,
                windowDurationMs = windowDurationMs,
                modifier = Modifier.offset(x = 0.dp)
            )
        } else {
            programs.forEachIndexed { programIndex, program ->
            val width = EpgLayout.widthForProgram(
                start = program.startTime,
                end = program.endTime,
                windowDurationMs = windowDurationMs
            ) - EpgLayout.CellGap
            val offset = EpgLayout.offsetForTime(program.startTime, windowStart, windowDurationMs)
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
                append(program.title)
            }
            val replayState = replayStateFor(channel, program)

            EpgProgramCell(
                program = program.copy(title = title),
                width = width,
                now = now,
                isFocused = isFocused,
                isSelected = isSelected,
                windowDurationMs = windowDurationMs,
                canReplay = replayState.canReplay,
                isFuture = replayState.timeState == com.grid.tv.ui.component.ProgramTimeState.FUTURE,
                onClick = if (touchGesturesEnabled) {
                    { onProgramClick(channelIndex, programIndex, program) }
                } else {
                    null
                },
                modifier = Modifier.offset(x = offset)
            )
        }
        }
    }
}
