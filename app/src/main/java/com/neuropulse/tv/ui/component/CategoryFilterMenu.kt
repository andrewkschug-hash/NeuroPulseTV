package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.feature.epg.ChannelCategoryPresets
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

data class CategoryFilterMenuItem(
    val label: String,
    val section: String? = null
)

@Composable
fun CategoryFilterMenu(
    expanded: Boolean,
    focusedIndex: Int,
    playlistGroups: List<String>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val items = buildList {
        add(CategoryFilterMenuItem("All channels", section = "Filter by"))
        ChannelCategoryPresets.presets.forEach { preset ->
            add(CategoryFilterMenuItem(preset.label))
        }
        if (playlistGroups.isNotEmpty()) {
            playlistGroups.forEach { group ->
                add(
                    CategoryFilterMenuItem(
                        label = group,
                        section = if (group == playlistGroups.first()) "Playlist groups" else null
                    )
                )
            }
        }
    }

    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = modifier
                .padding(top = 108.dp, end = 24.dp)
                .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEachIndexed { index, item ->
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
                val focused = index == focusedIndex
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
                        focusedContainerColor = EpgColors.ChannelRowFocusBg
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
                            .then(
                                if (focused) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

fun categoryFilterMenuItemCount(playlistGroups: List<String>): Int =
    1 + ChannelCategoryPresets.presets.size + playlistGroups.size

fun categoryFilterForMenuIndex(index: Int, playlistGroups: List<String>): com.neuropulse.tv.feature.epg.ChannelCategoryFilter {
    if (index == 0) return com.neuropulse.tv.feature.epg.ChannelCategoryFilter.All
    val presetCount = ChannelCategoryPresets.presets.size
    if (index <= presetCount) {
        return ChannelCategoryPresets.fromPreset(ChannelCategoryPresets.presets[index - 1].id)
    }
    val groupIndex = index - 1 - presetCount
    return ChannelCategoryPresets.fromGroup(playlistGroups[groupIndex])
}

fun currentCategoryMenuIndex(
    filter: com.neuropulse.tv.feature.epg.ChannelCategoryFilter,
    playlistGroups: List<String>
): Int {
    if (!filter.isActive) return 0
    filter.presetId?.let { presetId ->
        val presetIndex = ChannelCategoryPresets.presets.indexOfFirst { it.id == presetId }
        return if (presetIndex >= 0) presetIndex + 1 else 0
    }
    filter.groupName?.let { groupName ->
        val groupIndex = playlistGroups.indexOfFirst { it.equals(groupName, ignoreCase = true) }
        return if (groupIndex >= 0) 1 + ChannelCategoryPresets.presets.size + groupIndex else 0
    }
    return 0
}
