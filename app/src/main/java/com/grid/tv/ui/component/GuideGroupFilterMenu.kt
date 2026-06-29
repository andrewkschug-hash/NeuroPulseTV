package com.grid.tv.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.grid.tv.R
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

/** TV focus wiring for guide-group rows (requester + scroll sync). Activation is handled by [GridFocusSurface]. */
private fun Modifier.guideGroupRowTvFocus(
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onFocusVisualChanged: (Boolean) -> Unit,
): Modifier = this
    .focusRequester(focusRequester)
    .onFocusChanged { state ->
        onFocusVisualChanged(state.isFocused)
        if (state.isFocused) onFocused()
    }

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
    selectedGroups: Set<String> = emptySet(),
    groupChannelCounts: Map<String, Int> = emptyMap()
): Int {
    val categories = buildGuideGroupCategories(channelGroups, groupChannelCounts)
    val expanded = expandedCategories.ifEmpty {
        expandedCategoriesForSelection(categories, selectedGroups)
    }
    return buildVisibleGuideGroupRows(categories, expanded).size
}

fun guideFilterForMenuSelection(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    menuIndex: Int,
    expandedCategories: Set<Int>,
    groupChannelCounts: Map<String, Int> = emptyMap()
): GuideChannelFilter {
    val categories = buildGuideGroupCategories(channelGroups, groupChannelCounts)
    val row = buildVisibleGuideGroupRows(categories, expandedCategories).getOrNull(menuIndex)
        ?: return GuideChannelFilter(selectedGroups)
    return when (row) {
        GuideGroupVisibleRow.AllChannels -> GuideChannelFilter.All
        GuideGroupVisibleRow.FavoriteSectionHeader -> GuideChannelFilter(selectedGroups)
        GuideGroupVisibleRow.FavoriteSectionEmpty -> GuideChannelFilter(selectedGroups)
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
    GuideGroupVisibleRow.FavoriteSectionHeader -> null
    GuideGroupVisibleRow.FavoriteSectionEmpty -> null
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
    GuideGroupVisibleRow.FavoriteSectionHeader -> false
    GuideGroupVisibleRow.FavoriteSectionEmpty -> false
    is GuideGroupVisibleRow.Group -> row.fullName in selectedGroups
    is GuideGroupVisibleRow.SelectAll -> areAllGroupsSelected(row.groupNames, selectedGroups)
    is GuideGroupVisibleRow.Category -> false
}

fun isGuideGroupMenuItemSelected(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    menuIndex: Int,
    expandedCategories: Set<Int>,
    groupChannelCounts: Map<String, Int> = emptyMap()
): Boolean {
    val categories = buildGuideGroupCategories(channelGroups, groupChannelCounts)
    val row = buildVisibleGuideGroupRows(categories, expandedCategories).getOrNull(menuIndex) ?: return false
    return isGuideGroupRowSelected(row, selectedGroups)
}

@Composable
fun GuideGroupFilterMenu(
    expanded: Boolean,
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    expandedCategories: Set<Int>,
    groupChannelCounts: Map<String, Int> = emptyMap(),
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

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
    val doneFocusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded && visibleRows.isNotEmpty()) {
            onFocusedIndexChange(0)
            rowFocusRequesters[0].requestFocusSafelyAfterLayout()
        }
    }

    LaunchedEffect(focusedIndex) {
        if (expanded && visibleRows.isNotEmpty()) {
            listState.scrollToItem(focusedIndex.coerceIn(0, visibleRows.lastIndex))
        }
    }

    LaunchedEffect(visibleRows.size) {
        if (expanded && visibleRows.isNotEmpty() && focusedIndex > visibleRows.lastIndex) {
            onFocusedIndexChange(visibleRows.lastIndex)
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
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            Column(
                modifier = modifier
                    .padding(top = 108.dp, end = 24.dp)
                    .widthIn(min = 280.dp, max = 420.dp)
                    .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                    .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp)
                    .onPreviewKeyEvent { event ->
                        if (visibleRows.isEmpty()) return@onPreviewKeyEvent false
                        handleGuideGroupTvKeyEvent(
                            event = event,
                            focusedIndex = focusedIndex,
                            lastIndex = visibleRows.lastIndex,
                            onFocusedIndexChange = { next ->
                                onFocusedIndexChange(next)
                                rowFocusRequesters.getOrNull(next)?.requestFocusSafely()
                            },
                            onActivate = { onToggle(focusedIndex.coerceIn(0, visibleRows.lastIndex)) },
                            onBack = onDismiss
                        )
                    }
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
                                onClick = { onToggle(index) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { onFocusedIndexChange(index) }
                            )
                            is GuideGroupVisibleRow.Category -> GuideGroupCategoryRow(
                                displayName = row.displayName,
                                channelCount = row.channelCount,
                                subGroupCount = row.subGroupCount,
                                expanded = row.expanded,
                                onClick = { onToggle(index) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { onFocusedIndexChange(index) }
                            )
                            is GuideGroupVisibleRow.SelectAll -> GuideGroupSelectAllRow(
                                displayName = row.displayName,
                                childCount = row.groupNames.size,
                                checked = areAllGroupsSelected(row.groupNames, selectedGroups),
                                onClick = { onToggle(index) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { onFocusedIndexChange(index) }
                            )
                            is GuideGroupVisibleRow.Group -> GuideGroupChildRow(
                                label = com.grid.tv.domain.model.ChannelGroupIdentity.displayLabel(row.fullName),
                                checked = row.fullName in selectedGroups,
                                onClick = { onToggle(index) },
                                focusRequester = rowFocusRequesters[index],
                                onFocused = { onFocusedIndexChange(index) }
                            )
                        }
                    }
                }

                GuideFilterDoneButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    focusRequester = doneFocusRequester
                )
            }
        }
    }
}

