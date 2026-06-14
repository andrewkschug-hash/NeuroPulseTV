package com.neuropulse.tv.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.feature.epg.ChannelCategoryFilter
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.ChannelScanSnapshot
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.player.StreamPlaybackStatus
import com.neuropulse.tv.ui.component.ContinueWatchingRow
import com.neuropulse.tv.ui.component.EpgCategoryFilterChip
import com.neuropulse.tv.ui.component.EpgChannelCell
import com.neuropulse.tv.ui.component.EpgDetailPanel
import com.neuropulse.tv.ui.component.EpgJumpToLiveButton
import com.neuropulse.tv.ui.component.EpgLayout
import com.neuropulse.tv.ui.component.EpgProgramCell
import com.neuropulse.tv.ui.component.EpgTimelineHeader
import com.neuropulse.tv.ui.component.MiniPlayerOverlay
import com.neuropulse.tv.ui.component.formatEpgDay
import com.neuropulse.tv.ui.component.formatLastChecked
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

@Composable
internal fun HomeEpgContinueWatchingRow(
    continueWatchingItems: List<ContinueWatchingItem>,
    focusedContinueIndex: Int,
    continueWatchingFocused: Boolean,
    onContinueSelect: (ContinueWatchingItem) -> Unit,
    continueWatchingFocusRequester: FocusRequester,
    onContinueWatchingKey: (KeyEvent) -> Boolean,
    miniPlayerChannel: Pair<Channel, String>?,
    miniPlayerFocused: Boolean,
    miniPlayerIdleShrunk: Boolean,
    miniPlayerAudioEnabled: Boolean,
    onMiniPlayerClick: (Channel) -> Unit,
    miniPlayerFocusRequester: FocusRequester,
    onMiniPlayerKey: (KeyEvent) -> Boolean
) {
    if (continueWatchingItems.isEmpty() && miniPlayerChannel == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (continueWatchingItems.isNotEmpty()) {
            ContinueWatchingRow(
                items = continueWatchingItems,
                focusedIndex = focusedContinueIndex,
                rowFocused = continueWatchingFocused,
                onSelect = onContinueSelect,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(continueWatchingFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { continueWatchingFocused && onContinueWatchingKey(it) }
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        HomeEpgMiniPlayer(
            miniPlayerChannel = miniPlayerChannel,
            miniPlayerFocused = miniPlayerFocused,
            miniPlayerIdleShrunk = miniPlayerIdleShrunk,
            miniPlayerAudioEnabled = miniPlayerAudioEnabled,
            onMiniPlayerClick = onMiniPlayerClick,
            miniPlayerFocusRequester = miniPlayerFocusRequester,
            onMiniPlayerKey = onMiniPlayerKey
        )
    }
}

@Composable
internal fun HomeEpgMiniPlayer(
    miniPlayerChannel: Pair<Channel, String>?,
    miniPlayerFocused: Boolean,
    miniPlayerIdleShrunk: Boolean,
    miniPlayerAudioEnabled: Boolean,
    onMiniPlayerClick: (Channel) -> Unit,
    miniPlayerFocusRequester: FocusRequester,
    onMiniPlayerKey: (KeyEvent) -> Boolean
) {
    AnimatedVisibility(
        visible = miniPlayerChannel != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally()
    ) {
        miniPlayerChannel?.let { (playedChannel, streamUrl) ->
            MiniPlayerOverlay(
                channel = playedChannel,
                streamUrl = streamUrl,
                isFocused = miniPlayerFocused,
                isIdleShrunk = miniPlayerIdleShrunk,
                miniAudioEnabled = miniPlayerAudioEnabled,
                onClick = { onMiniPlayerClick(playedChannel) },
                modifier = Modifier
                    .padding(
                        top = 8.dp,
                        end = 16.dp,
                        bottom = EpgLayout.GuideHeaderBottomPadding
                    )
                    .focusRequester(miniPlayerFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { miniPlayerFocused && onMiniPlayerKey(it) }
            )
        }
    }
}

@Composable
internal fun HomeEpgChannelList(
    modifier: Modifier = Modifier,
    gridFocusRequester: FocusRequester,
    onGridKey: (KeyEvent) -> Boolean,
    gridFocused: Boolean,
    hScroll: androidx.compose.foundation.ScrollState,
    now: Long,
    windowStart: Long,
    windowDurationMs: Long,
    categoryFilter: ChannelCategoryFilter,
    channelGroups: List<String>,
    gridFilterFocused: Boolean,
    gridFilterFocusRequester: FocusRequester,
    onOpenCategoryFilter: () -> Unit,
    onGridFilterKey: (KeyEvent) -> Boolean,
    listState: LazyListState,
    displayChannels: List<Channel>,
    programsForChannel: (Channel) -> List<Program>,
    channelScanStatuses: Map<Long, ChannelScanSnapshot>,
    focusChannelIndex: Int,
    focusProgramIndex: Int,
    focusOnChannelColumn: Boolean,
    confirmedProgramId: Long?,
    scheduled: List<ScheduledRecordingEntity>,
    timelineWidth: Dp,
    scrolledAwayFromLive: Boolean,
    onJumpToLive: () -> Unit
) {
    Box(
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(gridFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { gridFocused && onGridKey(it) }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                    text = formatEpgDay(now),
                                    color = EpgColors.TextSecondary,
                                    fontFamily = DmSansFamily,
                                    fontSize = 12.sp
                                )
                                EpgCategoryFilterChip(
                                    label = categoryFilter.label,
                                    active = categoryFilter.isActive,
                                    focused = gridFilterFocused,
                                    headerStyle = true,
                                    onClick = onOpenCategoryFilter,
                                    modifier = Modifier
                                        .focusRequester(gridFilterFocusRequester)
                                        .focusProperties { canFocus = gridFilterFocused }
                                        .focusable()
                                        .onPreviewKeyEvent { gridFilterFocused && onGridFilterKey(it) }
                                )
                            }
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

                    Box(
                        modifier = Modifier
                            .height(EpgLayout.GuideChannelListHeight)
                            .fillMaxWidth()
                    ) {
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
                                        isFocused = gridFocused &&
                                            focusOnChannelColumn && index == focusChannelIndex,
                                        showBottomSeparator = index < displayChannels.lastIndex,
                                        scanStatus = channelScanStatuses[channel.id]?.status,
                                        lastCheckedLabel = formatLastChecked(
                                            channelScanStatuses[channel.id]?.lastCheckedAt,
                                            now
                                        ),
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
                                        gridFocused = gridFocused,
                                        isRowFocused = gridFocused && index == focusChannelIndex,
                                        confirmedProgramId = confirmedProgramId,
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
internal fun HomeEpgDetailBar(
    showDetailPanel: Boolean,
    channel: Channel?,
    program: Program?,
    now: Long,
    detailFocused: Boolean,
    detailActionIndex: Int,
    onDetailActionIndexChange: (Int) -> Unit,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    isFavorite: Boolean,
    streamStatus: StreamPlaybackStatus?,
    detailFocusRequester: FocusRequester,
    onDetailKey: (KeyEvent) -> Boolean
) {
    if (!showDetailPanel) return

    EpgDetailPanel(
        channel = channel,
        program = program,
        now = now,
        detailActionFocused = if (detailFocused) detailActionIndex else -1,
        onActionFocusChange = onDetailActionIndexChange,
        onWatch = onWatch,
        onFavorite = onFavorite,
        onRecord = onRecord,
        isFavorite = isFavorite,
        visible = true,
        streamStatus = streamStatus,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(detailFocusRequester)
            .focusable()
            .onPreviewKeyEvent { detailFocused && onDetailKey(it) }
    )
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
            .background(
                if (isRowFocused) EpgColors.ChannelRowFocusBg.copy(alpha = 0.35f) else EpgColors.GridBg
            )
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
