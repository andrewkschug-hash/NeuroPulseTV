package com.grid.tv.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.grid.tv.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import androidx.compose.material3.Icon
import coil.compose.AsyncImage
import com.grid.tv.domain.model.Channel
import com.grid.tv.player.StreamPlaybackStatus
import com.grid.tv.player.userLabel
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre
import com.grid.tv.ui.platform.touchTarget
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR
import com.grid.tv.domain.epg.EpgTime

/** Intervals for scoped EPG clock updates — avoids recomposing the full grid every second. */
object EpgNowTicker {
    const val CLOCK_INTERVAL_MS = 1_000L
    const val GRID_INTERVAL_MS = 30_000L
    const val WINDOW_SYNC_INTERVAL_MS = 60_000L
}

@Composable
fun rememberEpgNowMillis(intervalMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

object EpgLayout {
    val TopBarHeight = 72.dp
    val ChannelColumnWidth = 180.dp
    val RowHeight = 64.dp
    const val VisibleGuideRows = 6
    /** Minimum guide list height (~[VisibleGuideRows] rows); list expands to fill available space. */
    val GuideChannelListMinHeight = RowHeight * VisibleGuideRows
    val TimelineHeaderHeight = 36.dp
    val DetailPanelHeight = 84.dp
    val PreviewSectionHeight = 240.dp
    val PreviewInfoWidth = 280.dp
    val MiniPlayerWidth = 300.dp
    val MiniPlayerHeight = 169.dp
    val GuideHeaderBottomPadding = 8.dp
    val DpPerMinute = 4f
    val ThirtyMinWidthDp = DpPerMinute * 30f
    val CellGap = 2.dp
    val ChannelLogoSize = 36.dp
    val NowLineWidth = 2.dp
    val NowMarkerWidth = 64.dp

    private const val MinProgramDurationMs = 60_000L
    private const val MaxProgramDurationMs = 12 * 60 * 60 * 1000L
    private const val MaxTimelineDurationMs = 7 * 24 * 60 * 60 * 1000L

    fun dpPerMs(): Float = DpPerMinute / 60_000f

    fun sanitizeWindowDurationMs(windowDurationMs: Long): Long =
        windowDurationMs.coerceIn(MinProgramDurationMs, MaxTimelineDurationMs)

    fun programDurationMs(start: Long, end: Long, windowDurationMs: Long): Long {
        val raw = if (end >= start) end - start else 0L
        val windowCap = sanitizeWindowDurationMs(windowDurationMs)
        return raw.coerceIn(0L, windowCap)
            .coerceAtMost(MaxProgramDurationMs)
            .coerceAtLeast(MinProgramDurationMs)
    }

    fun widthForDurationMs(ms: Long, windowDurationMs: Long = ms.coerceAtLeast(MinProgramDurationMs)): Dp {
        val safeMs = ms.coerceIn(0L, sanitizeWindowDurationMs(windowDurationMs))
            .coerceAtMost(MaxProgramDurationMs)
        val maxDp = timelineWidthMs(windowDurationMs).value
        val rawDp = safeMs * dpPerMs()
        return rawDp.coerceIn(1f, maxDp.coerceAtLeast(1f)).dp
    }

    fun widthForProgram(start: Long, end: Long, windowDurationMs: Long): Dp =
        widthForDurationMs(programDurationMs(start, end, windowDurationMs), windowDurationMs)

    /** Pixel offset from [windowStart] using absolute epoch millis (local now from [System.currentTimeMillis]). */
    fun offsetForTime(time: Long, windowStart: Long, windowDurationMs: Long = MaxTimelineDurationMs): Dp {
        val windowCap = sanitizeWindowDurationMs(windowDurationMs)
        val offsetMs = (time - windowStart).coerceIn(0L, windowCap)
        val maxDp = timelineWidthMs(windowDurationMs).value
        return (offsetMs * dpPerMs()).coerceIn(0f, maxDp.coerceAtLeast(0f)).dp
    }

    fun offsetForLocalNow(windowStart: Long, windowDurationMs: Long, nowMs: Long = EpgTime.localNowMs()): Dp =
        offsetForTime(nowMs, windowStart, windowDurationMs)

    fun timelineWidthMs(windowDurationMs: Long): Dp {
        val safeWindow = sanitizeWindowDurationMs(windowDurationMs)
        return (safeWindow * dpPerMs()).coerceAtLeast(1f).dp
    }

    fun safeCellWidth(width: Dp, windowDurationMs: Long): Dp {
        val maxWidth = timelineWidthMs(windowDurationMs)
        return width.coerceIn(1.dp, maxWidth)
    }
}

fun formatEpgTime(epochMs: Long): String = EpgTime.formatWallClock(epochMs)

fun formatEpgClock(epochMs: Long): String = EpgTime.formatWallClock(epochMs)

fun formatEpgDay(epochMs: Long): String = EpgTime.formatWallClockDay(epochMs)

fun programDurationMinutes(program: Program): Int =
    ((program.endTime - program.startTime) / 60_000).toInt()

enum class ProgramTimeState { PAST, AIRING, FUTURE }

fun programTimeState(program: Program, now: Long): ProgramTimeState = when {
    now > program.endTime -> ProgramTimeState.PAST
    now in program.startTime..program.endTime -> ProgramTimeState.AIRING
    else -> ProgramTimeState.FUTURE
}

fun cellColors(state: ProgramTimeState): Pair<Color, Color> = when (state) {
    ProgramTimeState.PAST -> EpgColors.CellPast to EpgColors.CellPastText
    ProgramTimeState.AIRING -> EpgColors.CellAiringNow to EpgColors.TextPrimary
    ProgramTimeState.FUTURE -> EpgColors.CellFuture to EpgColors.TextFuture
}

fun genreLabel(genre: ProgramGenre): String = when (genre) {
    ProgramGenre.NEWS -> "News"
    ProgramGenre.SPORTS -> "Sports"
    ProgramGenre.MOVIES -> "Movies"
    ProgramGenre.KIDS -> "Kids"
    ProgramGenre.GENERAL -> "General"
}

@Composable
fun EpgTopBar(
    selectedTab: EpgNavTab,
    focusedNavTabIndex: Int = -1,
    navFocused: Boolean = false,
    profileFocused: Boolean = false,
    profileInitials: String = "?",
    profileAvatarColor: String = DEFAULT_PROFILE_AVATAR_COLOR,
    profileMenuExpanded: Boolean = false,
    profileMenuFocusIndex: Int = 0,
    onTabSelected: (EpgNavTab) -> Unit,
    onProfileClick: () -> Unit = {},
    onProfileMenuDismiss: () -> Unit = {},
    onSwitchAccounts: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onQuitApp: () -> Unit = {},
    isRecording: Boolean = false,
    activeRecordingTitle: String? = null,
    recordingHealth: com.grid.tv.feature.recording.RecordingHealth = com.grid.tv.feature.recording.RecordingHealth.RECORDING,
    recordingIndicatorFocused: Boolean = false,
    onRecordingIndicatorClick: () -> Unit = {},
    miniPlayer: @Composable () -> Unit,
    vodSearchFocused: Boolean = false,
    onVodSearchClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clockNow = rememberEpgNowMillis(EpgNowTicker.CLOCK_INTERVAL_MS)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavBarMinHeight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EpgColors.Background)
            )
            GridNavIconRow(
                selectedTab = selectedTab,
                focusedIndex = focusedNavTabIndex,
                navFocused = navFocused,
                profileInitials = profileInitials,
                profileFocused = profileFocused,
                profileAvatarColor = profileAvatarColor,
                onTabSelected = onTabSelected,
                onProfileClick = onProfileClick,
                modifier = Modifier.fillMaxWidth(),
                trailing = {
                    if (onVodSearchClick != null) {
                        VodTopBarSearchIcon(
                            focused = vodSearchFocused,
                            onClick = onVodSearchClick
                        )
                    }
                    if (isRecording) {
                        RecordingIndicatorChip(
                            title = activeRecordingTitle,
                            focused = recordingIndicatorFocused,
                            onClick = onRecordingIndicatorClick,
                            health = recordingHealth
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatEpgDay(clockNow),
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 11.sp
                        )
                        Text(
                            text = formatEpgClock(clockNow),
                            color = EpgColors.TextPrimary,
                            fontFamily = DmSansFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    miniPlayer()
                }
            )

            ProfileMenuDropdown(
                expanded = profileMenuExpanded,
                focusedIndex = profileMenuFocusIndex,
                onDismiss = onProfileMenuDismiss,
                onSwitchAccounts = onSwitchAccounts,
                onOpenSettings = onOpenSettings,
                onQuitApp = onQuitApp
            )
        }

        GridNavTooltipRow(
            visible = navFocused,
            focusedIndex = focusedNavTabIndex,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)
        )
    }
}

