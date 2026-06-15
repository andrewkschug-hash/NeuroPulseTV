package com.neuropulse.tv.ui.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.player.StreamPlaybackStatus
import com.neuropulse.tv.player.isHealthy
import com.neuropulse.tv.player.userLabel
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

private val LiveRed = Color(0xFFEF4444)
private val LiveGreen = Color(0xFF4ADE80)

fun channelQualityLabel(channel: Channel): String {
    Regex("""\((\d+p)\)""", RegexOption.IGNORE_CASE).find(channel.name)?.groupValues?.get(1)?.let {
        return it.uppercase()
    }
    return when {
        channel.name.contains("4k", ignoreCase = true) -> "4K"
        channel.name.contains("1080", ignoreCase = true) -> "1080p"
        channel.name.contains("720", ignoreCase = true) -> "720p"
        else -> "HD"
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EpgPreviewSection(
    channel: Channel,
    program: Program?,
    upcomingPrograms: List<Program>,
    now: Long,
    player: ExoPlayer?,
    streamStatus: StreamPlaybackStatus?,
    detailActionFocused: Int,
    isFavorite: Boolean,
    previewFocused: Boolean,
    attachSurface: Boolean = true,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val programSubtitle = when {
        program != null -> program.title
        channel.currentProgram != null -> channel.currentProgram
        else -> "No EPG data available for this channel"
    }
    val isLiveNow = program?.let { now in it.startTime..it.endTime } ?: true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EpgLayout.PreviewSectionHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EpgPreviewPlayerPane(
            channel = channel,
            programSubtitle = programSubtitle,
            player = player,
            streamStatus = streamStatus,
            isLiveNow = isLiveNow,
            detailActionFocused = if (previewFocused) detailActionFocused else -1,
            isFavorite = isFavorite,
            attachSurface = attachSurface,
            onWatch = onWatch,
            onFavorite = onFavorite,
            onRecord = onRecord,
            modifier = Modifier.weight(1f)
        )
        EpgPreviewInfoSidebar(
            channel = channel,
            program = program,
            upcomingPrograms = upcomingPrograms,
            now = now,
            streamStatus = streamStatus,
            modifier = Modifier.width(EpgLayout.PreviewInfoWidth)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EpgPreviewPlayerPane(
    channel: Channel,
    programSubtitle: String,
    player: ExoPlayer?,
    streamStatus: StreamPlaybackStatus?,
    isLiveNow: Boolean,
    detailActionFocused: Int,
    isFavorite: Boolean,
    attachSurface: Boolean,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
        }
        player?.addListener(listener)
        playbackState = player?.playbackState ?: Player.STATE_IDLE
        onDispose { player?.removeListener(listener) }
    }

    val showVideo = attachSurface &&
        player != null &&
        player.mediaItemCount > 0 &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0xFF0A0A12))
            .border(1.dp, EpgColors.BorderSubtle, shape)
    ) {
        if (player != null && attachSurface) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        this.player = player
                    }
                },
                update = { view ->
                    view.player = if (attachSurface) player else null
                },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!showVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF13131A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(2).uppercase(),
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showVideo && streamStatus != null && !streamStatus.isHealthy()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                StreamStatusBadge(status = streamStatus)
            }
        }

        if (isLiveNow) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "●",
                    color = LiveRed,
                    fontSize = 10.sp
                )
                Text(
                    text = "Live",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                        startY = 0f
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = programSubtitle,
                    color = Color(0xFF9CA3AF),
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
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
    }
}

@Composable
private fun EpgPreviewInfoSidebar(
    channel: Channel,
    program: Program?,
    upcomingPrograms: List<Program>,
    now: Long,
    streamStatus: StreamPlaybackStatus?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val quality = channelQualityLabel(channel)
    val isLiveNow = program?.let { now in it.startTime..it.endTime } ?: true

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0xFF12121C))
            .border(1.dp, EpgColors.BorderSubtle, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        EpgInfoField(label = "CHANNEL", value = channel.name)
        EpgInfoField(label = "QUALITY", value = "$quality HD")
        EpgInfoField(
            label = "STATUS",
            value = if (isLiveNow) "● Live now" else "Scheduled",
            valueColor = if (isLiveNow) LiveGreen else EpgColors.TextSecondary
        )
        streamStatus?.let { status ->
            if (status.userLabel().isNotBlank()) {
                StreamStatusBadge(status = status, compact = true)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Up Next",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        if (upcomingPrograms.isEmpty()) {
            repeat(3) { index ->
                EpgUpNextRow(
                    time = formatEpgTime(now + (index + 1) * 30 * 60 * 1000L),
                    title = "No data"
                )
            }
        } else {
            upcomingPrograms.take(4).forEach { upcoming ->
                EpgUpNextRow(
                    time = formatEpgTime(upcoming.startTime),
                    title = upcoming.title
                )
            }
        }
    }
}

@Composable
private fun EpgInfoField(
    label: String,
    value: String,
    valueColor: Color = EpgColors.TextPrimary
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpgUpNextRow(time: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = title,
            color = if (title == "No data") EpgColors.TextDimmed else EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
