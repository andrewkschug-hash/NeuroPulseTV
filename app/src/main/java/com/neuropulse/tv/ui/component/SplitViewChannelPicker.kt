package com.neuropulse.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

private enum class SplitChannelPickerFilter {
    FAVORITES,
    LAST_WATCHED,
    ALL_CHANNELS
}

@Composable
fun SplitViewChannelPicker(
    favoriteChannels: List<Channel>,
    recentChannels: List<Channel>,
    allChannels: List<Channel>,
    excludeChannelIds: Set<Long>,
    onSelect: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    val playableFavorites = remember(favoriteChannels, excludeChannelIds) {
        favoriteChannels.filter { it.id !in excludeChannelIds && it.streamUrl.isNotBlank() }
    }
    val playableRecent = remember(recentChannels, excludeChannelIds) {
        recentChannels.filter { it.id !in excludeChannelIds && it.streamUrl.isNotBlank() }
    }
    val playableAll = remember(allChannels, excludeChannelIds) {
        allChannels
            .filter { it.id !in excludeChannelIds && it.streamUrl.isNotBlank() }
            .sortedWith(compareBy({ it.number }, { it.name }))
    }

    var selectedFilter by remember(playableFavorites, playableRecent) {
        mutableStateOf(
            when {
                playableFavorites.isNotEmpty() -> SplitChannelPickerFilter.FAVORITES
                playableRecent.isNotEmpty() -> SplitChannelPickerFilter.LAST_WATCHED
                else -> SplitChannelPickerFilter.ALL_CHANNELS
            }
        )
    }
    var allChannelsExpanded by remember { mutableStateOf(false) }

    val filterOptions = SplitChannelPickerFilter.entries
    val quickPickChannels = when (selectedFilter) {
        SplitChannelPickerFilter.FAVORITES -> playableFavorites
        SplitChannelPickerFilter.LAST_WATCHED -> playableRecent
        SplitChannelPickerFilter.ALL_CHANNELS -> emptyList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A24))
                .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Add stream",
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filterOptions.forEach { filter ->
                        SplitPickerFilterChip(
                            label = when (filter) {
                                SplitChannelPickerFilter.FAVORITES -> "Favorites"
                                SplitChannelPickerFilter.LAST_WATCHED -> "Last Watched"
                                SplitChannelPickerFilter.ALL_CHANNELS -> "All Channels"
                            },
                            selected = selectedFilter == filter,
                            onClick = {
                                selectedFilter = filter
                                allChannelsExpanded = filter == SplitChannelPickerFilter.ALL_CHANNELS
                            }
                        )
                    }
                }

                when (selectedFilter) {
                    SplitChannelPickerFilter.FAVORITES,
                    SplitChannelPickerFilter.LAST_WATCHED -> {
                        if (quickPickChannels.isEmpty()) {
                            Text(
                                text = when (selectedFilter) {
                                    SplitChannelPickerFilter.FAVORITES ->
                                        "No favorite channels yet. Star channels in the guide to see them here."
                                    else -> "No recently watched channels yet."
                                },
                                color = EpgColors.TextDimmed,
                                fontFamily = DmSansFamily,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(quickPickChannels, key = { _, channel -> channel.id }) { _, channel ->
                                    SplitPickerChannelRow(
                                        channel = channel,
                                        onClick = { onSelect(channel) }
                                    )
                                }
                            }
                        }
                    }
                    SplitChannelPickerFilter.ALL_CHANNELS -> {
                        SplitPickerDropdown(
                            label = "Browse all channels",
                            count = playableAll.size,
                            expanded = allChannelsExpanded,
                            onToggle = { allChannelsExpanded = !allChannelsExpanded }
                        )
                        AnimatedVisibility(
                            visible = allChannelsExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(playableAll, key = { _, channel -> channel.id }) { _, channel ->
                                    SplitPickerChannelRow(
                                        channel = channel,
                                        onClick = { onSelect(channel) }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    GlowFocusButton(onClick = onDismiss) {
                        Text(
                            text = "Close",
                            color = EpgColors.TextPrimary,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitPickerFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(20.dp)
    val containerColor = if (selected) {
        EpgColors.Accent.copy(alpha = 0.22f)
    } else {
        Color(0xFF252530)
    }
    GlowFocusButton(
        onClick = onClick,
        containerColor = containerColor,
        modifier = Modifier.border(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) EpgColors.Accent.copy(alpha = 0.55f) else EpgColors.BorderSubtle,
            shape = chipShape
        )
    ) {
        Text(
            text = label,
            color = if (selected) EpgColors.TextPrimary else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SplitPickerDropdown(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    GlowFocusButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$count channels",
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = EpgColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SplitPickerChannelRow(
    channel: Channel,
    onClick: () -> Unit
) {
    GlowFocusButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.number.toString(),
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = channel.name,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
