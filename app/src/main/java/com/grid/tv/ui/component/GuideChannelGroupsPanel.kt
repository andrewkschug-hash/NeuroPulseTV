package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

/** TiviMate-style column-2 channel group tree beside the icon rail. */
val GuideChannelGroupsPanelWidth = 260.dp

@Composable
fun GuideChannelGroupsPanel(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    expandedCategories: Set<Int>,
    groupChannelCounts: Map<String, Int>,
    focusedIndex: Int,
    panelFocused: Boolean,
    panelFocusRequester: FocusRequester,
    onFocusedIndexChange: (Int) -> Unit,
    onPreviewKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    onFilterChange: (GuideChannelFilter) -> Unit,
    onToggleCategory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channelGroups.isEmpty()) return

    val categories = remember(channelGroups, groupChannelCounts) {
        buildGuideGroupCategories(channelGroups, groupChannelCounts)
    }
    val visibleRows = remember(categories, expandedCategories) {
        buildVisibleGuideGroupRows(categories, expandedCategories)
    }
    val listState = rememberLazyListState()
    val rowFocusRequesters = remember(visibleRows.size) {
        List(visibleRows.size) { FocusRequester() }
    }

    LaunchedEffect(panelFocused, visibleRows.size) {
        if (panelFocused && visibleRows.isNotEmpty()) {
            val index = focusedIndex.coerceIn(0, visibleRows.lastIndex)
            onFocusedIndexChange(index)
            rowFocusRequesters.getOrNull(index)?.requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(focusedIndex) {
        if (visibleRows.isNotEmpty()) {
            listState.scrollToItem(focusedIndex.coerceIn(0, visibleRows.lastIndex))
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
                key = { _, row ->
                    when (row) {
                        GuideGroupVisibleRow.AllChannels -> "all"
                        is GuideGroupVisibleRow.Category -> "cat_${row.categoryIndex}"
                        is GuideGroupVisibleRow.SelectAll -> "all_${row.categoryIndex}"
                        is GuideGroupVisibleRow.Group -> "grp_${row.fullName}"
                    }
                }
            ) { index, row ->
                val selected = isGuideGroupRowSelected(row, selectedGroups)
                when (row) {
                    GuideGroupVisibleRow.AllChannels -> GuideGroupAllChannelsRow(
                        checked = selected,
                        onClick = { onFilterChange(GuideChannelFilter.All) },
                        focusRequester = rowFocusRequesters[index],
                        onFocused = { onFocusedIndexChange(index) }
                    )
                    is GuideGroupVisibleRow.Category -> GuideGroupCategoryRow(
                        displayName = row.displayName,
                        channelCount = row.channelCount,
                        subGroupCount = row.subGroupCount,
                        expanded = row.expanded,
                        onClick = { onToggleCategory(index) },
                        focusRequester = rowFocusRequesters[index],
                        onFocused = { onFocusedIndexChange(index) }
                    )
                    is GuideGroupVisibleRow.SelectAll -> GuideGroupSelectAllRow(
                        displayName = row.displayName,
                        childCount = row.groupNames.size,
                        checked = selected,
                        onClick = {
                            onFilterChange(
                                GuideChannelFilter(toggleSelectAllGroups(row.groupNames, selectedGroups))
                            )
                        },
                        focusRequester = rowFocusRequesters[index],
                        onFocused = { onFocusedIndexChange(index) }
                    )
                    is GuideGroupVisibleRow.Group -> GuideGroupChildRow(
                        label = com.grid.tv.domain.model.ChannelGroupIdentity.displayLabel(row.fullName),
                        checked = selected,
                        onClick = {
                            onFilterChange(GuideChannelFilter(setOf(row.fullName)))
                        },
                        focusRequester = rowFocusRequesters[index],
                        onFocused = { onFocusedIndexChange(index) }
                    )
                }
            }
        }
    }
}
