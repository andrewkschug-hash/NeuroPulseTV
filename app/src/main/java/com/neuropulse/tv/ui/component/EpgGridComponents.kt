package com.neuropulse.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.player.StreamPlaybackStatus
import com.neuropulse.tv.player.userLabel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.ProgramGenre
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.util.DEFAULT_PROFILE_AVATAR_COLOR
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun dpPerMs(): Float = DpPerMinute / 60_000f

    fun widthForDurationMs(ms: Long): Dp = (ms * dpPerMs()).dp

    fun offsetForTime(time: Long, windowStart: Long): Dp =
        ((time - windowStart).coerceAtLeast(0) * dpPerMs()).dp

    fun timelineWidthMs(windowDurationMs: Long): Dp = (windowDurationMs * dpPerMs()).dp
}

fun formatEpgTime(epochMs: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))

fun formatEpgClock(epochMs: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))

fun formatEpgDay(epochMs: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = java.util.Calendar.getInstance()
    val isToday = cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    val datePart = SimpleDateFormat("M/d", Locale.getDefault()).format(Date(epochMs))
    return if (isToday) "Today $datePart" else {
        SimpleDateFormat("EEE M/d", Locale.getDefault()).format(Date(epochMs))
    }
}

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
    now: Long,
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
    isRecording: Boolean = false,
    activeRecordingTitle: String? = null,
    recordingHealth: com.neuropulse.tv.feature.recording.RecordingHealth = com.neuropulse.tv.feature.recording.RecordingHealth.RECORDING,
    recordingIndicatorFocused: Boolean = false,
    onRecordingIndicatorClick: () -> Unit = {},
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
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
                            text = formatEpgDay(now),
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 11.sp
                        )
                        Text(
                            text = formatEpgClock(now),
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
                onOpenSettings = onOpenSettings
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
    showBottomSeparator: Boolean = true,
    scanStatus: com.neuropulse.tv.domain.model.ChannelScanStatus? = null,
    lastCheckedLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isFocused) EpgColors.ChannelRowFocusBg else EpgColors.ChannelColumnBg
    val logoTint = if (isFocused) EpgColors.TextPrimary else EpgColors.TextSecondary
    val nameColor = if (isFocused) EpgColors.TextPrimary else EpgColors.TextSecondary
    val initials = channel.name.take(2).uppercase()

    Column(modifier = modifier.height(EpgLayout.RowHeight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(bgColor)
                .then(
                    if (isFocused) {
                        Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(0.dp))
                    } else {
                        Modifier
                    }
                ),
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
    modifier: Modifier = Modifier
) {
    val timeState = programTimeState(program, now)
    val (bgColor, textColor) = cellColors(timeState)
    val isAiring = timeState == ProgramTimeState.AIRING
    val effectiveBg = when {
        isFocused -> EpgColors.ChannelRowFocusBg
        isSelected -> EpgColors.SelectedFill.copy(alpha = 0.35f)
        else -> bgColor
    }
    val showTime = width.value >= 100f

    Box(
        modifier = modifier
            .width(width)
            .padding(end = EpgLayout.CellGap)
            .height(EpgLayout.RowHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFocused) {
                        Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(2.dp))
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(2.dp))
                .background(effectiveBg)
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
                    color = if (isSelected) EpgColors.TextPrimary else textColor,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
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
            }
        }
    }
}

@Composable
fun EpgTimelineHeader(
    windowStart: Long,
    windowDurationMs: Long,
    now: Long,
    modifier: Modifier = Modifier
) {
    val totalWidth = EpgLayout.timelineWidthMs(windowDurationMs)
    val slotMs = 30 * 60 * 1000L
    val slotCount = (windowDurationMs / slotMs).toInt()
    val showNow = now in windowStart..(windowStart + windowDurationMs)
    val nowOffset = if (showNow) EpgLayout.offsetForTime(now, windowStart) else 0.dp

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
                    modifier = Modifier.width(EpgLayout.widthForDurationMs(slotMs)),
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
    now: Long,
    scrollOffsetPx: Int = 0,
    modifier: Modifier = Modifier
) {
    if (now !in windowStart..(windowStart + windowDurationMs)) return
    val nowOffset = EpgLayout.offsetForTime(now, windowStart)
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
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EpgColors.Accent.copy(alpha = 0.15f),
            focusedContainerColor = EpgColors.Accent.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .height(32.dp)
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
            EpgActionButton(
                label = if (isFavorite) "★ Favourite" else "☆ Favourite",
                isFocused = detailActionFocused == 1,
                onClick = onFavorite,
                compact = true,
                labelColor = if (isFavorite) EpgColors.FavoriteStar else null
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
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        label = "epgActionScale"
    )
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EpgColors.GridBg,
            focusedContainerColor = EpgColors.GridBg
        ),
        modifier = Modifier
            .scale(scale)
            .height(if (compact) 32.dp else 36.dp)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) EpgColors.FocusBorder else EpgColors.BorderSubtle,
                shape = shape
            )
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
        val borderWidth = if (focused) 2.dp else 1.dp
        val textColor = when {
            focused -> EpgColors.TextPrimary
            active -> EpgColors.TextPrimary
            else -> EpgColors.TextDimmed
        }
        Surface(
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
                    .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
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
    Surface(
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
                width = if (isFocused) 2.dp else 1.dp,
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
