package com.neuropulse.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.ProgramGenre
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EpgLayout {
    val TopBarHeight = 56.dp
    val ChannelColumnWidth = 180.dp
    val RowHeight = 64.dp
    val TimelineHeaderHeight = 32.dp
    val DetailPanelHeight = 120.dp
    val DpPerMinute = 4f
    val ThirtyMinWidthDp = DpPerMinute * 30f
    val CellGap = 2.dp
    val ChannelLogoSize = 36.dp
    val NowLineWidth = 1.5.dp

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
    isRecordingActive: Boolean = false,
    onTabSelected: (EpgNavTab) -> Unit,
    onProfileClick: () -> Unit = {},
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.TopBarHeight)
            .background(EpgColors.Background)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatEpgClock(now),
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 16.dp)
        )

        if (isRecordingActive) {
            Row(
                modifier = Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "●", color = Color(0xFFFF3B3B), fontSize = 14.sp)
                Text(
                    text = "REC",
                    color = Color(0xFFFF3B3B),
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        GridNavIconRow(
            selectedTab = selectedTab,
            focusedIndex = focusedNavTabIndex,
            navFocused = navFocused,
            profileInitials = profileInitials,
            profileFocused = profileFocused,
            onTabSelected = onTabSelected,
            onProfileClick = onProfileClick,
            modifier = Modifier.weight(1f),
            trailing = { miniPlayer() }
        )
    }
}

enum class EpgNavTab(val glyph: String) {
    Home("⌂"),
    Search("⌕"),
    Recordings("●"),
    Settings("⚙"),
    Profile("◉")
}

@Composable
fun EpgChannelCell(
    channel: Channel,
    isFocused: Boolean,
    showBottomSeparator: Boolean = true,
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
                .background(bgColor),
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
                    sessions = if (channel.reliabilityScore == 50) 0 else 1
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
                    Text(
                        text = channel.number.toString(),
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp
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
    val effectiveBg = if (isSelected) EpgColors.SelectedFill.copy(alpha = 0.35f) else bgColor
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, label = "epgCellScale")
    val showTime = width.value >= 100f
    val isAiring = timeState == ProgramTimeState.AIRING

    Box(
        modifier = modifier
            .width(width)
            .height(if (isFocused) EpgLayout.RowHeight + 4.dp else EpgLayout.RowHeight)
            .scale(scale)
            .padding(end = EpgLayout.CellGap)
            .then(
                if (isFocused) Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(2.dp))
                else Modifier
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
        val nowOffset = EpgLayout.offsetForTime(now, windowStart)
        if (now in windowStart..(windowStart + windowDurationMs)) {
            Box(
                modifier = Modifier
                    .offset(x = nowOffset - 16.dp)
                    .width(32.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "NOW",
                    color = EpgColors.NowLine,
                    fontFamily = DmSansFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun EpgNowLine(
    windowStart: Long,
    windowDurationMs: Long,
    now: Long,
    modifier: Modifier = Modifier
) {
    if (now !in windowStart..(windowStart + windowDurationMs)) return
    val nowOffset = EpgLayout.offsetForTime(now, windowStart)
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .offset(x = nowOffset - EpgLayout.NowLineWidth / 2)
                .width(EpgLayout.NowLineWidth)
                .fillMaxHeight()
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(width = 8.dp, height = 6.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height)
                    lineTo(0f, 0f)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(path, EpgColors.NowLine, style = Fill)
            }
            Box(
                modifier = Modifier
                    .width(EpgLayout.NowLineWidth)
                    .weight(1f)
                    .background(EpgColors.NowLine)
            )
        }
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
            containerColor = Color.Red.copy(alpha = 0.15f),
            focusedContainerColor = Color.Red.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .height(32.dp)
            .border(1.dp, EpgColors.NowLine, RoundedCornerShape(6.dp))
    ) {
        Text(
            text = "● Live",
            color = EpgColors.NowLine,
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
    onRecord: () -> Unit,
    onFavorite: () -> Unit = {},
    onMoreInfo: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible || channel == null || program == null) return

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
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EpgColors.ChannelColumnBg)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = program.title,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatEpgTime(program.startTime)} – ${formatEpgTime(program.endTime)}  •  ${programDurationMinutes(program)} min",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    EpgTagPill(genreLabel(program.genre), EpgColors.HdBadgeBg, EpgColors.TextSecondary)
                    if (programTimeState(program, now) == ProgramTimeState.AIRING) {
                        EpgTagPill("HD", EpgColors.HdBadgeBg, EpgColors.TextSecondary)
                    }
                }
                Text(
                    text = program.description.ifBlank { "No description available." },
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EpgActionButton("▶ Watch", detailActionFocused == 0, onClick = onWatch) {
                    onActionFocusChange(0)
                }
                EpgActionButton("⏺ Record", detailActionFocused == 1, onClick = onRecord) {
                    onActionFocusChange(1)
                }
                EpgActionButton("★ Save", detailActionFocused == 2, onClick = onFavorite) {
                    onActionFocusChange(2)
                }
                EpgActionButton("ℹ Info", detailActionFocused == 3, onClick = onMoreInfo) {
                    onActionFocusChange(3)
                }
            }
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
private fun EpgActionButton(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .then(if (isFocused) Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp)) else Modifier)
            .focusable()
    ) {
        Text(
            text = label,
            fontFamily = DmSansFamily,
            fontSize = 13.sp
        )
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
            val display = if (chipFocused || selected) "[$label]" else label
            val bg = when {
                chipFocused -> EpgColors.Accent.copy(alpha = 0.25f)
                selected -> EpgColors.ChannelRowFocusBg
                else -> EpgColors.ChannelColumnBg
            }
            val borderColor = if (chipFocused) EpgColors.Accent else EpgColors.BorderSubtle
            Text(
                text = display,
                color = if (chipFocused || selected) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = if (chipFocused) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    .background(bg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
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
    val bg = if (isFocused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.RowHeight)
            .background(bg)
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = EpgColors.FocusBorder,
                shape = RoundedCornerShape(0.dp)
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
        if (thumbnailPath != null && java.io.File(thumbnailPath).exists()) {
            AsyncImage(
                model = java.io.File(thumbnailPath),
                contentDescription = title,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(72.dp, 44.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(72.dp, 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EpgColors.ChannelColumnBg),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = EpgColors.TextSecondary, fontSize = 18.sp)
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
            Text(
                text = badge,
                color = if (badge.contains("REC")) EpgColors.LiveBadge else EpgColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .background(EpgColors.HdBadgeBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                        onClick = { onAction(index) },
                        onFocus = { onActionFocusChange(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun EpgListEmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Schedule from the live guide or record a program in progress.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
