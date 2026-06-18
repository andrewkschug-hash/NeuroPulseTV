package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private enum class GuidePickerFocusZone { Regions, List, Actions }

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
    var regionFocusIndex by remember { mutableIntStateOf(0) }
    var listFocusIndex by remember { mutableIntStateOf(0) }
    var actionFocusIndex by remember { mutableIntStateOf(0) } // 0 = Cancel, 1 = Save

    val regions = remember(channelGroups) { guideGroupRegions(channelGroups) }
    val regionFilters = remember(regions) { listOf<String?>(null) + regions }
    val selectedRegion = regionFilters.getOrNull(regionFocusIndex)
    val visibleGroups = remember(channelGroups, selectedRegion) {
        guideGroupsInRegion(channelGroups, selectedRegion)
    }
    val listItemCount = 1 + visibleGroups.size

    val listState = rememberLazyListState()
    val regionRowState = rememberLazyListState()
    val saveFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedRegion) {
        listFocusIndex = 0
        listState.scrollToItem(0)
    }

    LaunchedEffect(focusZone, listFocusIndex, selectedRegion) {
        if (focusZone == GuidePickerFocusZone.List) {
            listState.animateScrollToItem(listFocusIndex.coerceIn(0, listItemCount - 1))
        }
    }

    LaunchedEffect(focusZone, regionFocusIndex) {
        if (focusZone == GuidePickerFocusZone.Regions) {
            regionRowState.animateScrollToItem(regionFocusIndex.coerceIn(0, regionFilters.lastIndex))
        }
    }

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionDown -> {
                when (focusZone) {
                    GuidePickerFocusZone.Regions -> {
                        focusZone = GuidePickerFocusZone.List
                        listFocusIndex = 0
                    }
                    GuidePickerFocusZone.List -> {
                        if (listFocusIndex < listItemCount - 1) {
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
                    GuidePickerFocusZone.Regions -> Unit
                    GuidePickerFocusZone.List -> {
                        if (listFocusIndex > 0) {
                            listFocusIndex -= 1
                        } else if (regions.isNotEmpty()) {
                            focusZone = GuidePickerFocusZone.Regions
                        }
                    }
                    GuidePickerFocusZone.Actions -> {
                        focusZone = GuidePickerFocusZone.List
                        listFocusIndex = listItemCount - 1
                    }
                }
                true
            }
            Key.DirectionLeft -> {
                when (focusZone) {
                    GuidePickerFocusZone.Regions -> {
                        regionFocusIndex = (regionFocusIndex - 1).coerceAtLeast(0)
                    }
                    GuidePickerFocusZone.Actions -> {
                        actionFocusIndex = 0
                    }
                    GuidePickerFocusZone.List -> Unit
                }
                true
            }
            Key.DirectionRight -> {
                when (focusZone) {
                    GuidePickerFocusZone.Regions -> {
                        regionFocusIndex = (regionFocusIndex + 1).coerceAtMost(regionFilters.lastIndex)
                    }
                    GuidePickerFocusZone.Actions -> {
                        actionFocusIndex = 1
                    }
                    GuidePickerFocusZone.List -> Unit
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (focusZone) {
                    GuidePickerFocusZone.Regions -> {
                        focusZone = GuidePickerFocusZone.List
                        listFocusIndex = 0
                    }
                    GuidePickerFocusZone.List -> {
                        if (listFocusIndex == 0) {
                            selection = emptySet()
                        } else {
                            val group = visibleGroups.getOrNull(listFocusIndex - 1) ?: return true
                            selection = toggleGuideGroupSelection(selection, group)
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

                if (regions.isNotEmpty()) {
                    Text(
                        text = "Filter by region",
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        state = regionRowState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        itemsIndexed(regionFilters, key = { index, region -> region ?: "all_$index" }) { index, region ->
                            val label = region?.let(::guideRegionDisplayLabel) ?: "All"
                            val selected = index == regionFocusIndex
                            val focused = focusZone == GuidePickerFocusZone.Regions && selected
                            GuideRegionFilterChip(
                                label = label,
                                active = selected,
                                focused = focused,
                                onClick = {
                                    regionFocusIndex = index
                                    focusZone = GuidePickerFocusZone.Regions
                                }
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    item(key = "all_channels") {
                        GuideGroupPickerRow(
                            label = "All channels",
                            checked = selection.isEmpty(),
                            focused = focusZone == GuidePickerFocusZone.List && listFocusIndex == 0,
                            onClick = {
                                selection = emptySet()
                                focusZone = GuidePickerFocusZone.List
                                listFocusIndex = 0
                            }
                        )
                    }
                    itemsIndexed(visibleGroups, key = { _, name -> name }) { index, group ->
                        val listIndex = index + 1
                        GuideGroupPickerRow(
                            label = group,
                            checked = group in selection,
                            focused = focusZone == GuidePickerFocusZone.List && listFocusIndex == listIndex,
                            onClick = {
                                selection = toggleGuideGroupSelection(selection, group)
                                focusZone = GuidePickerFocusZone.List
                                listFocusIndex = listIndex
                            }
                        )
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
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .focusable()
                            .then(
                                if (focusZone == GuidePickerFocusZone.Actions && actionFocusIndex == 0) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                    )
                    GridPrimaryButton(
                        text = confirmLabel,
                        onClick = { onConfirm(selection) },
                        modifier = Modifier
                            .focusRequester(saveFocusRequester)
                            .focusable()
                            .then(
                                if (focusZone == GuidePickerFocusZone.Actions && actionFocusIndex == 1) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideRegionFilterChip(
    label: String,
    active: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        focused -> EpgColors.Accent.copy(alpha = 0.25f)
        active -> EpgColors.ChannelRowFocusBg
        else -> EpgColors.ChannelColumnBg
    }
    val borderColor = when {
        focused -> EpgColors.FocusBorder
        active -> EpgColors.Accent.copy(alpha = 0.6f)
        else -> EpgColors.BorderSubtle
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = Modifier.then(
            if (focused) Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
            else Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background
        )
    ) {
        Text(
            text = label,
            color = if (focused || active) EpgColors.TextPrimary else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            fontWeight = if (focused || active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun GuideGroupPickerRow(
    label: String,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        focused -> EpgColors.ChannelRowFocusBg
        checked -> Color(0xFF16161E)
        else -> EpgColors.GridBg
    }
    val textColor = when {
        focused -> EpgColors.TextPrimary
        checked -> EpgColors.TextDimmed
        else -> EpgColors.TextSecondary
    }

    GridFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(
                if (focused) Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (checked) {
                Text(
                    text = "✓",
                    color = if (focused) EpgColors.Accent else EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
