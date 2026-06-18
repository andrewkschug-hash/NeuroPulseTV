package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private data class GuideGroupMenuItem(
    val label: String,
    val groupName: String?,
    val menuIndex: Int,
    val section: String? = null
)

private fun buildGuideGroupMenuItems(channelGroups: List<String>): List<GuideGroupMenuItem> = buildList {
    add(GuideGroupMenuItem("All channels", groupName = null, menuIndex = 0, section = "Channel groups"))
    channelGroups.forEachIndexed { index, group ->
        add(
            GuideGroupMenuItem(
                label = group,
                groupName = group,
                menuIndex = index + 1,
                section = null
            )
        )
    }
}

fun guideFilterMenuItemCount(channelGroups: List<String>): Int =
    1 + channelGroups.size

private val GROUP_REGION_SEPARATORS = listOf(" ❖ ", " | ", " - ", " – ")

/** Leading token before a separator in Xtream group names, e.g. `CA ❖ Sports`. */
fun guideGroupRegionPrefix(groupName: String): String? {
    val trimmed = groupName.trim()
    for (separator in GROUP_REGION_SEPARATORS) {
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

fun toggleGuideGroupSelection(selectedGroups: Set<String>, group: String): Set<String> {
    val next = selectedGroups.toMutableSet()
    if (group in next) next.remove(group) else next.add(group)
    return next
}

fun guideFilterForMenuSelection(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    menuIndex: Int
): GuideChannelFilter {
    if (menuIndex == 0) return GuideChannelFilter.All
    val group = channelGroups.getOrNull(menuIndex - 1) ?: return GuideChannelFilter(selectedGroups)
    return GuideChannelFilter(toggleGuideGroupSelection(selectedGroups, group))
}

fun isGuideGroupMenuItemSelected(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    menuIndex: Int
): Boolean {
    if (menuIndex == 0) return selectedGroups.isEmpty()
    val group = channelGroups.getOrNull(menuIndex - 1) ?: return false
    return group in selectedGroups
}

@Composable
fun GuideGroupFilterMenu(
    expanded: Boolean,
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    focusedIndex: Int,
    onDismiss: () -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val items = buildGuideGroupMenuItems(channelGroups)
    val listState = rememberLazyListState()
    val focusedLazyIndex = items.indexOfFirst { it.menuIndex == focusedIndex }.coerceAtLeast(0)

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedLazyIndex)
    }

    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        LazyColumn(
            state = listState,
            modifier = modifier
                .padding(top = 108.dp, end = 24.dp)
                .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp)
                .heightIn(max = 420.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.menuIndex }) { _, item ->
                if (item.section != null) {
                    Text(
                        text = item.section,
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                val focused = item.menuIndex == focusedIndex
                val checked = isGuideGroupMenuItemSelected(channelGroups, selectedGroups, item.menuIndex)
                val rowBackground = when {
                    focused -> EpgColors.ChannelRowFocusBg
                    checked -> androidx.compose.ui.graphics.Color(0xFF16161E)
                    else -> EpgColors.GridBg
                }
                val rowTextColor = when {
                    focused -> EpgColors.TextPrimary
                    checked -> EpgColors.TextDimmed
                    else -> EpgColors.TextSecondary
                }
                GridFocusSurface(
                    onClick = { onToggle(item.menuIndex) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .then(
                            if (focused) {
                                Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                            } else {
                                Modifier
                            }
                        ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = rowBackground,
                        focusedContainerColor = rowBackground
                    )
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.label,
                            color = rowTextColor,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
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
        }
    }
}