@Composable
private fun GuideFilterDoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val background = if (rowFocused) Color(0xFF5AA3FF) else Color(0xFF3B8FFF)
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .then(
                if (focusRequester != null) {
                    Modifier.guideGroupRowTvFocus(
                        focusRequester = focusRequester,
                        onFocused = {},
                        onFocusVisualChanged = { rowFocused = it }
                    )
                } else {
                    Modifier.onFocusChanged { rowFocused = it.isFocused }
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
                .fillMaxSize()
                .border(
                    width = 2.dp,
                    color = if (rowFocused) EpgColors.FocusBorder else Color.Transparent,
                    shape = shape
                ),
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
}

@Composable
internal fun GuideGroupAllChannelsRow(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    blockRemoteActivation: Boolean = false,
) {
    GuideGroupTreeRowShell(
        label = "All channels",
        checked = checked,
        onClick = onClick,
        startPadding = 20.dp,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocused = onFocused,
        blockRemoteActivation = blockRemoteActivation,
    )
}

@Composable
internal fun GuideGroupCategoryRow(
    displayName: String,
    channelCount: Int,
    subGroupCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    var rowFocused by remember { mutableStateOf(false) }
    val background = when {
        rowFocused -> EpgColors.LiveGuideFocusBg
        expanded -> EpgColors.LiveGuideFocusBg
        else -> EpgColors.GridBg
    }
    val textColor = when {
        rowFocused -> EpgColors.LiveGuideFocus
        expanded -> EpgColors.LiveGuideFocusDim
        else -> EpgColors.TextSecondary
    }
    val shape = RoundedCornerShape(6.dp)
    val countLabel = categoryCountLabel(channelCount, subGroupCount)
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.guideGroupRowTvFocus(
                        focusRequester = focusRequester,
                        onFocused = onFocused,
                        onFocusVisualChanged = { rowFocused = it }
                    )
                } else {
                    Modifier.onFocusChanged { rowFocused = it.isFocused }
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
                .border(
                    width = 2.dp,
                    color = if (rowFocused) EpgColors.LiveGuideFocus else Color.Transparent,
                    shape = shape
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$displayName ($countLabel)",
                color = textColor,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (rowFocused) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "▼" else "▶",
                color = if (rowFocused) EpgColors.Accent else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun GuideGroupSelectAllRow(
    displayName: String,
    childCount: Int,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    GuideGroupTreeRowShell(
        label = "Select all $displayName ($childCount)",
        checked = checked,
        onClick = onClick,
        startPadding = 28.dp,
        italic = true,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocused = onFocused
    )
}

@Composable
internal fun GuideGroupChildRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    blockRemoteActivation: Boolean = false,
    showFavoriteStar: Boolean = false,
) {
    GuideGroupTreeRowShell(
        label = label,
        checked = checked,
        onClick = onClick,
        startPadding = 16.dp,
        modifier = modifier,
        focusRequester = focusRequester,
        onFocused = onFocused,
        blockRemoteActivation = blockRemoteActivation,
        showFavoriteStar = showFavoriteStar,
    )
}

@Composable
private fun GuideGroupTreeRowShell(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    startPadding: androidx.compose.ui.unit.Dp,
    italic: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    blockRemoteActivation: Boolean = false,
    showFavoriteStar: Boolean = false,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val background = when {
        rowFocused -> EpgColors.LiveGuideFocusBg
        checked -> EpgColors.LiveGuideFocusBg
        else -> EpgColors.GridBg
    }
    val textColor = when {
        rowFocused -> EpgColors.LiveGuideFocus
        checked -> EpgColors.LiveGuideFocusDim
        else -> EpgColors.TextSecondary
    }
    val shape = RoundedCornerShape(6.dp)
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (blockRemoteActivation) {
                    Modifier.onPreviewKeyEvent { event ->
                        isTvActivateKey(event) || isTvActivateKeyUp(event)
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (focusRequester != null) {
                    Modifier.guideGroupRowTvFocus(
                        focusRequester = focusRequester,
                        onFocused = onFocused,
                        onFocusVisualChanged = { rowFocused = it }
                    )
                } else {
                    Modifier.onFocusChanged { rowFocused = it.isFocused }
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
                .border(
                    width = 2.dp,
                    color = if (rowFocused) EpgColors.LiveGuideFocus else Color.Transparent,
                    shape = shape
                )
                .padding(start = startPadding, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontFamily = DmSansFamily,
                fontSize = if (italic) 13.sp else 14.sp,
                fontWeight = if (rowFocused) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showFavoriteStar) {
                Icon(
                    painter = painterResource(R.drawable.ic_star_filled),
                    contentDescription = "Favourited",
                    tint = FavoriteGold,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (checked) {
                Text(
                    text = "✓",
                    color = if (rowFocused) EpgColors.LiveGuideFocus else EpgColors.LiveGuideFocusDim,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private val FavoriteGold = Color(0xFFFFD700)
