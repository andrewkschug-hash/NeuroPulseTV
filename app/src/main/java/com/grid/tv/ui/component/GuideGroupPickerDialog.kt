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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
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

    LaunchedEffect(Unit) {
        if (visibleRows.isNotEmpty()) {
            val index = focusedRowIndex.coerceIn(0, visibleRows.lastIndex)
            rowFocusRequesters[index].requestFocusSafelyAfterLayout()
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

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
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
                                is GuideGroupVisibleRow.Category -> "cat_${row.categoryIndex}"
                                is GuideGroupVisibleRow.SelectAll -> "all_${row.categoryIndex}"
                                is GuideGroupVisibleRow.Group -> "grp_${row.fullName}"
                            }
                        }
                    ) { index, row ->
                        val rowModifier = Modifier
                            .focusRequester(rowFocusRequesters[index])
                            .onFocusChanged {
                                if (it.isFocused) focusedRowIndex = index
                            }
                        when (row) {
                            GuideGroupVisibleRow.AllChannels -> GuideGroupAllChannelsRow(
                                checked = selection.isEmpty(),
                                focused = false,
                                onClick = { selection = emptySet() },
                                modifier = rowModifier,
                                tvFocusable = true
                            )
                            is GuideGroupVisibleRow.Category -> GuideGroupCategoryRow(
                                displayName = row.displayName,
                                channelCount = row.channelCount,
                                subGroupCount = row.subGroupCount,
                                expanded = row.expanded,
                                focused = false,
                                onClick = {
                                    expandedCategories = toggleCategoryExpansion(
                                        expandedCategories,
                                        row.categoryIndex
                                    )
                                },
                                modifier = rowModifier,
                                tvFocusable = true
                            )
                            is GuideGroupVisibleRow.SelectAll -> GuideGroupSelectAllRow(
                                displayName = row.displayName,
                                childCount = row.groupNames.size,
                                checked = areAllGroupsSelected(row.groupNames, selection),
                                focused = false,
                                onClick = {
                                    selection = toggleSelectAllGroups(row.groupNames, selection)
                                },
                                modifier = rowModifier,
                                tvFocusable = true
                            )
                            is GuideGroupVisibleRow.Group -> GuideGroupChildRow(
                                label = row.fullName,
                                checked = row.fullName in selection,
                                focused = false,
                                onClick = {
                                    selection = toggleGuideGroupSelection(selection, row.fullName)
                                },
                                modifier = rowModifier,
                                tvFocusable = true
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    GuidePickerActionButton(
                        text = "Cancel",
                        focused = false,
                        primary = false,
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(cancelFocusRequester),
                        tvFocusable = true
                    )
                    GuidePickerActionButton(
                        text = confirmLabel,
                        focused = false,
                        primary = true,
                        onClick = { onConfirm(selection) },
                        modifier = Modifier.focusRequester(saveFocusRequester),
                        tvFocusable = true
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidePickerActionButton(
    text: String,
    focused: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tvFocusable: Boolean = false
) {
    var rowFocused by remember { mutableStateOf(false) }
    val showFocus = focused || rowFocused
    val shape = RoundedCornerShape(8.dp)
    val background = when {
        primary && showFocus -> Color(0xFF5AA3FF)
        primary -> Color(0xFF3B8FFF)
        showFocus -> Color(0xFF343446)
        else -> Color(0xFF2E2E3E)
    }
    val borderColor = when {
        showFocus -> EpgColors.FocusBorder
        primary -> Color.Transparent
        else -> Color(0xFF4B5563)
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .then(
                if (tvFocusable) {
                    Modifier.onFocusChanged { rowFocused = it.isFocused }
                } else {
                    Modifier
                }
            ),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background
        )
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = if (showFocus) 2.dp else if (primary) 0.dp else 1.dp,
                    color = borderColor,
                    shape = shape
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
