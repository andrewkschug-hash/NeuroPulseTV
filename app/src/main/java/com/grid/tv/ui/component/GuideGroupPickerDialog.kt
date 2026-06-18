package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private enum class GuidePickerFocusZone { List, Actions }

@Composable
fun GuideGroupPickerDialog(
    channelGroups: List<String>,
    initialSelection: Set<String>,
    title: String = "Choose your channels",
    subtitle: String = "Pick the groups you want in the live guide. You can change this later in Settings.",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selection by remember { mutableStateOf(initialSelection) }
    var focusZone by remember { mutableStateOf(GuidePickerFocusZone.List) }
    var listFocusIndex by remember { mutableIntStateOf(0) }
    var actionFocusIndex by remember { mutableIntStateOf(0) }

    val categories = remember(channelGroups) { buildGuideGroupCategories(channelGroups) }
    var expandedCategories by remember(channelGroups, initialSelection) {
        mutableStateOf(expandedCategoriesForSelection(categories, initialSelection))
    }
    val visibleRows = remember(categories, expandedCategories) {
        buildVisibleGuideGroupRows(categories, expandedCategories)
    }

    val listState = rememberLazyListState()

    LaunchedEffect(channelGroups, initialSelection) {
        val expanded = expandedCategoriesForSelection(categories, initialSelection)
        expandedCategories = expanded
        listFocusIndex = visibleRowIndexForSelection(categories, expanded, initialSelection)
    }

    LaunchedEffect(focusZone, listFocusIndex, visibleRows.size) {
        if (focusZone == GuidePickerFocusZone.List && visibleRows.isNotEmpty()) {
            listState.animateScrollToItem(listFocusIndex.coerceIn(0, visibleRows.lastIndex))
        }
    }

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val lastListIndex = (visibleRows.size - 1).coerceAtLeast(0)
        return when (event.key) {
            Key.DirectionDown -> {
                when (focusZone) {
                    GuidePickerFocusZone.List -> {
                        if (listFocusIndex < lastListIndex) {
                            listFocusIndex += 1
                        } else {
                            focusZone = GuidePickerFocusZone.Actions
                            actionFocusIndex = 0
                        }
                    }
                    GuidePickerFocusZone.Actions -> Unit
                }
                true
            }
            Key.DirectionUp -> {
                when (focusZone) {
                    GuidePickerFocusZone.List -> {
                        if (listFocusIndex > 0) {
                            listFocusIndex -= 1
                        }
                    }
                    GuidePickerFocusZone.Actions -> {
                        focusZone = GuidePickerFocusZone.List
                        listFocusIndex = lastListIndex
                    }
                }
                true
            }
            Key.DirectionLeft -> {
                if (focusZone == GuidePickerFocusZone.Actions) {
                    actionFocusIndex = 0
                }
                true
            }
            Key.DirectionRight -> {
                if (focusZone == GuidePickerFocusZone.Actions) {
                    actionFocusIndex = 1
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (focusZone) {
                    GuidePickerFocusZone.List -> {
                        when (val row = visibleRows.getOrNull(listFocusIndex)) {
                            GuideGroupVisibleRow.AllChannels -> selection = emptySet()
                            is GuideGroupVisibleRow.Category -> {
                                expandedCategories = toggleCategoryExpansion(
                                    expandedCategories,
                                    row.categoryIndex
                                )
                            }
                            is GuideGroupVisibleRow.Group -> {
                                selection = toggleGuideGroupSelection(selection, row.fullName)
                            }
                            null -> Unit
                        }
                    }
                    GuidePickerFocusZone.Actions -> {
                        if (actionFocusIndex == 0) onDismiss() else onConfirm(selection)
                    }
                }
                true
            }
            Key.Back, Key.Escape -> {
                onDismiss()
                true
            }
            else -> false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(12.dp))
                    .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .onPreviewKeyEvent(::handleKey)
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
                        .heightIn(max = 400.dp)
                ) {
                    itemsIndexed(
                        visibleRows,
                        key = { _, row ->
                            when (row) {
                                GuideGroupVisibleRow.AllChannels -> "all"
                                is GuideGroupVisibleRow.Category -> "cat_${row.categoryIndex}"
                                is GuideGroupVisibleRow.Group -> "grp_${row.fullName}"
                            }
                        }
                    ) { index, row ->
                        val focused = focusZone == GuidePickerFocusZone.List && listFocusIndex == index
                        when (row) {
                            GuideGroupVisibleRow.AllChannels -> GuideGroupAllChannelsRow(
                                checked = selection.isEmpty(),
                                focused = focused,
                                onClick = {
                                    selection = emptySet()
                                    focusZone = GuidePickerFocusZone.List
                                    listFocusIndex = index
                                }
                            )
                            is GuideGroupVisibleRow.Category -> GuideGroupCategoryRow(
                                prefix = row.prefix,
                                childCount = row.childCount,
                                expanded = row.expanded,
                                focused = focused,
                                onClick = {
                                    expandedCategories = toggleCategoryExpansion(
                                        expandedCategories,
                                        row.categoryIndex
                                    )
                                    focusZone = GuidePickerFocusZone.List
                                    listFocusIndex = index
                                }
                            )
                            is GuideGroupVisibleRow.Group -> GuideGroupChildRow(
                                suffixLabel = row.suffixLabel,
                                checked = row.fullName in selection,
                                focused = focused,
                                onClick = {
                                    selection = toggleGuideGroupSelection(selection, row.fullName)
                                    focusZone = GuidePickerFocusZone.List
                                    listFocusIndex = index
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    GridOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = if (focusZone == GuidePickerFocusZone.Actions && actionFocusIndex == 0) {
                            Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
                    GridPrimaryButton(
                        text = confirmLabel,
                        onClick = { onConfirm(selection) },
                        modifier = if (focusZone == GuidePickerFocusZone.Actions && actionFocusIndex == 1) {
                            Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}
