package com.grid.tv.ui.component

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.domain.model.Channel
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

enum class PlayerSideMenuSection { WATCH_HISTORY, FAVORITES, ACTIONS }

sealed interface PlayerSideMenuFocusTarget {
    data object WatchHistoryHeader : PlayerSideMenuFocusTarget
    data class RecentChannel(val index: Int) : PlayerSideMenuFocusTarget
    data object FavoritesHeader : PlayerSideMenuFocusTarget
    data class FavoriteChannel(val index: Int) : PlayerSideMenuFocusTarget
    data class Action(val index: Int) : PlayerSideMenuFocusTarget
}

data class PlayerSideMenuFocusState(
    val watchHistoryHeader: Boolean = false,
    val recentChannelIndex: Int? = null,
    val favoritesHeader: Boolean = false,
    val favoriteChannelIndex: Int? = null,
    val actionIndex: Int? = null
)

const val PlayerSideMenuMaxWatchHistory = 5
const val PlayerSideMenuMaxFavorites = 8

fun buildPlayerSideMenuFocusOrder(
    recentChannelCount: Int,
    favoriteChannelCount: Int,
    actionCount: Int,
    watchHistoryExpanded: Boolean,
    favoritesExpanded: Boolean
): List<PlayerSideMenuFocusTarget> = buildList {
    add(PlayerSideMenuFocusTarget.WatchHistoryHeader)
    if (watchHistoryExpanded) {
        for (i in 0 until recentChannelCount) add(PlayerSideMenuFocusTarget.RecentChannel(i))
    }
    add(PlayerSideMenuFocusTarget.FavoritesHeader)
    if (favoritesExpanded) {
        for (i in 0 until favoriteChannelCount) add(PlayerSideMenuFocusTarget.FavoriteChannel(i))
    }
    for (i in 0 until actionCount) add(PlayerSideMenuFocusTarget.Action(i))
}

fun playerSideMenuFocusState(target: PlayerSideMenuFocusTarget?): PlayerSideMenuFocusState =
    when (target) {
        PlayerSideMenuFocusTarget.WatchHistoryHeader ->
            PlayerSideMenuFocusState(watchHistoryHeader = true)
        is PlayerSideMenuFocusTarget.RecentChannel ->
            PlayerSideMenuFocusState(recentChannelIndex = target.index)
        PlayerSideMenuFocusTarget.FavoritesHeader ->
            PlayerSideMenuFocusState(favoritesHeader = true)
        is PlayerSideMenuFocusTarget.FavoriteChannel ->
            PlayerSideMenuFocusState(favoriteChannelIndex = target.index)
        is PlayerSideMenuFocusTarget.Action ->
            PlayerSideMenuFocusState(actionIndex = target.index)
        null -> PlayerSideMenuFocusState()
    }

