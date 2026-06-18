package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

    val categories = remember(channelGroups) { buildGuideGroupCategories(channelGroups) }
    val visibleRows = remember(categories, expandedCategories) {
        buildVisibleGuideGroupRows(categories, expandedCategories)
    }
    val listState = rememberLazyListState()
    val lastIndex = (visibleRows.size - 1).coerceAtLeast(0)

    LaunchedEffect(focusedIndex, visibleRows.size) {
        listState.animateScrollToItem(focusedIndex.coerceIn(0, lastIndex))
    }

    fun handleMenuKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionUp -> {
                onFocusedIndexChange((focusedIndex - 1).coerceAtLeast(0))
                true
            }
            Key.DirectionDown -> {
                onFocusedIndexChange((focusedIndex + 1).coerceAtMost(lastIndex))
                true
            }
            Key.Back, Key.Escape -> {
                onDismiss()
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                onToggle(focusedIndex)
                true
            }
            else -> false
        }
    }

    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true)
    ) {
        LazyColumn(
            state = listState,
            modifier = modifier
                .padding(top = 108.dp, end = 24.dp)
                .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp)
                .heightIn(max = 420.dp)
                .onPreviewKeyEvent(::handleMenuKey)
        ) {
            if (visibleRows.isNotEmpty()) {
                item {
                    Text(
                        text = "Channel groups",
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
            itemsIndexed(
                visibleRows,
                key = { index, row ->
                    when (row) {
                        GuideGroupVisibleRow.AllChannels -> "all"
                        is GuideGroupVisibleRow.Category -> "cat_${row.categoryIndex}"
                        is GuideGroupVisibleRow.SelectAll -> "all_${row.categoryIndex}"
                        is GuideGroupVisibleRow.Group -> "grp_${row.fullName}"
                    }
                }
            ) { index, row ->
                val focused = index == focusedIndex
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
    }
}

@Composable
internal fun GuideGroupAllChannelsRow(
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    GuideGroupTreeRowShell(
        label = "All channels",
        checked = checked,
        focused = focused,
        onClick = onClick,
        startPadding = 20.dp
    )
}

@Composable
internal fun GuideGroupCategoryRow(
    prefix: String,
    childCount: Int,
    expanded: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        focused -> EpgColors.ChannelRowFocusBg
        else -> EpgColors.GridBg
    }
    val textColor = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .focusProperties { canFocus = false }
            .clip(shape)
            .background(background, shape)
            .then(
                if (focused) Modifier.border(2.dp, EpgColors.FocusBorder, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$prefix ($childCount)",
            color = textColor,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (expanded) "▼" else "▶",
            color = if (focused) EpgColors.Accent else EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun GuideGroupSelectAllRow(
    prefix: String,
    childCount: Int,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val regionLabel = guideRegionDisplayLabel(prefix)
    GuideGroupTreeRowShell(
        label = "Select all $regionLabel ($childCount)",
        checked = checked,
        focused = focused,
        onClick = onClick,
        startPadding = 28.dp,
        italic = true
    )
}

@Composable
internal fun GuideGroupChildRow(
    suffixLabel: String,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    GuideGroupTreeRowShell(
        label = suffixLabel,
        checked = checked,
        focused = focused,
        onClick = onClick,
        startPadding = 36.dp
    )
}

@Composable
private fun GuideGroupTreeRowShell(
    label: String,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    startPadding: androidx.compose.ui.unit.Dp,
    italic: Boolean = false
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
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .focusProperties { canFocus = false }
            .clip(shape)
            .background(background, shape)
            .then(
                if (focused) Modifier.border(2.dp, EpgColors.FocusBorder, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(start = startPadding, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = DmSansFamily,
            fontSize = if (italic) 13.sp else 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
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
