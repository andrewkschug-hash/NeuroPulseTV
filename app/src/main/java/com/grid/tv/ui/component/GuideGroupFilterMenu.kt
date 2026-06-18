package com.grid.tv.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

val GUIDE_GROUP_REGION_SEPARATORS = listOf(" ❖ ", " | ", " - ", " – ")

/** Leading token before a separator in Xtream group names, e.g. `CA ❖ Sports`. */
fun guideGroupRegionPrefix(groupName: String): String? {
    val trimmed = groupName.trim()
    for (separator in GUIDE_GROUP_REGION_SEPARATORS) {
        if (separator in trimmed) {
            return trimmed.substringBefore(separator).trim().takeIf { it.isNotBlank() }
        }
    }
    val token = trimmed.substringBefore(' ').trim()
    return if (token.length in 2..4 && token.all { it.isLetter() }) {
        token.uppercase()
    } else {
        null
    }
}

fun guideGroupRegions(channelGroups: List<String>): List<String> =
    channelGroups.mapNotNull(::guideGroupRegionPrefix)
        .distinct()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

fun guideGroupsInRegion(channelGroups: List<String>, region: String?): List<String> {
    if (region == null) return channelGroups
    return channelGroups.filter { guideGroupRegionPrefix(it) == region }
}

fun guideRegionDisplayLabel(code: String): String = when (code.uppercase()) {
    "CA" -> "Canada"
    "US", "USA" -> "USA"
    "UK", "GB" -> "UK"
    "AU" -> "Australia"
    "AR" -> "Arabic"
    "FR" -> "France"
    "DE" -> "Germany"
    "ES" -> "Spain"
    "IT" -> "Italy"
    "BR" -> "Brazil"
    "MX" -> "Mexico"
    else -> code
}

fun toggleSelectAllGroups(groupNames: List<String>, selectedGroups: Set<String>): Set<String> {
    if (groupNames.isEmpty()) return selectedGroups
    val next = selectedGroups.toMutableSet()
    if (groupNames.all { it in selectedGroups }) {
        groupNames.forEach { next.remove(it) }
    } else {
        groupNames.forEach { next.add(it) }
    }
    return next
}

fun areAllGroupsSelected(groupNames: List<String>, selectedGroups: Set<String>): Boolean =
    groupNames.isNotEmpty() && groupNames.all { it in selectedGroups }

fun toggleGuideGroupSelection(selectedGroups: Set<String>, group: String): Set<String> {
    val next = selectedGroups.toMutableSet()
    if (group in next) next.remove(group) else next.add(group)
    return next
}

fun guideFilterMenuItemCount(
    channelGroups: List<String>,
    expandedCategories: Set<Int>,
    selectedGroups: Set<String> = emptySet()
): Int {
    val categories = buildGuideGroupCategories(channelGroups)
    val expanded = expandedCategories.ifEmpty {
        expandedCategoriesForSelection(categories, selectedGroups)
    }
    return buildVisibleGuideGroupRows(categories, expanded).size
}

fun guideFilterForMenuSelection(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    menuIndex: Int,
    expandedCategories: Set<Int>
): GuideChannelFilter {
    val categories = buildGuideGroupCategories(channelGroups)
    val row = buildVisibleGuideGroupRows(categories, expandedCategories).getOrNull(menuIndex)
        ?: return GuideChannelFilter(selectedGroups)
    return when (row) {
        GuideGroupVisibleRow.AllChannels -> GuideChannelFilter.All
        is GuideGroupVisibleRow.Group -> GuideChannelFilter(
            toggleGuideGroupSelection(selectedGroups, row.fullName)
        )
        is GuideGroupVisibleRow.SelectAll -> GuideChannelFilter(
            toggleSelectAllGroups(row.groupNames, selectedGroups)
        )
        is GuideGroupVisibleRow.Category -> GuideChannelFilter(selectedGroups)
    }
}

fun guideFilterRowAction(
    row: GuideGroupVisibleRow,
    selectedGroups: Set<String>
): GuideChannelFilter? = when (row) {
    GuideGroupVisibleRow.AllChannels -> GuideChannelFilter.All
    is GuideGroupVisibleRow.Group -> GuideChannelFilter(
        toggleGuideGroupSelection(selectedGroups, row.fullName)
    )
    is GuideGroupVisibleRow.SelectAll -> GuideChannelFilter(
        toggleSelectAllGroups(row.groupNames, selectedGroups)
    )
    is GuideGroupVisibleRow.Category -> null
}

