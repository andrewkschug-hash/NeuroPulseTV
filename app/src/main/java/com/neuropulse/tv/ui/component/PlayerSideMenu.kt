package com.neuropulse.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

enum class PlayerSideMenuSection { CHANNELS, SPORTS, ACTIONS }

sealed interface PlayerSideMenuFocusTarget {
    data object ChannelsHeader : PlayerSideMenuFocusTarget
    data class NearbyChannel(val index: Int) : PlayerSideMenuFocusTarget
    data object BrowseAll : PlayerSideMenuFocusTarget
    data object SportsHeader : PlayerSideMenuFocusTarget
    data class SportsChannel(val index: Int) : PlayerSideMenuFocusTarget
    data class Action(val index: Int) : PlayerSideMenuFocusTarget
}

data class PlayerSideMenuFocusState(
    val channelsHeader: Boolean = false,
    val nearbyChannelIndex: Int? = null,
    val browseAll: Boolean = false,
    val sportsHeader: Boolean = false,
    val sportsChannelIndex: Int? = null,
    val actionIndex: Int? = null
)

const val PlayerSideMenuCollapsibleChannelThreshold = 3

fun buildPlayerSideMenuFocusOrder(
    nearbyChannelCount: Int,
    sportsChannelCount: Int,
    actionCount: Int,
    channelsCollapsible: Boolean,
    channelsExpanded: Boolean,
    sportsExpanded: Boolean
): List<PlayerSideMenuFocusTarget> = buildList {
    if (channelsCollapsible) {
        add(PlayerSideMenuFocusTarget.ChannelsHeader)
        if (channelsExpanded) {
            for (i in 0 until nearbyChannelCount) add(PlayerSideMenuFocusTarget.NearbyChannel(i))
        }
    } else {
        for (i in 0 until nearbyChannelCount) add(PlayerSideMenuFocusTarget.NearbyChannel(i))
    }
    add(PlayerSideMenuFocusTarget.BrowseAll)
    if (sportsChannelCount > 0) {
        add(PlayerSideMenuFocusTarget.SportsHeader)
        if (sportsExpanded) {
            for (i in 0 until sportsChannelCount) add(PlayerSideMenuFocusTarget.SportsChannel(i))
        }
    }
    for (i in 0 until actionCount) add(PlayerSideMenuFocusTarget.Action(i))
}

fun playerSideMenuFocusState(target: PlayerSideMenuFocusTarget?): PlayerSideMenuFocusState =
    when (target) {
        PlayerSideMenuFocusTarget.ChannelsHeader ->
            PlayerSideMenuFocusState(channelsHeader = true)
        is PlayerSideMenuFocusTarget.NearbyChannel ->
            PlayerSideMenuFocusState(nearbyChannelIndex = target.index)
        PlayerSideMenuFocusTarget.BrowseAll ->
            PlayerSideMenuFocusState(browseAll = true)
        PlayerSideMenuFocusTarget.SportsHeader ->
            PlayerSideMenuFocusState(sportsHeader = true)
        is PlayerSideMenuFocusTarget.SportsChannel ->
            PlayerSideMenuFocusState(sportsChannelIndex = target.index)
        is PlayerSideMenuFocusTarget.Action ->
            PlayerSideMenuFocusState(actionIndex = target.index)
        null -> PlayerSideMenuFocusState()
    }

fun playerSideMenuFocusSection(target: PlayerSideMenuFocusTarget): PlayerSideMenuSection = when (target) {
    PlayerSideMenuFocusTarget.ChannelsHeader,
    is PlayerSideMenuFocusTarget.NearbyChannel,
    PlayerSideMenuFocusTarget.BrowseAll -> PlayerSideMenuSection.CHANNELS
    PlayerSideMenuFocusTarget.SportsHeader, is PlayerSideMenuFocusTarget.SportsChannel -> PlayerSideMenuSection.SPORTS
    is PlayerSideMenuFocusTarget.Action -> PlayerSideMenuSection.ACTIONS
}

data class PlayerSideMenuAction(
    val id: String,
    val label: String,
    val glyph: String? = null,
    val highlightStop: Boolean = false
)

private val PanelBg = Color(0xEB0D0D0D)
private val PanelWidth = 320.dp
const val PlayerSideMenuMaxSports = 8

