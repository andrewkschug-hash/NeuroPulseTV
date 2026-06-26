package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.domain.model.ChannelGroupIdentity
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

/** TiviMate-style column-2 channel group list beside the icon rail. */
val GuideChannelGroupsPanelWidth = 260.dp

@Composable
fun GuideChannelGroupsPanel(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    groupChannelCounts: Map<String, Int>,
    focusedIndex: Int,
    panelFocused: Boolean,
    panelFocusRequester: FocusRequester,
    onFocusedIndexChange: (Int) -> Unit,
    onPreviewKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    onFilterChange: (GuideChannelFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channelGroups.isEmpty()) return

    val visibleRows = remember(channelGroups) {
        buildFlatProviderVisibleRows(channelGroups)
    }
    val listState = rememberLazyListState()
    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    fun focusRequesterFor(row: GuideGroupVisibleRow): FocusRequester =
        rowFocusRequesters.getOrPut(guideGroupVisibleRowKey(row)) { FocusRequester() }

    LaunchedEffect(panelFocused, focusedIndex, visibleRows) {
        if (!panelFocused || visibleRows.isEmpty()) return@LaunchedEffect
        val index = focusedIndex.coerceIn(0, visibleRows.lastIndex)
        focusRequesterFor(visibleRows[index]).requestFocusSafelyAfterLayout()
    }

    LaunchedEffect(focusedIndex, visibleRows) {
        if (visibleRows.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, visibleRows.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .width(GuideChannelGroupsPanelWidth)
            .fillMaxHeight()
            .background(EpgColors.SidebarPanelBg)
            .border(
                width = 1.dp,
                color = EpgColors.BorderSubtle,
                shape = RoundedCornerShape(0.dp)
            )
            .focusRequester(panelFocusRequester)
            .onPreviewKeyEvent(onPreviewKey)
    ) {
        Text(
            text = "Channel groups",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                visibleRows,
                key = { _, row -> guideGroupVisibleRowKey(row) }
            ) { index, row ->
                val selected = isGuideGroupRowSelected(row, selectedGroups)
                val rowFocusRequester = focusRequesterFor(row)
                when (row) {
                    GuideGroupVisibleRow.AllChannels -> GuideGroupAllChannelsRow(
                        checked = selected,
                        onClick = { onFilterChange(GuideChannelFilter.All) },
                        focusRequester = rowFocusRequester,
                        onFocused = { onFocusedIndexChange(index) }
                    )
                    is GuideGroupVisibleRow.Group -> {
                        val count = groupChannelCounts[row.fullName] ?: 0
                        val label = buildString {
                            append(ChannelGroupIdentity.displayLabel(row.fullName))
                            if (count > 0) append(" ($count)")
                        }
                        GuideGroupChildRow(
                            label = label,
                            checked = selected,
                            onClick = {
                                onFilterChange(GuideChannelFilter(setOf(row.fullName)))
                            },
                            focusRequester = rowFocusRequester,
                            onFocused = { onFocusedIndexChange(index) }
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}
