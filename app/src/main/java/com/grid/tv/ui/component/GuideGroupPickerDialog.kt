package com.grid.tv.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun GuideGroupPickerDialog(
    channelGroups: List<String>,
    initialSelection: Set<String>,
    groupChannelCounts: Map<String, Int> = emptyMap(),
    title: String = "Choose your channels",
    subtitle: String = "Pick the groups you want in the live guide. You can change this later in Settings.",
    confirmLabel: String = "Save",
    allowDismiss: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selection by remember { mutableStateOf(initialSelection) }
    val categories = remember(channelGroups, groupChannelCounts) {
        buildGuideGroupCategories(channelGroups, groupChannelCounts)
    }
    var expandedCategories by remember {
        mutableStateOf(expandedCategoriesForSelection(categories, initialSelection))
    }
    val visibleRows = remember(categories, expandedCategories) {
        buildVisibleGuideGroupRows(categories, expandedCategories)
    }

    var focusedRowIndex by remember {
        mutableIntStateOf(
            visibleRowIndexForSelection(
                categories,
                expandedCategoriesForSelection(categories, initialSelection),
                initialSelection
            )
        )
    }

    val rowFocusRequesters = remember(visibleRows.size) {
        List(visibleRows.size) { FocusRequester() }
    }
    val cancelFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState()

    fun activateRow(row: GuideGroupVisibleRow) {
        when (row) {
            GuideGroupVisibleRow.AllChannels -> selection = emptySet()
            GuideGroupVisibleRow.FavoriteSectionHeader -> Unit
            GuideGroupVisibleRow.FavoriteSectionEmpty -> Unit
            is GuideGroupVisibleRow.Category -> {
                expandedCategories = toggleCategoryExpansion(expandedCategories, row.categoryIndex)
            }
            is GuideGroupVisibleRow.SelectAll -> {
                selection = toggleSelectAllGroups(row.groupNames, selection)
            }
            is GuideGroupVisibleRow.Group -> {
                selection = toggleGuideGroupSelection(selection, row.fullName)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (visibleRows.isNotEmpty()) {
            val index = focusedRowIndex.coerceIn(0, visibleRows.lastIndex)
            rowFocusRequesters[index].requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(focusedRowIndex) {
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

    if (allowDismiss) {
        BackHandler(onBack = onDismiss)
    }

    Dialog(
        onDismissRequest = { if (allowDismiss) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = allowDismiss,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EpgColors.Background.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight(0.82f)
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(12.dp))
                    .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .onPreviewKeyEvent { event ->
                        if (visibleRows.isEmpty()) return@onPreviewKeyEvent false
                        handleGuideGroupTvKeyEvent(
                            event = event,
                            focusedIndex = focusedRowIndex,
                            lastIndex = visibleRows.lastIndex,
                            onFocusedIndexChange = { next ->
                                focusedRowIndex = next
                                rowFocusRequesters.getOrNull(next)?.requestFocusSafely()
                            },
                            onActivate = { activateRow(visibleRows[focusedRowIndex.coerceIn(0, visibleRows.lastIndex)]) },
                            onBack = { if (allowDismiss) onDismiss() }
                        )
                    }
            ) {
                Text(
                    text = title,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
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
                                checked = selection.isEmpty(),
                                onClick = { activateRow(row) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { focusedRowIndex = index }
                            )
                            is GuideGroupVisibleRow.Category -> GuideGroupCategoryRow(
                                displayName = row.displayName,
                                channelCount = row.channelCount,
                                subGroupCount = row.subGroupCount,
                                expanded = row.expanded,
                                onClick = { activateRow(row) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { focusedRowIndex = index }
                            )
                            is GuideGroupVisibleRow.SelectAll -> GuideGroupSelectAllRow(
                                displayName = row.displayName,
                                childCount = row.groupNames.size,
                                checked = areAllGroupsSelected(row.groupNames, selection),
                                onClick = { activateRow(row) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { focusedRowIndex = index }
                            )
                            is GuideGroupVisibleRow.Group -> GuideGroupChildRow(
                                label = row.fullName,
                                checked = row.fullName in selection,
                                onClick = { activateRow(row) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { focusedRowIndex = index }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (allowDismiss) {
                        GridOutlinedButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(cancelFocusRequester)
                        )
                    }
                    GridPrimaryButton(
                        text = confirmLabel,
                        onClick = { onConfirm(selection) },
                        modifier = Modifier.focusRequester(saveFocusRequester)
                    )
                }
            }
        }
    }
}
