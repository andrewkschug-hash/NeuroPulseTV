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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.player.LivePlayerManager
import com.neuropulse.tv.ui.component.EpgChannelCell
import com.neuropulse.tv.ui.component.EpgDetailPanel
import com.neuropulse.tv.ui.component.EpgLayout
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgNowLine
import com.neuropulse.tv.ui.component.EpgProgramCell
import com.neuropulse.tv.ui.component.EpgTimeJumpPills
import com.neuropulse.tv.ui.component.EpgTimelineHeader
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.MiniNowPlayingPlayer
import com.neuropulse.tv.ui.component.MiniPlayerLayout
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import kotlinx.coroutines.launch

private enum class EpgFocusZone { GRID, TOP_NAV, MINI_PLAYER }

@Composable
fun HomeEpgScreen(
    onWatchChannel: (Long) -> Unit,
    onNavigateSearch: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    viewModel: HomeEpgViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val epgWindow by viewModel.epgPrograms.collectAsStateWithLifecycle()
    val epgLoading by viewModel.epgLoading.collectAsStateWithLifecycle()
    val windowStart by viewModel.windowStart.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
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

    LaunchedEffect(channels, continueWatching) {
        if (channels.isNotEmpty()) viewModel.tuneLastWatched(context)
    }

    val liveChannelId = livePlayerManager.activeChannelId()
    val watchingChannel = channels.find { it.id == liveChannelId }
        ?: continueWatching.firstOrNull()
        ?: channels.firstOrNull()
    val watchingProgram = watchingChannel?.epgId?.let { epgId ->
        epgWindow.firstOrNull { it.channelEpgId == epgId && now in it.startTime..it.endTime }
    }

    var selectedTab by remember { mutableStateOf(EpgNavTab.Home) }
    var focusZone by remember { mutableStateOf(EpgFocusZone.GRID) }
    var focusedNavTabIndex by remember { mutableIntStateOf(0) }
    var focusChannelIndex by remember { mutableIntStateOf(0) }
    var focusProgramIndex by remember { mutableIntStateOf(0) }
    var focusOnChannelColumn by remember { mutableStateOf(false) }
    var detailExpanded by remember { mutableStateOf(false) }
    var detailActionIndex by remember { mutableIntStateOf(0) }
    var confirmedProgramId by remember { mutableStateOf<Long?>(null) }

    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gridFocusRequester = remember { FocusRequester() }
    val miniFocusRequester = remember { FocusRequester() }
    val topNavFocusRequester = remember { FocusRequester() }
    val windowDurationMs = viewModel.windowDurationMs
    val timelineWidth = EpgLayout.timelineWidthMs(windowDurationMs)
    val livePlayer = livePlayerManager.activePlayer()

    LaunchedEffect(windowStart) {
        val nowOffset = (now - windowStart) * EpgLayout.dpPerMs()
        val target = (nowOffset - 400f).coerceAtLeast(0f)
        hScroll.scrollTo(target.toInt())
    }

    LaunchedEffect(channels.size, focusZone) {
        when (focusZone) {
            EpgFocusZone.GRID -> if (channels.isNotEmpty()) gridFocusRequester.requestFocus()
            EpgFocusZone.MINI_PLAYER -> miniFocusRequester.requestFocus()
            EpgFocusZone.TOP_NAV -> topNavFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
    }

    val focusedChannel = channels.getOrNull(focusChannelIndex)
    val channelPrograms = remember(focusedChannel, epgWindow) {
        focusedChannel?.epgId?.let { epgId ->
            epgWindow.filter { it.channelEpgId == epgId }.sortedBy { it.startTime }
        } ?: emptyList()
    }
    val focusedProgram = if (focusOnChannelColumn) {
        channelPrograms.firstOrNull { now in it.startTime..it.endTime }
            ?: channelPrograms.firstOrNull()
    } else {
        channelPrograms.getOrNull(focusProgramIndex)
    }

    fun programsForChannel(channel: Channel): List<Program> =
        channel.epgId?.let { epgId ->
            epgWindow.filter { it.channelEpgId == epgId }.sortedBy { it.startTime }
        } ?: emptyList()

    fun clampProgramIndex(channelIdx: Int, programIdx: Int): Int {
        val progs = channels.getOrNull(channelIdx)?.let { programsForChannel(it) } ?: emptyList()
        return programIdx.coerceIn(0, (progs.size - 1).coerceAtLeast(0))
    }

    fun scrollToProgram(program: Program?) {
        program ?: return
        val offset = ((program.startTime - windowStart) * EpgLayout.dpPerMs() - 200f).coerceAtLeast(0f)
        scope.launch { hScroll.animateScrollTo(offset.toInt()) }
    }

    fun activateNavTab(tab: EpgNavTab) {
        selectedTab = tab
        when (tab) {
            EpgNavTab.Home -> Unit
            EpgNavTab.Search -> onNavigateSearch()
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Settings -> onNavigateSettings()
            EpgNavTab.Profile -> onNavigateProfile()
        }
    }

    fun handleTopNavKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val tabs = EpgNavTab.entries
        return when (event.key) {
            Key.DirectionLeft -> {
                focusedNavTabIndex = (focusedNavTabIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                if (focusedNavTabIndex < tabs.lastIndex) {
                    focusedNavTabIndex += 1
                } else {
                    focusZone = EpgFocusZone.MINI_PLAYER
                }
                true
            }
            Key.DirectionDown -> {
                focusZone = EpgFocusZone.MINI_PLAYER
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                activateNavTab(tabs[focusedNavTabIndex])
                true
            }
            else -> false
        }
    }

    fun handleMiniPlayerKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionDown -> {
                focusZone = EpgFocusZone.GRID
                true
            }
            Key.DirectionLeft -> {
                focusZone = EpgFocusZone.TOP_NAV
                focusedNavTabIndex = EpgNavTab.entries.lastIndex
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                watchingChannel?.let { onWatchChannel(it.id) }
                true
            }
            Key.Back, Key.Escape -> {
                focusZone = EpgFocusZone.GRID
                true
            }
            else -> false
        }
    }

    fun handleGridKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (detailExpanded) {
            return when (event.key) {
                Key.DirectionLeft -> {
                    detailActionIndex = (detailActionIndex - 1).coerceAtLeast(0)
                    true
                }
                Key.DirectionRight -> {
                    detailActionIndex = (detailActionIndex + 1).coerceAtMost(2)
                    true
                }
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    when (detailActionIndex) {
                        0 -> focusedChannel?.let { onWatchChannel(it.id) }
                        1 -> focusedProgram?.let { prog ->
                            val ch = focusedChannel ?: return@let
                            if (prog.startTime <= now) {
                                val duration = (prog.endTime - now).coerceAtLeast(10 * 60 * 1000)
                                recordingViewModel.startImmediateRecording(context, ch, prog.title, duration)
                            } else {
                                recordingViewModel.scheduleProgram(ch, prog)
                            }
                        }
                        2 -> Unit
                    }
                    true
                }
                Key.Back, Key.Escape -> {
                    detailExpanded = false
                    true
                }
                else -> false
            }
        }

        return when (event.key) {
            Key.DirectionDown -> {
                if (focusChannelIndex < channels.lastIndex) {
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
                    focusZone = EpgFocusZone.MINI_PLAYER
                }
                true
            }
            Key.DirectionRight -> {
                if (focusOnChannelColumn) {
                    focusOnChannelColumn = false
                    focusProgramIndex = 0
                } else {
                    val progs = programsForChannel(channels[focusChannelIndex])
                    if (focusProgramIndex < progs.lastIndex) {
                        focusProgramIndex += 1
                        scrollToProgram(progs[focusProgramIndex])
                    } else {
                        scope.launch { hScroll.animateScrollBy(EpgLayout.ThirtyMinWidthDp) }
                    }
                }
                detailExpanded = true
                true
            }
            Key.DirectionLeft -> {
                if (!focusOnChannelColumn && focusProgramIndex > 0) {
                    val progs = programsForChannel(channels[focusChannelIndex])
                    focusProgramIndex -= 1
                    scrollToProgram(progs[focusProgramIndex])
                } else if (!focusOnChannelColumn) {
                    focusOnChannelColumn = true
                    scope.launch { hScroll.animateScrollBy(-EpgLayout.ThirtyMinWidthDp) }
                } else {
                    scope.launch { hScroll.animateScrollBy(-EpgLayout.ThirtyMinWidthDp) }
                }
                detailExpanded = true
                true
            }
            Key.PageDown -> {
                focusChannelIndex = (focusChannelIndex + 10).coerceAtMost(channels.lastIndex)
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
                val prog = focusedProgram
                if (prog != null && confirmedProgramId == prog.id) {
                    focusedChannel?.let { onWatchChannel(it.id) }
                } else if (prog != null) {
                    confirmedProgramId = prog.id
                    detailExpanded = true
                } else {
                    focusedChannel?.let { onWatchChannel(it.id) }
                }
                true
            }
            Key.Back, Key.Escape -> {
                if (detailExpanded || confirmedProgramId != null) {
                    detailExpanded = false
                    confirmedProgramId = null
                    true
                } else false
            }
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                watchingChannel = watchingChannel,
                currentProgram = watchingProgram,
                now = now,
                selectedTab = selectedTab,
                focusedNavTabIndex = focusedNavTabIndex,
                navFocused = focusZone == EpgFocusZone.TOP_NAV,
                onTabSelected = { tab ->
                    focusZone = EpgFocusZone.TOP_NAV
                    focusedNavTabIndex = EpgNavTab.entries.indexOf(tab)
                    activateNavTab(tab)
                },
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == EpgFocusZone.TOP_NAV) handleTopNavKey(it) else false
                    }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(gridFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent {
                            if (focusZone == EpgFocusZone.GRID) handleGridKey(it) else false
                        }
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(EpgLayout.ChannelColumnWidth)
                                .height(EpgLayout.TimelineHeaderHeight)
                                .background(EpgColors.ChannelColumnBg)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(hScroll)
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
                            items(channels.size) { index ->
                                val channel = channels[index]
                                val programs = programsForChannel(channel)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    EpgChannelCell(
                                        channel = channel,
                                        isFocused = focusZone == EpgFocusZone.GRID &&
                                            focusOnChannelColumn && index == focusChannelIndex,
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
                                        confirmedProgramId = confirmedProgramId,
                                        scheduled = scheduled,
                                        hScrollModifier = Modifier.horizontalScroll(hScroll),
                                        timelineWidth = timelineWidth
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .padding(start = EpgLayout.ChannelColumnWidth)
                                .horizontalScroll(hScroll)
                                .fillMaxHeight()
                        ) {
                            EpgNowLine(
                                windowStart = windowStart,
                                windowDurationMs = windowDurationMs,
                                now = now,
                                modifier = Modifier
                                    .width(timelineWidth)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                EpgTimeJumpPills(
                    loading = epgLoading,
                    onPrev2h = { viewModel.loadPrevBlock() },
                    onLive = { viewModel.snapToLive() },
                    onNext2h = { viewModel.loadNextBlock() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = EpgLayout.ChannelColumnWidth + 12.dp)
                )
            }

            EpgDetailPanel(
                channel = focusedChannel,
                program = focusedProgram,
                now = now,
                detailActionFocused = detailActionIndex,
                onActionFocusChange = { detailActionIndex = it },
                onWatch = { focusedChannel?.let { onWatchChannel(it.id) } },
                onRecord = {
                    val prog = focusedProgram ?: return@EpgDetailPanel
                    val ch = focusedChannel ?: return@EpgDetailPanel
                    if (prog.startTime <= now) {
                        val duration = (prog.endTime - now).coerceAtLeast(10 * 60 * 1000)
                        recordingViewModel.startImmediateRecording(context, ch, prog.title, duration)
                    } else {
                        recordingViewModel.scheduleProgram(ch, prog)
                    }
                },
                onMoreInfo = { detailExpanded = true },
                visible = detailExpanded || focusedProgram != null,
                modifier = Modifier.fillMaxWidth()
            )
        }

        MiniNowPlayingPlayer(
            channel = watchingChannel,
            program = watchingProgram,
            player = livePlayer,
            isFocused = focusZone == EpgFocusZone.MINI_PLAYER,
            onFocus = { focusZone = EpgFocusZone.MINI_PLAYER },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = MiniPlayerLayout.Margin, end = MiniPlayerLayout.Margin)
                .zIndex(10f)
                .focusRequester(miniFocusRequester)
                .onPreviewKeyEvent {
                    if (focusZone == EpgFocusZone.MINI_PLAYER) handleMiniPlayerKey(it) else false
                }
        )
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
    confirmedProgramId: Long?,
    scheduled: List<ScheduledRecordingEntity>,
    hScrollModifier: Modifier,
    timelineWidth: Dp
) {
    Box(
        modifier = hScrollModifier
            .width(timelineWidth)
            .height(EpgLayout.RowHeight)
            .background(EpgColors.GridBg)
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