fun isGuideGroupRowSelected(
    row: GuideGroupVisibleRow,
    selectedGroups: Set<String>
): Boolean = when (row) {
    GuideGroupVisibleRow.AllChannels -> selectedGroups.isEmpty()
    is GuideGroupVisibleRow.Group -> row.fullName in selectedGroups
    is GuideGroupVisibleRow.SelectAll -> areAllGroupsSelected(row.groupNames, selectedGroups)
    is GuideGroupVisibleRow.Category -> false
}

fun isGuideGroupMenuItemSelected(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    menuIndex: Int,
    expandedCategories: Set<Int>
): Boolean {
    val categories = buildGuideGroupCategories(channelGroups)
    val row = buildVisibleGuideGroupRows(categories, expandedCategories).getOrNull(menuIndex) ?: return false
    return isGuideGroupRowSelected(row, selectedGroups)
}

private enum class GuideFilterMenuFocusZone { List, Done }

@Composable
fun GuideGroupFilterMenu(
    expanded: Boolean,
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    expandedCategories: Set<Int>,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val menuFocusRequester = remember { FocusRequester() }
    val categories = remember(channelGroups) { buildGuideGroupCategories(channelGroups) }
    val visibleRows = remember(categories, expandedCategories) {
        buildVisibleGuideGroupRows(categories, expandedCategories)
    }
    val listState = rememberLazyListState()
    val lastIndex = (visibleRows.size - 1).coerceAtLeast(0)
    val hasHeader = visibleRows.isNotEmpty()
    val listHeaderOffset = if (hasHeader) 1 else 0
    var focusZone by remember { mutableStateOf(GuideFilterMenuFocusZone.List) }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusZone = GuideFilterMenuFocusZone.List
            menuFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(expanded, expandedCategories, selectedGroups) {
        if (expanded) {
            menuFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(focusZone, focusedIndex, visibleRows.size) {
        if (focusZone == GuideFilterMenuFocusZone.List && visibleRows.isNotEmpty()) {
            listState.scrollToItem((focusedIndex + listHeaderOffset).coerceAtLeast(0))
        }
    }

    fun handleMenuKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when {
            event.key == Key.DirectionUp -> {
                when (focusZone) {
                    GuideFilterMenuFocusZone.List -> {
                        onFocusedIndexChange((focusedIndex - 1).coerceAtLeast(0))
                    }
                    GuideFilterMenuFocusZone.Done -> {
                        focusZone = GuideFilterMenuFocusZone.List
                        onFocusedIndexChange(lastIndex)
                    }
                }
                true
            }
            event.key == Key.DirectionDown -> {
                when (focusZone) {
                    GuideFilterMenuFocusZone.List -> {
                        if (focusedIndex < lastIndex) {
                            onFocusedIndexChange(focusedIndex + 1)
                        } else {
                            focusZone = GuideFilterMenuFocusZone.Done
                        }
                    }
                    GuideFilterMenuFocusZone.Done -> Unit
                }
                true
            }
            event.key == Key.Back || event.key == Key.Escape -> {
                onDismiss()
                true
            }
            isTvActivateKey(event) -> {
                when (focusZone) {
                    GuideFilterMenuFocusZone.List -> onToggle(focusedIndex)
                    GuideFilterMenuFocusZone.Done -> onDismiss()
                }
                true
            }
            else -> false
        }
    }

    val keyModifier = Modifier
        .onPreviewKeyEvent(::handleMenuKey)
        .onKeyEvent(::handleMenuKey)

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
                .focusRequester(menuFocusRequester)
                .focusable()
                .focusProperties { canFocus = true }
                .then(keyModifier),
            contentAlignment = Alignment.TopEnd
        ) {
            Column(
                modifier = modifier
                    .padding(top = 108.dp, end = 24.dp)
                    .widthIn(min = 280.dp, max = 420.dp)
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                    .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp)
            ) {
                if (visibleRows.isNotEmpty()) {
                    Text(
                        text = "Channel groups",
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
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
                        val focused = focusZone == GuideFilterMenuFocusZone.List && index == focusedIndex
                        when (row) {
                            GuideGroupVisibleRow.AllChannels -> GuideGroupAllChannelsRow(
                                checked = selectedGroups.isEmpty(),
                                focused = focused,
                                onClick = { onToggle(index) }
                            )
                            is GuideGroupVisibleRow.Category -> GuideGroupCategoryRow(
                                prefix = row.prefix,
                                childCount = row.childCount,
                                expanded = row.expanded,
                                focused = focused,
                                onClick = { onToggle(index) }
                            )
                            is GuideGroupVisibleRow.SelectAll -> GuideGroupSelectAllRow(
                                prefix = row.prefix,
                                childCount = row.groupNames.size,
                                checked = areAllGroupsSelected(row.groupNames, selectedGroups),
                                focused = focused,
                                onClick = { onToggle(index) }
                            )
                            is GuideGroupVisibleRow.Group -> GuideGroupChildRow(
                                suffixLabel = row.suffixLabel,
                                checked = row.fullName in selectedGroups,
                                focused = focused,
                                onClick = { onToggle(index) }
                            )
                        }
                    }
                }

                GuideFilterDoneButton(
                    focused = focusZone == GuideFilterMenuFocusZone.Done,
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun GuideFilterDoneButton(
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (focused) Color(0xFF5AA3FF) else Color(0xFF3B8FFF), shape)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = EpgColors.FocusBorder,
                shape = shape
            )
            .clickable(onClick = onClick)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Done",
            color = Color.White,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun GuideGroupAllChannelsRow(
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tvFocusable: Boolean = false
) {
    GuideGroupTreeRowShell(
        label = "All channels",
        checked = checked,
        focused = focused,
        onClick = onClick,
        startPadding = 20.dp,
        modifier = modifier,
        tvFocusable = tvFocusable
    )
}

@Composable
internal fun GuideGroupCategoryRow(
    prefix: String,
    childCount: Int,
    expanded: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tvFocusable: Boolean = false
) {
    var rowFocused by remember { mutableStateOf(false) }
    val showFocus = focused || rowFocused
    val background = when {
        showFocus -> EpgColors.ChannelRowFocusBg
        else -> EpgColors.GridBg
    }
    val textColor = if (showFocus) EpgColors.TextPrimary else EpgColors.TextSecondary
    val shape = RoundedCornerShape(6.dp)
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (tvFocusable) {
                    Modifier.onFocusChanged { rowFocused = it.isFocused }
                } else {
                    Modifier.focusProperties { canFocus = false }
                }
            ),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showFocus) Modifier.border(2.dp, EpgColors.FocusBorder, shape)
                    else Modifier
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$prefix ($childCount)",
                color = textColor,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (showFocus) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "▼" else "▶",
                color = if (showFocus) EpgColors.Accent else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun GuideGroupSelectAllRow(
    prefix: String,
    childCount: Int,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tvFocusable: Boolean = false
) {
    val regionLabel = guideRegionDisplayLabel(prefix)
    GuideGroupTreeRowShell(
        label = "Select all $regionLabel ($childCount)",
        checked = checked,
        focused = focused,
        onClick = onClick,
        startPadding = 28.dp,
        italic = true,
        modifier = modifier,
        tvFocusable = tvFocusable
    )
}

@Composable
internal fun GuideGroupChildRow(
    suffixLabel: String,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tvFocusable: Boolean = false
) {
    GuideGroupTreeRowShell(
        label = suffixLabel,
        checked = checked,
        focused = focused,
        onClick = onClick,
        startPadding = 36.dp,
        modifier = modifier,
        tvFocusable = tvFocusable
    )
}

@Composable
private fun GuideGroupTreeRowShell(
    label: String,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    startPadding: androidx.compose.ui.unit.Dp,
    italic: Boolean = false,
    modifier: Modifier = Modifier,
    tvFocusable: Boolean = false
) {
    var rowFocused by remember { mutableStateOf(false) }
    val showFocus = focused || rowFocused
    val background = when {
        showFocus -> EpgColors.ChannelRowFocusBg
        checked -> Color(0xFF16161E)
        else -> EpgColors.GridBg
    }
    val textColor = when {
        showFocus -> EpgColors.TextPrimary
        checked -> EpgColors.TextDimmed
        else -> EpgColors.TextSecondary
    }
    val shape = RoundedCornerShape(6.dp)
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (tvFocusable) {
                    Modifier.onFocusChanged { rowFocused = it.isFocused }
                } else {
                    Modifier.focusProperties { canFocus = false }
                }
            ),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = background,
            focusedContainerColor = background
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showFocus) Modifier.border(2.dp, EpgColors.FocusBorder, shape)
                    else Modifier
                )
                .padding(start = startPadding, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontFamily = DmSansFamily,
                fontSize = if (italic) 13.sp else 14.sp,
                fontWeight = if (showFocus) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (checked) {
                Text(
                    text = "✓",
                    color = if (showFocus) EpgColors.Accent else EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