@Composable
fun PlayerSideMenu(
    visible: Boolean,
    channels: List<Channel>,
    sportsChannels: List<Channel>,
    actions: List<PlayerSideMenuAction>,
    currentChannelId: Long?,
    focusState: PlayerSideMenuFocusState,
    channelsExpanded: Boolean,
    sportsExpanded: Boolean,
    flashingSection: PlayerSideMenuSection? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val sportsShown = sportsChannels.take(PlayerSideMenuMaxSports)
    val channelsCollapsible = channels.size > PlayerSideMenuCollapsibleChannelThreshold

    LaunchedEffect(focusState, visible) {
        if (!visible) return@LaunchedEffect
        val target = when {
            focusState.actionIndex != null -> scrollState.maxValue
            focusState.sportsHeader || focusState.sportsChannelIndex != null -> 280
            focusState.channelsHeader || focusState.browseAll -> 180
            else -> 0
        }
        scrollState.scrollTo(target)
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
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            Box(modifier = Modifier.width(PanelWidth).fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(20.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PanelBg)
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        if (channelsCollapsible) {
                            CollapsibleSectionHeader(
                                title = "Channels",
                                icon = null,
                                expanded = channelsExpanded,
                                focused = focusState.channelsHeader,
                                section = PlayerSideMenuSection.CHANNELS,
                                flashingSection = flashingSection,
                                activeSection = when {
                                    focusState.channelsHeader ||
                                        focusState.nearbyChannelIndex != null ||
                                        focusState.browseAll ->
                                        PlayerSideMenuSection.CHANNELS
                                    else -> null
                                }
                            )
                            AnimatedVisibility(
                                visible = channelsExpanded,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                            ) {
                                Column {
                                    channels.forEachIndexed { index, channel ->
                                        ChannelRow(
                                            channel = channel,
                                            focused = focusState.nearbyChannelIndex == index,
                                            selected = channel.id == currentChannelId,
                                            showLiveDot = false,
                                            pinnedCurrent = channel.id == currentChannelId
                                        )
                                    }
                                }
                            }
                        } else {
                            SectionHeader(
                                title = "Channels",
                                section = PlayerSideMenuSection.CHANNELS,
                                flashingSection = flashingSection,
                                activeSection = when {
                                    focusState.nearbyChannelIndex != null || focusState.browseAll ->
                                        PlayerSideMenuSection.CHANNELS
                                    else -> null
                                }
                            )
                            channels.forEachIndexed { index, channel ->
                                ChannelRow(
                                    channel = channel,
                                    focused = focusState.nearbyChannelIndex == index,
                                    selected = channel.id == currentChannelId,
                                    showLiveDot = false,
                                    pinnedCurrent = channel.id == currentChannelId
                                )
                            }
                        }
                        BrowseAllChannelsRow(focused = focusState.browseAll)
                        if (sportsShown.isNotEmpty()) {
                            MenuDivider(
                                highlighted = focusState.sportsHeader ||
                                    focusState.sportsChannelIndex != null ||
                                    focusState.actionIndex != null
                            )
                            CollapsibleSectionHeader(
                                title = "Sports Now",
                                icon = "⚽",
                                expanded = sportsExpanded,
                                focused = focusState.sportsHeader,
                                section = PlayerSideMenuSection.SPORTS,
                                flashingSection = flashingSection,
                                activeSection = when {
                                    focusState.sportsHeader || focusState.sportsChannelIndex != null ->
                                        PlayerSideMenuSection.SPORTS
                                    else -> null
                                }
                            )
                            AnimatedVisibility(
                                visible = sportsExpanded,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                            ) {
                                Column {
                                    sportsShown.forEachIndexed { index, channel ->
                                        ChannelRow(
                                            channel = channel,
                                            focused = focusState.sportsChannelIndex == index,
                                            selected = channel.id == currentChannelId,
                                            showLiveDot = true,
                                            pinnedCurrent = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                    MenuDivider(
                        highlighted = focusState.actionIndex != null
                    )
                    SectionHeader(
                        title = "Quick Actions",
                        section = PlayerSideMenuSection.ACTIONS,
                        flashingSection = flashingSection,
                        activeSection = if (focusState.actionIndex != null) {
                            PlayerSideMenuSection.ACTIONS
                        } else {
                            null
                        }
                    )
                    actions.forEachIndexed { index, action ->
                        ActionRow(
                            action = action,
                            focused = focusState.actionIndex == index
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDivider(highlighted: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(if (highlighted) 2.dp else 1.dp)
            .background(
                if (highlighted) EpgColors.Accent.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.08f)
            )
    )
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    focused: Boolean,
    section: PlayerSideMenuSection,
    flashingSection: PlayerSideMenuSection?,
    activeSection: PlayerSideMenuSection?,
    icon: String? = null
) {
    val isFlashing = flashingSection == section
    val isActive = activeSection == section
    val labelAlpha by animateFloatAsState(
        targetValue = when {
            focused || isFlashing -> 1f
            isActive -> 0.88f
            else -> 0.5f
        },
        animationSpec = tween(durationMillis = 300),
        label = "collapsibleHeaderAlpha"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "sectionChevronRotation"
    )
    val bg = if (focused) Color(0xFF1A1A28) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (focused) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(EpgColors.FocusBorder)
                )
            }
            if (icon != null) {
                Text(text = icon, fontSize = 11.sp, color = Color.White.copy(alpha = labelAlpha))
            }
            Text(
                text = title,
                color = if (focused || isFlashing) EpgColors.TextPrimary else EpgColors.TextDimmed.copy(alpha = labelAlpha),
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (focused || isFlashing) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "›",
            color = if (focused || isFlashing) EpgColors.Accent else EpgColors.TextDimmed.copy(alpha = labelAlpha),
            fontFamily = DmSansFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.rotate(chevronRotation)
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    section: PlayerSideMenuSection,
    icon: String? = null,
    focused: Boolean = false,
    flashingSection: PlayerSideMenuSection?,
    activeSection: PlayerSideMenuSection?
) {
    val isFlashing = flashingSection == section
    val isActive = activeSection == section
    val labelAlpha by animateFloatAsState(
        targetValue = when {
            focused || isFlashing -> 1f
            isActive -> 0.88f
            else -> 0.5f
        },
        animationSpec = tween(durationMillis = 300),
        label = "sectionHeaderAlpha"
    )
    val bg = if (focused) Color(0xFF1A1A28) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (focused) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(EpgColors.FocusBorder)
            )
        }
        if (icon != null) {
            Text(text = icon, fontSize = 11.sp, color = Color.White.copy(alpha = labelAlpha))
        }
        Text(
            text = title.uppercase(),
            color = if (focused || isFlashing) EpgColors.TextPrimary else EpgColors.TextDimmed.copy(alpha = labelAlpha),
            fontFamily = DmSansFamily,
            fontSize = 10.sp,
            fontWeight = if (focused || isFlashing) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    focused: Boolean,
    selected: Boolean,
    showLiveDot: Boolean,
    pinnedCurrent: Boolean
) {
    val bg = when {
        focused -> Color(0xFF1A1A28)
        selected || pinnedCurrent -> Color(0xFF151520)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
    ) {
        Box(
            modifier = Modifier
                .width(if (focused) 3.dp else 0.dp)
                .height(40.dp)
                .background(if (focused) EpgColors.FocusBorder else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = channel.number.toString(),
                color = if (focused) EpgColors.TextSecondary else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                modifier = Modifier.width(28.dp)
            )
            Text(
                text = channel.name,
                color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (pinnedCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showLiveDot) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(6.dp)
                        .background(EpgColors.LiveBadge, CircleShape)
                )
            } else if (selected) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(6.dp)
                        .background(EpgColors.Accent.copy(alpha = 0.7f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun BrowseAllChannelsRow(focused: Boolean) {
    val bg = if (focused) Color(0xFF1A1A28) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
    ) {
        Box(
            modifier = Modifier
                .width(if (focused) 3.dp else 0.dp)
                .height(36.dp)
                .background(if (focused) EpgColors.FocusBorder else Color.Transparent)
        )
        Text(
            text = "↓  Browse all channels",
            color = if (focused) EpgColors.TextPrimary else EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ActionRow(action: PlayerSideMenuAction, focused: Boolean) {
    val stopActive = action.highlightStop
    val bg = when {
        focused && stopActive -> Color.Red.copy(alpha = 0.35f)
        focused -> Color(0xFF1A1A28)
        stopActive -> Color.Red.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
    ) {
        Box(
            modifier = Modifier
                .width(if (focused) 3.dp else 0.dp)
                .height(44.dp)
                .background(
                    when {
                        focused && stopActive -> Color.Red
                        focused -> EpgColors.FocusBorder
                        else -> Color.Transparent
                    }
                )
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (action.glyph != null) {
                Text(
                    text = action.glyph,
                    color = when {
                        stopActive -> Color.Red
                        focused -> EpgColors.TextPrimary
                        else -> EpgColors.TextSecondary
                    },
                    fontSize = 16.sp
                )
            }
            Text(
                text = action.label,
                color = when {
                    stopActive -> Color.Red
                    focused -> EpgColors.TextPrimary
                    else -> EpgColors.TextSecondary
                },
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun visiblePlayerSideMenuSections(sportsCount: Int): List<PlayerSideMenuSection> = buildList {
    add(PlayerSideMenuSection.CHANNELS)
    if (sportsCount > 0) add(PlayerSideMenuSection.SPORTS)
    add(PlayerSideMenuSection.ACTIONS)
}

fun sectionSize(
    section: PlayerSideMenuSection,
    channelCount: Int,
    sportsCount: Int,
    actionCount: Int,
): Int = when (section) {
    PlayerSideMenuSection.CHANNELS -> channelCount + 1
    PlayerSideMenuSection.SPORTS -> if (sportsCount > 0) sportsCount + 1 else 0
    PlayerSideMenuSection.ACTIONS -> actionCount
}
