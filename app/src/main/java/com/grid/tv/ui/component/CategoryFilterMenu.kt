package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.grid.tv.feature.epg.ChannelCategoryFilter
import com.grid.tv.feature.epg.ChannelCategoryPresets
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private data class CategoryFilterMenuItem(
    val label: String,
    val menuIndex: Int,
    val section: String? = null
)

private val regionPresetIds = setOf("usa", "canada", "uk")

private fun buildCategoryFilterMenuItems(): List<CategoryFilterMenuItem> = buildList {
    add(CategoryFilterMenuItem("All channels", menuIndex = 0, section = "Filter by"))
    val regions = ChannelCategoryPresets.presets.filter { it.id in regionPresetIds }
    val categories = ChannelCategoryPresets.presets.filter { it.id !in regionPresetIds }
    regions.forEachIndexed { index, preset ->
        add(
            CategoryFilterMenuItem(
                label = preset.label,
                menuIndex = 1 + ChannelCategoryPresets.presets.indexOf(preset),
                section = if (index == 0) "Regions" else null
            )
        )
    }
    categories.forEachIndexed { index, preset ->
        add(
            CategoryFilterMenuItem(
                label = preset.label,
                menuIndex = 1 + ChannelCategoryPresets.presets.indexOf(preset),
                section = if (index == 0) "Categories" else null
            )
        )
    }
}

@Composable
fun CategoryFilterMenu(
    expanded: Boolean,
    focusedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val items = buildCategoryFilterMenuItems()
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
                GridFocusSurface(
                    onClick = { onSelect(item.menuIndex) },
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
                        containerColor = if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
                        focusedContainerColor = if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg
                    )
                ) {
                    Text(
                        text = item.label,
                        color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

fun categoryFilterMenuItemCount(): Int = 1 + ChannelCategoryPresets.presets.size

fun categoryFilterForMenuIndex(index: Int): ChannelCategoryFilter {
    if (index == 0) return ChannelCategoryFilter.All
    val presetCount = ChannelCategoryPresets.presets.size
    if (index in 1..presetCount) {
        return ChannelCategoryPresets.fromPreset(ChannelCategoryPresets.presets[index - 1].id)
    }
    return ChannelCategoryFilter.All
}

fun currentCategoryMenuIndex(filter: ChannelCategoryFilter): Int {
    if (!filter.isActive) return 0
    filter.presetId?.let { presetId ->
        val presetIndex = ChannelCategoryPresets.presets.indexOfFirst { it.id == presetId }
        return if (presetIndex >= 0) presetIndex + 1 else 0
    }
    filter.groupName?.let { groupName ->
        val presetIndex = ChannelCategoryPresets.presets.indexOfFirst { preset ->
            ChannelCategoryPresets.matches(preset.id, groupName)
        }
        return if (presetIndex >= 0) presetIndex + 1 else 0
    }
    return 0
}
