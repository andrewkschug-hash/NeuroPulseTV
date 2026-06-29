package com.grid.tv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.guide.GroupsTrace
import com.grid.tv.feature.guide.GuideCategoryProcessor
import com.grid.tv.ui.component.GuideGroupAllChannelsRow
import com.grid.tv.ui.component.GuideGroupCategoryRow
import com.grid.tv.ui.component.GuideGroupChildRow
import com.grid.tv.ui.component.GuideGroupSelectAllRow
import com.grid.tv.ui.component.GuideGroupVisibleRow
import com.grid.tv.ui.component.areAllGroupsSelected
import com.grid.tv.ui.component.buildVisibleGuideGroupRows
import com.grid.tv.ui.component.expandedCategoriesForSelection
import com.grid.tv.ui.component.guideFilterRowAction
import com.grid.tv.ui.component.handleGuideGroupTvKeyEvent
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.toggleCategoryExpansion
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import kotlinx.coroutines.launch

@Composable
fun GuideGroupsScreen(
    organized: GuideCategoryProcessor.OrganizedGuideGroups,
    selectedGroups: Set<String>,
    hideAdult: Boolean,
    onApplyFilter: (GuideChannelFilter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = organized.flatCategories
    var expandedCategories by remember(selectedGroups) {
        mutableStateOf(expandedCategoriesForSelection(categories, selectedGroups))
    }
    val visibleRows = remember(categories, expandedCategories) {
        buildVisibleGuideGroupRows(categories, expandedCategories)
    }
    var focusedRowIndex by remember { mutableIntStateOf(0) }
    val rowFocusRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun focusRequesterFor(index: Int): FocusRequester =
        rowFocusRequesters.getOrPut(index) { FocusRequester() }

    fun requestRowFocus(index: Int) {
        if (index !in visibleRows.indices) return
        focusedRowIndex = index
        scope.launch {
            focusRequesterFor(index).requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(visibleRows.size, rowFocusRequesters.size) {
        GroupsTrace.logVisibleRows(
            visibleRowCount = visibleRows.size,
            focusRequesterCount = rowFocusRequesters.size
        )
    }

    fun activateRow(row: GuideGroupVisibleRow) {
        when (row) {
            GuideGroupVisibleRow.AllChannels -> {
                onApplyFilter(GuideChannelFilter.All)
                onBack()
            }
            GuideGroupVisibleRow.FavoriteSectionHeader -> Unit
            GuideGroupVisibleRow.FavoriteSectionEmpty -> Unit
            is GuideGroupVisibleRow.Category -> {
                expandedCategories = toggleCategoryExpansion(expandedCategories, row.categoryIndex)
            }
            is GuideGroupVisibleRow.SelectAll -> {
                guideFilterRowAction(row, selectedGroups)?.let { onApplyFilter(it) }
            }
            is GuideGroupVisibleRow.Group -> {
                onApplyFilter(GuideChannelFilter(setOf(row.fullName)))
                onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (visibleRows.isNotEmpty()) {
            focusRequesterFor(0).requestFocusSafelyAfterLayout()
            focusedRowIndex = 0
        }
    }

    LaunchedEffect(focusedRowIndex, visibleRows.size) {
        if (visibleRows.isNotEmpty()) {
            val index = focusedRowIndex.coerceIn(0, visibleRows.lastIndex)
            listState.scrollToItem(index)
        }
    }

    LaunchedEffect(visibleRows.size) {
        if (visibleRows.isNotEmpty() && focusedRowIndex > visibleRows.lastIndex) {
            focusedRowIndex = visibleRows.lastIndex
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .onPreviewKeyEvent { event ->
                if (visibleRows.isEmpty()) return@onPreviewKeyEvent false
                handleGuideGroupTvKeyEvent(
                    event = event,
                    focusedIndex = focusedRowIndex,
                    lastIndex = visibleRows.lastIndex,
                    onFocusedIndexChange = ::requestRowFocus,
                    onActivate = { activateRow(visibleRows[focusedRowIndex]) },
                    onBack = onBack
                )
            }
    ) {
        Text(
            text = "Channel Groups",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        Text(
            text = "Select groups to show in the live guide. Press Enter to choose.",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            itemsIndexed(
                visibleRows,
                key = { _, row ->
                    when (row) {
                        GuideGroupVisibleRow.AllChannels -> "all"
                        GuideGroupVisibleRow.FavoriteSectionHeader -> "fav_header"
                        GuideGroupVisibleRow.FavoriteSectionEmpty -> "fav_empty"
                        is GuideGroupVisibleRow.Category -> "cat_${row.categoryIndex}"
                        is GuideGroupVisibleRow.SelectAll -> "all_${row.categoryIndex}"
                        is GuideGroupVisibleRow.Group -> "grp_${row.fullName}"
                    }
                }
            ) { index, row ->
                when (row) {
                    GuideGroupVisibleRow.FavoriteSectionHeader -> Unit
            GuideGroupVisibleRow.FavoriteSectionEmpty -> Unit
                    GuideGroupVisibleRow.AllChannels -> GuideGroupAllChannelsRow(
                        checked = selectedGroups.isEmpty(),
                        onClick = { activateRow(row) },
                        focusRequester = focusRequesterFor(index),
                        onFocused = { focusedRowIndex = index }
                    )
                    is GuideGroupVisibleRow.Category -> GuideGroupCategoryRow(
                        displayName = row.displayName,
                        channelCount = row.channelCount,
                        subGroupCount = row.subGroupCount,
                        expanded = row.expanded,
                        onClick = { activateRow(row) },
                        focusRequester = focusRequesterFor(index),
                        onFocused = { focusedRowIndex = index }
                    )
                    is GuideGroupVisibleRow.SelectAll -> GuideGroupSelectAllRow(
                        displayName = row.displayName,
                        childCount = row.groupNames.size,
                        checked = areAllGroupsSelected(row.groupNames, selectedGroups),
                        onClick = { activateRow(row) },
                        focusRequester = focusRequesterFor(index),
                        onFocused = { focusedRowIndex = index }
                    )
                    is GuideGroupVisibleRow.Group -> GuideGroupChildRow(
                        label = com.grid.tv.domain.model.ChannelGroupIdentity.displayLabel(row.fullName),
                        checked = row.fullName in selectedGroups,
                        onClick = { activateRow(row) },
                        focusRequester = focusRequesterFor(index),
                        onFocused = { focusedRowIndex = index }
                    )
                }
            }
        }
    }
}