fun playerSideMenuFocusSection(target: PlayerSideMenuFocusTarget): PlayerSideMenuSection = when (target) {
    PlayerSideMenuFocusTarget.WatchHistoryHeader,
    is PlayerSideMenuFocusTarget.RecentChannel -> PlayerSideMenuSection.WATCH_HISTORY
    PlayerSideMenuFocusTarget.FavoritesHeader, is PlayerSideMenuFocusTarget.FavoriteChannel ->
        PlayerSideMenuSection.FAVORITES
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

@Composable
fun PlayerSideMenu(
    visible: Boolean,
    watchHistoryChannels: List<Channel>,
    favoriteChannels: List<Channel>,
    actions: List<PlayerSideMenuAction>,
    currentChannelId: Long?,
    focusState: PlayerSideMenuFocusState,
    watchHistoryExpanded: Boolean,
    favoritesExpanded: Boolean,
    flashingSection: PlayerSideMenuSection? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val favoritesShown = favoriteChannels.take(PlayerSideMenuMaxFavorites)
    val historyShown = watchHistoryChannels.take(PlayerSideMenuMaxWatchHistory)
    val density = LocalDensity.current

    LaunchedEffect(
        focusState,
        visible,
        watchHistoryExpanded,
        favoritesExpanded,
        historyShown.size,
        favoritesShown.size
    ) {
        if (!visible) return@LaunchedEffect
        val rowPx = with(density) { 42.dp.roundToPx() }
        val headerPx = with(density) { 36.dp.roundToPx() }
        val dividerPx = with(density) { 22.dp.roundToPx() }
        val historyBlockPx = headerPx + dividerPx +
            if (watchHistoryExpanded) historyShown.size * rowPx else 0

        val target = when {
            focusState.actionIndex != null -> scrollState.maxValue
            focusState.favoriteChannelIndex != null -> {
                historyBlockPx + headerPx + focusState.favoriteChannelIndex * rowPx
            }
            focusState.favoritesHeader -> historyBlockPx
            focusState.recentChannelIndex != null -> {
                headerPx + focusState.recentChannelIndex * rowPx
            }
            focusState.watchHistoryHeader -> 0
            else -> 0
        }.coerceIn(0, scrollState.maxValue)
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
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                    ) {
                        CollapsibleSectionHeader(
                            title = "Watch History",
                            icon = "🕐",
                            expanded = watchHistoryExpanded,
                            focused = focusState.watchHistoryHeader,
                            section = PlayerSideMenuSection.WATCH_HISTORY,
                            flashingSection = flashingSection,
                            activeSection = when {
                                focusState.watchHistoryHeader ||
                                    focusState.recentChannelIndex != null ->
                                    PlayerSideMenuSection.WATCH_HISTORY
                                else -> null
                            }
                        )
                        AnimatedVisibility(
                            visible = watchHistoryExpanded,
                            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                        ) {
                            Column {
                                if (historyShown.isEmpty()) {
                                    Text(
                                        text = "No recent channels yet",
                                        color = EpgColors.TextDimmed,
                                        fontFamily = DmSansFamily,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                } else {
                                    historyShown.forEachIndexed { index, channel ->
                                        ChannelRow(
                                            channel = channel,
                                            focused = focusState.recentChannelIndex == index,
                                            selected = channel.id == currentChannelId,
                                            showLiveDot = false,
                                            pinnedCurrent = channel.id == currentChannelId
                                        )
                                    }
                                }
                            }
                        }
                        MenuDivider(
                            highlighted = focusState.favoritesHeader ||
                                focusState.favoriteChannelIndex != null ||
                                focusState.actionIndex != null
                        )
                        CollapsibleSectionHeader(
                            title = "My Favorites",
                            icon = "★",
                            expanded = favoritesExpanded,
                            focused = focusState.favoritesHeader,
                            section = PlayerSideMenuSection.FAVORITES,
                            flashingSection = flashingSection,
                            activeSection = when {
                                focusState.favoritesHeader || focusState.favoriteChannelIndex != null ->
                                    PlayerSideMenuSection.FAVORITES
                                else -> null
                            }
                        )
                        AnimatedVisibility(
                            visible = favoritesExpanded,
                            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                        ) {
                            Column {
                                favoritesShown.forEachIndexed { index, channel ->
                                    ChannelRow(
                                        channel = channel,
                                        focused = focusState.favoriteChannelIndex == index,
                                        selected = channel.id == currentChannelId,
                                        showLiveDot = false,
                                        pinnedCurrent = channel.id == currentChannelId
                                    )
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

fun visiblePlayerSideMenuSections(): List<PlayerSideMenuSection> = listOf(
    PlayerSideMenuSection.WATCH_HISTORY,
    PlayerSideMenuSection.FAVORITES,
    PlayerSideMenuSection.ACTIONS
)

fun sectionSize(
    section: PlayerSideMenuSection,
    watchHistoryCount: Int,
    favoriteCount: Int,
    actionCount: Int,
): Int = when (section) {
    PlayerSideMenuSection.WATCH_HISTORY -> watchHistoryCount + 1
    PlayerSideMenuSection.FAVORITES -> favoriteCount + 1
    PlayerSideMenuSection.ACTIONS -> actionCount
}
