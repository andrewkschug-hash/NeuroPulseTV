package com.neuropulse.tv.ui.screen

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
import com.neuropulse.tv.ui.component.EpgJumpToLiveButton
import com.neuropulse.tv.ui.component.EpgLayout
import com.neuropulse.tv.ui.component.EpgNowLine
import com.neuropulse.tv.ui.component.EpgPreviewSection
import com.neuropulse.tv.ui.component.EpgProgramCell
import com.neuropulse.tv.ui.component.EpgTimelineHeader
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
    onContinueWatchingKey: (KeyEvent) -> Boolean
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
            .focusable()
            .onPreviewKeyEvent { continueWatchingFocused && onContinueWatchingKey(it) }
    )
}

@Composable
internal fun HomeEpgPreviewSection(
    channel: Channel?,
    program: Program?,
    upcomingPrograms: List<Program>,
    now: Long,
    player: androidx.media3.exoplayer.ExoPlayer?,
    streamStatus: StreamPlaybackStatus?,
    detailActionIndex: Int,
    previewFocused: Boolean,
    attachSurface: Boolean,
    isFavorite: Boolean,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    previewFocusRequester: FocusRequester,
    onPreviewKey: (KeyEvent) -> Boolean
) {
    val ch = channel ?: return

    EpgPreviewSection(
        channel = ch,
        program = program,
        upcomingPrograms = upcomingPrograms,
        now = now,
        player = player,
        streamStatus = streamStatus,
        detailActionFocused = detailActionIndex,
        isFavorite = isFavorite,
        previewFocused = previewFocused,
        attachSurface = attachSurface,
        onWatch = onWatch,
        onFavorite = onFavorite,
        onRecord = onRecord,
        modifier = Modifier
            .focusRequester(previewFocusRequester)
            .focusable()
            .onPreviewKeyEvent { previewFocused && onPreviewKey(it) }
    )
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
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(gridFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { gridFocused && onGridKey(it) }
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
                            .weight(1f)
                            .fillMaxWidth()
                            .heightIn(min = EpgLayout.GuideChannelListMinHeight)
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

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = EpgLayout.ChannelColumnWidth)
                        ) {
                            EpgNowLine(
                                windowStart = windowStart,
                                windowDurationMs = windowDurationMs,
                                now = now,
                                scrollOffsetPx = hScroll.value,
                                modifier = Modifier.fillMaxSize()
                            )
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
