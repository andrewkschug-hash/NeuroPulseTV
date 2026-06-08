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
    val ChannelColumnWidth = 160.dp
    val RowHeight = 64.dp
    val TimelineHeaderHeight = 32.dp
    val DetailPanelHeight = 120.dp
    val ThirtyMinWidthDp = 200f
    val CellGap = 2.dp
    val ChannelLogoSize = 32.dp
    val NowLineWidth = 1.5.dp

    fun dpPerMs(): Float = ThirtyMinWidthDp / (30 * 60 * 1000f)

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
    ProgramTimeState.PAST -> EpgColors.CellPast to EpgColors.TextDimmed
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
    watchingChannel: Channel?,
    currentProgram: Program?,
    now: Long,
    selectedTab: EpgNavTab,
    focusedNavTabIndex: Int = -1,
    navFocused: Boolean = false,
    onTabSelected: (EpgNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.TopBarHeight)
            .background(EpgColors.Background)
            .padding(start = 16.dp, end = MiniPlayerLayout.ReservedWidth),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (watchingChannel != null) {
                AsyncImage(
                    model = watchingChannel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 80.dp, height = 45.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(EpgColors.ChannelColumnBg)
                )
                Column {
                    Text(
                        text = "${watchingChannel.name} • ${watchingChannel.number}",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentProgram?.title ?: "No program info",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = formatEpgClock(now),
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EpgNavTab.entries.forEachIndexed { index, tab ->
                EpgTabIcon(
                    tab = tab,
                    selected = tab == selectedTab,
                    focused = navFocused && index == focusedNavTabIndex,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

enum class EpgNavTab(val icon: String) {
    Home("⌂"),
    Search("⌕"),
    Recordings("⏺"),
    Settings("⚙"),
    Profile("◉")
}

@Composable
private fun EpgTabIcon(
    tab: EpgNavTab,
    selected: Boolean,
    focused: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .then(
                if (focused) Modifier.border(2.dp, EpgColors.Accent, RoundedCornerShape(4.dp))
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = EpgColors.GridBg
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tab.icon,
                fontSize = 18.sp,
                color = if (selected) EpgColors.Accent else EpgColors.TextSecondary
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(20.dp)
                        .height(2.dp)
                        .background(EpgColors.Accent)
                )
            }
        }
    }
}

@Composable
fun EpgChannelCell(
    channel: Channel,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isFocused) EpgColors.GridBg else EpgColors.ChannelColumnBg
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.RowHeight)
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(EpgLayout.ChannelLogoSize)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A22))
                    .then(
                        if (isFocused) Modifier.border(1.dp, EpgColors.Accent, RoundedCornerShape(4.dp))
                        else Modifier
                    )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = channel.number.toString(),
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 11.sp
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
    val showTime = width.value >= 80f
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
            .padding(horizontal = 8.dp, vertical = 6.dp)
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
                fontSize = 14.sp,
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
        if (isAiring) {
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
            Column(
                modifier = Modifier.offset(x = nowOffset),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NOW",
                    color = EpgColors.Accent,
                    fontFamily = DmSansFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
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
fun EpgTimeJumpPills(
    loading: Boolean,
    onPrev2h: () -> Unit,
    onLive: () -> Unit,
    onNext2h: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EpgPillButton(text = "← 2h", onClick = onPrev2h)
        EpgPillButton(text = "Live", onClick = onLive, accent = true)
        EpgPillButton(text = "2h →", onClick = onNext2h)
        if (loading) {
            Text(
                text = "…",
                color = EpgColors.TextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun EpgPillButton(
    text: String,
    onClick: () -> Unit,
    accent: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) EpgColors.Accent.copy(alpha = 0.25f) else EpgColors.DetailPanelBg,
            focusedContainerColor = EpgColors.Accent.copy(alpha = 0.4f)
        ),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = text,
            color = if (accent) EpgColors.Accent else EpgColors.TextPrimary,
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
                EpgActionButton("ℹ More", detailActionFocused == 2, onClick = onMoreInfo) {
                    onActionFocusChange(2)
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
