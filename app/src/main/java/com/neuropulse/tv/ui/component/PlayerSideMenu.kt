package com.neuropulse.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

enum class PlayerSideMenuSection { CHANNELS, SPORTS, NEWS, ACTIONS }

data class PlayerSideMenuSportItem(
    val channel: Channel,
    val programTitle: String
)

data class PlayerSideMenuAction(
    val id: String,
    val label: String,
    val glyph: String? = null
)

@Composable
fun PlayerSideMenu(
    visible: Boolean,
    channels: List<Channel>,
    sportsItems: List<PlayerSideMenuSportItem>,
    newsChannels: List<Channel>,
    actions: List<PlayerSideMenuAction>,
    currentChannelId: Long?,
    focusedSection: PlayerSideMenuSection,
    focusedIndex: Int,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedSection, focusedIndex, visible) {
        if (!visible || focusedSection == PlayerSideMenuSection.ACTIONS) return@LaunchedEffect
        val row = sectionStartRow(
            focusedSection,
            channels.size,
            sportsItems.size,
            newsChannels.size
        ) + focusedIndex
        listState.animateScrollToItem(row.coerceAtLeast(0))
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier.fillMaxSize()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(EpgColors.Background.copy(alpha = 0.45f))
            )
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(EpgColors.DetailPanelBg)
                    .border(width = 1.dp, color = EpgColors.BorderSubtle)
                    .padding(vertical = 16.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        SectionHeader("Channels")
                    }
                    itemsIndexed(channels, key = { _, ch -> "ch-${ch.id}" }) { index, channel ->
                        ChannelRow(
                            channel = channel,
                            subtitle = null,
                            focused = focusedSection == PlayerSideMenuSection.CHANNELS && index == focusedIndex,
                            selected = channel.id == currentChannelId
                        )
                    }
                    if (sportsItems.isNotEmpty()) {
                        item { SectionHeader("Live Sports") }
                        itemsIndexed(sportsItems, key = { _, item -> "sport-${item.channel.id}" }) { index, item ->
                            ChannelRow(
                                channel = item.channel,
                                subtitle = item.programTitle,
                                focused = focusedSection == PlayerSideMenuSection.SPORTS && index == focusedIndex,
                                selected = item.channel.id == currentChannelId
                            )
                        }
                    }
                    if (newsChannels.isNotEmpty()) {
                        item { SectionHeader("News") }
                        itemsIndexed(newsChannels, key = { _, ch -> "news-${ch.id}" }) { index, channel ->
                            ChannelRow(
                                channel = channel,
                                subtitle = null,
                                focused = focusedSection == PlayerSideMenuSection.NEWS && index == focusedIndex,
                                selected = channel.id == currentChannelId
                            )
                        }
                    }
                }
                SectionHeader("Quick actions")
                actions.forEachIndexed { index, action ->
                    ActionRow(
                        action = action,
                        focused = focusedSection == PlayerSideMenuSection.ACTIONS && index == focusedIndex
                    )
                }
                Text(
                    text = "← Close menu",
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = if (title == "Quick actions") EpgColors.TextSecondary else EpgColors.TextPrimary,
        fontFamily = DmSansFamily,
        fontSize = if (title == "Quick actions") 12.sp else 16.sp,
        fontWeight = if (title == "Quick actions") FontWeight.Normal else FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ChannelRow(
    channel: Channel,
    subtitle: String?,
    focused: Boolean,
    selected: Boolean
) {
    val bg = when {
        focused -> EpgColors.ChannelRowFocusBg
        selected -> EpgColors.Accent.copy(alpha = 0.15f)
        else -> EpgColors.GridBg
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .then(
                if (focused) {
                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = channel.number.toString(),
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                modifier = Modifier.width(28.dp)
            )
            Text(
                text = channel.name,
                color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text(text = "●", color = EpgColors.Accent, fontSize = 10.sp)
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 38.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun ActionRow(action: PlayerSideMenuAction, focused: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
                RoundedCornerShape(6.dp)
            )
            .then(
                if (focused) {
                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (action.glyph != null) {
            Text(
                text = action.glyph,
                color = if (focused) EpgColors.Accent else EpgColors.TextSecondary,
                fontSize = 18.sp
            )
        }
        Text(
            text = action.label,
            color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
    }
}

fun visiblePlayerSideMenuSections(
    sportsCount: Int,
    newsCount: Int
): List<PlayerSideMenuSection> = buildList {
    add(PlayerSideMenuSection.CHANNELS)
    if (sportsCount > 0) add(PlayerSideMenuSection.SPORTS)
    if (newsCount > 0) add(PlayerSideMenuSection.NEWS)
    add(PlayerSideMenuSection.ACTIONS)
}

fun sectionSize(
    section: PlayerSideMenuSection,
    channelCount: Int,
    sportsCount: Int,
    newsCount: Int,
    actionCount: Int
): Int = when (section) {
    PlayerSideMenuSection.CHANNELS -> channelCount
    PlayerSideMenuSection.SPORTS -> sportsCount
    PlayerSideMenuSection.NEWS -> newsCount
    PlayerSideMenuSection.ACTIONS -> actionCount
}

private fun sectionStartRow(
    section: PlayerSideMenuSection,
    channelCount: Int,
    sportsCount: Int,
    newsCount: Int
): Int {
    var row = 1 // Channels header
    if (section == PlayerSideMenuSection.CHANNELS) return row
    row += channelCount
    if (sportsCount > 0) {
        row += 1 // Sports header
        if (section == PlayerSideMenuSection.SPORTS) return row
        row += sportsCount
    }
    if (newsCount > 0) {
        row += 1 // News header
        if (section == PlayerSideMenuSection.NEWS) return row
        row += newsCount
    }
    return row
}