enum class EpgNavTab(val glyph: String, val label: String) {
    Guide("☰", "Guide"),
    Search("⌕", "Search"),
    Vod("▦", "VOD"),
    Movies("▦", "Movies"),
    Series("▤", "Series"),
    Favorites("★", "Favorites"),
    Home("⌂", "Home"),
    Recordings("●", "Recordings"),
    Settings("⚙", "Settings")
}

@Composable
fun EpgChannelCell(
    channel: Channel,
    isFocused: Boolean,
    isRowActive: Boolean = isFocused,
    showBottomSeparator: Boolean = true,
    scanStatus: com.grid.tv.domain.model.ChannelScanStatus? = null,
    lastCheckedLabel: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> EpgColors.ChannelRowFocusBg
            isRowActive -> EpgColors.ChannelRowFocusBg.copy(alpha = 0.45f)
            else -> EpgColors.ChannelColumnBg
        },
        animationSpec = tween(durationMillis = 120),
        label = "channelCellBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> EpgColors.FocusBorder
            isRowActive -> EpgColors.Accent.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "channelCellBorder"
    )
    val logoTint = if (isFocused || isRowActive) EpgColors.TextPrimary else EpgColors.TextSecondary
    val nameColor = if (isFocused || isRowActive) EpgColors.TextPrimary else EpgColors.TextSecondary
    val showAccentBar = isFocused || isRowActive
    val initials = channel.name.take(2).uppercase()
    val touchModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick).touchTarget()
    } else {
        Modifier
    }

    Column(modifier = modifier.heightIn(min = EpgLayout.RowHeight).height(EpgLayout.RowHeight).then(touchModifier)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(bgColor)
                .then(
                    if (showAccentBar) {
                        Modifier.border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(0.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAccentBar) {
                Box(
                    modifier = Modifier
                        .width(if (isFocused) 3.dp else 2.dp)
                        .fillMaxHeight()
                        .background(
                            if (isFocused) EpgColors.Accent else EpgColors.Accent.copy(alpha = 0.6f)
                        )
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(EpgLayout.ChannelLogoSize)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1A1A22))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(EpgLayout.ChannelLogoSize)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1A1A22)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = logoTint,
                            fontFamily = DmSansFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                ChannelHealthDot(
                    reliabilityScore = channel.reliabilityScore,
                    sessions = if (channel.reliabilityScore == 50) 0 else 1,
                    scanStatus = scanStatus
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = nameColor,
                        fontFamily = DmSansFamily,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleText = when {
                        isFocused && lastCheckedLabel != null -> lastCheckedLabel
                        !channel.playlistName.isNullOrBlank() -> channel.playlistName
                        else -> channel.number.toString()
                    }
                    Text(
                        text = subtitleText,
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = if (!channel.playlistName.isNullOrBlank() && lastCheckedLabel == null) 10.sp else 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (showBottomSeparator) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(EpgColors.RowSeparator)
            )
        }
    }
}

@Composable
fun EpgProgramCell(
    program: Program,
    width: Dp,
    now: Long,
    isFocused: Boolean,
    isSelected: Boolean,
    windowDurationMs: Long,
    canReplay: Boolean = false,
    isFuture: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timeState = programTimeState(program, now)
    val (baseBgColor, textColor) = cellColors(timeState)
    val isAiring = timeState == ProgramTimeState.AIRING
    val effectiveBg = when {
        isFocused -> EpgColors.ChannelRowFocusBg
        isSelected -> EpgColors.SelectedFill.copy(alpha = 0.35f)
        else -> baseBgColor
    }
    val animatedBg by animateColorAsState(
        targetValue = effectiveBg,
        animationSpec = tween(durationMillis = 120),
        label = "programCellBg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (isFocused) EpgColors.FocusBorder else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "programCellBorder"
    )
    val safeWidth = EpgLayout.safeCellWidth(width.coerceAtLeast(1.dp), windowDurationMs)
    val showTime = safeWidth.value >= 100f
    val touchModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick).touchTarget()
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .width(safeWidth)
            .padding(end = EpgLayout.CellGap)
            .height(EpgLayout.RowHeight.coerceAtLeast(1.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(touchModifier)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = animatedBorder,
                    shape = RoundedCornerShape(2.dp)
                )
                .clip(RoundedCornerShape(2.dp))
                .background(animatedBg)
                .padding(horizontal = 8.dp)
        ) {
            if (isAiring) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-8).dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(EpgColors.Accent)
                )
            }
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = program.title,
                    color = if (isFocused || isSelected) EpgColors.TextPrimary else textColor,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = if (isFocused) 2 else 1,
                    overflow = if (isFocused) TextOverflow.Visible else TextOverflow.Ellipsis
                )
                if (showTime) {
                    Text(
                        text = formatEpgTime(program.startTime),
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            if (isAiring && isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(EpgColors.LiveBadge, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (canReplay) {
                Text(
                    text = "⏪",
                    color = Color(0xFF60A5FA),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            } else if (isFuture) {
                Text(
                    text = "🔔",
                    color = EpgColors.TextDimmed,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
fun EpgNoInformationCell(
    width: Dp,
    isFocused: Boolean,
    windowDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val baseBg = EpgColors.CellFuture.copy(alpha = 0.65f)
    val animatedBg by animateColorAsState(
        targetValue = if (isFocused) EpgColors.ChannelRowFocusBg else baseBg,
        animationSpec = tween(durationMillis = 120),
        label = "noInfoCellBg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (isFocused) EpgColors.FocusBorder else EpgColors.BorderSubtle.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 120),
        label = "noInfoCellBorder"
    )

    val safeWidth = EpgLayout.safeCellWidth(width.coerceAtLeast(120.dp), windowDurationMs)

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .width(safeWidth)
            .padding(end = EpgLayout.CellGap)
            .height(EpgLayout.RowHeight.coerceAtLeast(1.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, animatedBorder, RoundedCornerShape(2.dp))
                .clip(RoundedCornerShape(2.dp))
                .background(animatedBg)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "No information",
                color = if (isFocused) EpgColors.TextPrimary else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EpgTimelineHeader(
    windowStart: Long,
    windowDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val now = rememberEpgNowMillis(EpgNowTicker.CLOCK_INTERVAL_MS)
    val totalWidth = EpgLayout.timelineWidthMs(windowDurationMs)
    val slotMs = 30 * 60 * 1000L
    val slotCount = (windowDurationMs / slotMs).toInt()
    val showNow = now in windowStart..(windowStart + windowDurationMs)
    val nowOffset = if (showNow) {
        EpgLayout.offsetForLocalNow(windowStart, windowDurationMs, now)
    } else {
        0.dp
    }

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(EpgLayout.TimelineHeaderHeight)
            .background(EpgColors.GridBg)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(slotCount + 1) { i ->
                val time = windowStart + i * slotMs
                val label = formatEpgTime(time)
                Box(
                    modifier = Modifier.width(EpgLayout.widthForDurationMs(slotMs, windowDurationMs)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = label,
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        if (showNow) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = nowOffset - EpgLayout.NowMarkerWidth / 2)
                    .width(EpgLayout.NowMarkerWidth),
                contentAlignment = Alignment.TopCenter
            ) {
                EpgNowArrow()
            }
        }
    }
}

@Composable
private fun EpgNowArrow(
    modifier: Modifier = Modifier,
    color: Color = EpgColors.Accent
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(width = 10.dp, height = 6.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2f, size.height)
            lineTo(0f, 0f)
            lineTo(size.width, 0f)
            close()
        }
        drawPath(path, color, style = Fill)
    }
}

@Composable
fun EpgNowLine(
    windowStart: Long,
    windowDurationMs: Long,
    scrollOffsetPx: Int = 0,
    modifier: Modifier = Modifier
) {
    val now = rememberEpgNowMillis(EpgNowTicker.CLOCK_INTERVAL_MS)
    if (now !in windowStart..(windowStart + windowDurationMs)) return
    val nowOffset = EpgLayout.offsetForLocalNow(windowStart, windowDurationMs, now)
    val scrollOffsetDp = with(LocalDensity.current) { scrollOffsetPx.toDp() }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset(x = nowOffset - scrollOffsetDp - EpgLayout.NowLineWidth / 2)
                .width(EpgLayout.NowLineWidth)
                .fillMaxHeight()
                .background(EpgColors.Accent)
        )
    }
}

@Composable
fun EpgJumpToLiveButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    GridFocusSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EpgColors.Accent.copy(alpha = 0.15f),
            focusedContainerColor = EpgColors.Accent.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .height(32.dp)
            .focusProperties { canFocus = false }
            .border(1.dp, EpgColors.Accent, RoundedCornerShape(6.dp))
    ) {
        Text(
            text = "● Live",
            color = EpgColors.Accent,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun EpgDetailPanel(
    channel: Channel?,
    program: Program?,
    now: Long,
    detailActionFocused: Int,
    onActionFocusChange: (Int) -> Unit,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    isFavorite: Boolean = false,
    visible: Boolean,
    streamStatus: StreamPlaybackStatus? = null,
    modifier: Modifier = Modifier
) {
    if (!visible || channel == null) return

    val programInfo = when {
        program != null -> {
            val timeLine =
                "${formatEpgTime(program.startTime)} – ${formatEpgTime(program.endTime)}  •  ${programDurationMinutes(program)} min"
            "${program.title}  •  $timeLine"
        }
        else -> "No EPG data available for this channel."
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.DetailPanelHeight)
            .background(Color(0xFF0D0D1A))
            .border(width = 0.5.dp, color = EpgColors.BorderSubtle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = programInfo,
                color = Color(0xFF9CA3AF),
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (program != null) {
                    if (programTimeState(program, now) == ProgramTimeState.AIRING) {
                        EpgTagPill("Live", EpgColors.HdBadgeBg, EpgColors.TextSecondary)
                    }
                    EpgTagPill(genreLabel(program.genre), EpgColors.HdBadgeBg, EpgColors.TextSecondary)
                } else {
                    EpgTagPill("Live", EpgColors.HdBadgeBg, EpgColors.TextSecondary)
                }
                streamStatus?.let { status ->
                    if (status.userLabel().isNotBlank()) {
                        StreamStatusBadge(status = status, compact = true)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EpgActionButton(
                label = "▶ Watch",
                isFocused = detailActionFocused == 0,
                onClick = onWatch,
                compact = true
            )
            EpgFavoriteActionButton(
                isFavorite = isFavorite,
                isFocused = detailActionFocused == 1,
                onClick = onFavorite,
                compact = true
            )
            EpgActionButton(
                label = "⏺ Record",
                isFocused = detailActionFocused == 2,
                onClick = onRecord,
                compact = true
            )
        }
    }
}

@Composable
private fun EpgTagPill(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        color = fg,
        fontFamily = DmSansFamily,
        fontSize = 10.sp,
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    )
}

@Composable
internal fun EpgActionButton(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    labelColor: Color? = null
) {
    val shape = RoundedCornerShape(6.dp)
    GridFocusSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EpgColors.GridBg,
            focusedContainerColor = EpgColors.GridBg
        ),
        modifier = Modifier
            .height(if (compact) 32.dp else 36.dp)
            .tvFocusBorder(focused = isFocused, shape = shape)
            .focusProperties { canFocus = false }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 8.dp)
        ) {
            Text(
                text = label,
                fontFamily = DmSansFamily,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor ?: EpgColors.TextPrimary,
                maxLines = 1
            )
        }
    }
}

private val FavoriteGold = Color(0xFFFFD700)

@Composable
internal fun EpgFavoriteActionButton(
    isFavorite: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    val starTint by animateColorAsState(
        targetValue = if (isFavorite) FavoriteGold else EpgColors.TextSecondary,
        animationSpec = tween(150),
        label = "favoriteStarTint"
    )
    GridFocusSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EpgColors.GridBg,
            focusedContainerColor = EpgColors.GridBg
        ),
        modifier = modifier
            .height(if (compact) 32.dp else 36.dp)
            .tvFocusBorder(focused = isFocused, shape = shape)
            .focusProperties { canFocus = false }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_border
                ),
                contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                tint = starTint,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
            Text(
                text = "Favourite",
                fontFamily = DmSansFamily,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isFavorite) FavoriteGold else EpgColors.TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EpgChipFilterBar(
    labels: List<String>,
    activeIndex: Int,
    focusedIndex: Int,
    barFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == activeIndex
            val chipFocused = barFocused && index == focusedIndex
            val bg = when {
                chipFocused -> EpgColors.Accent.copy(alpha = 0.25f)
                selected -> EpgColors.ChannelRowFocusBg
                else -> EpgColors.ChannelColumnBg
            }
            val borderColor = if (chipFocused) EpgColors.Accent else EpgColors.BorderSubtle
            Text(
                text = label,
                color = if (chipFocused || selected) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = if (chipFocused || selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    .background(bg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun EpgCategoryFilterChip(
    label: String,
    active: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    headerStyle: Boolean = false
) {
    if (headerStyle) {
        val display = if (active) label else "Filter"
        val bg = when {
            focused -> EpgColors.Accent.copy(alpha = 0.22f)
            active -> EpgColors.ChannelRowFocusBg
            else -> Color.Transparent
        }
        val borderColor = when {
            focused -> EpgColors.Accent
            active -> EpgColors.Accent.copy(alpha = 0.55f)
            else -> EpgColors.BorderSubtle.copy(alpha = 0.45f)
        }
        val textColor = when {
            focused -> EpgColors.TextPrimary
            active -> EpgColors.TextPrimary
            else -> EpgColors.TextDimmed
        }
        GridFocusSurface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = bg,
                focusedContainerColor = bg
            )
        ) {
            Row(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(6.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "⊞",
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 14.sp
                )
                Text(
                    text = display,
                    color = textColor,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    fontWeight = if (focused || active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
        return
    }

    val display = if (active) "⊞ $label" else "⊞ Filter"
    val chipHeight = 36.dp
    val bg = when {
        focused -> EpgColors.Accent.copy(alpha = 0.25f)
        active -> EpgColors.ChannelRowFocusBg
        else -> EpgColors.ChannelColumnBg
    }
    val borderColor = when {
        focused -> EpgColors.Accent
        active -> EpgColors.Accent.copy(alpha = 0.45f)
        else -> EpgColors.BorderSubtle
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier.height(chipHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = display,
                color = if (focused || active) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                fontWeight = if (focused || active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EpgFilterBar(
    groupNames: List<String>,
    activeGroupIndex: Int,
    focusedIndex: Int,
    barFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val labels = buildList {
        add("All")
        add("★ Favorites")
        addAll(groupNames)
        add("+ Group")
    }
    EpgChipFilterBar(
        labels = labels,
        activeIndex = activeGroupIndex,
        focusedIndex = focusedIndex,
        barFocused = barFocused,
        modifier = modifier
    )
}

@Composable
fun RecordingsListRow(
    title: String,
    subtitle: String,
    badge: String?,
    thumbnailPath: String?,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        isFocused -> EpgColors.ChannelRowFocusBg
        else -> Color(0xFF13131A)
    }
    val borderColor = when {
        isFocused -> EpgColors.Accent
        else -> EpgColors.BorderSubtle.copy(alpha = 0.55f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.RowHeight)
            .clip(shape)
            .background(bg, shape)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = shape
            )
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFocused) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(EpgColors.Accent)
            )
        }
        if (thumbnailPath != null && java.io.File(thumbnailPath).exists()) {
            AsyncImage(
                model = java.io.File(thumbnailPath),
                contentDescription = title,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(72.dp, 44.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(72.dp, 44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A22)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = EpgColors.TextSecondary, fontSize = 16.sp)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = title,
                color = if (isFocused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (badge != null) {
            val isRec = badge.contains("REC")
            Text(
                text = badge,
                color = if (isRec) EpgColors.LiveBadge else EpgColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .border(
                        1.dp,
                        if (isRec) EpgColors.LiveBadge.copy(alpha = 0.5f) else EpgColors.Accent.copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (isRec) EpgColors.LiveBadge.copy(alpha = 0.12f) else EpgColors.Accent.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun RecordingsDetailPanel(
    title: String,
    subtitle: String,
    meta: String,
    thumbnailPath: String?,
    detailActionFocused: Int,
    actions: List<String>,
    onActionFocusChange: (Int) -> Unit,
    onAction: (Int) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.DetailPanelHeight)
            .background(EpgColors.DetailPanelBg)
            .border(width = 0.5.dp, color = EpgColors.BorderSubtle)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbnailPath != null && java.io.File(thumbnailPath).exists()) {
                AsyncImage(
                    model = java.io.File(thumbnailPath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(EpgColors.ChannelColumnBg),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(EpgColors.ChannelColumnBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = EpgColors.TextSecondary, fontSize = 16.sp)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = meta,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { index, label ->
                    EpgActionButton(
                        label = label,
                        isFocused = detailActionFocused == index,
                        onClick = { onAction(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun EpgListEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    hint: String = "Schedule from the live guide or record a program in progress.",
    icon: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Text(
                    text = icon,
                    fontSize = 48.sp,
                    color = EpgColors.TextDimmed,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = message,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = hint,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
