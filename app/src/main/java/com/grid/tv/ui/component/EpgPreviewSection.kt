package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
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
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.player.LiveFullscreenLogger
import com.grid.tv.player.PlaybackSurfaceInstrument
import com.grid.tv.player.StreamPlaybackStatus
import com.grid.tv.player.isHealthy
import com.grid.tv.player.userLabel
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private val LiveRed = Color(0xFFEF4444)

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
    nextProgram: Program? = null,
    player: ExoPlayer?,
    streamStatus: StreamPlaybackStatus?,
    detailActionFocused: Int,
    isFavorite: Boolean,
    previewFocused: Boolean,
    attachSurface: Boolean = true,
    primaryActionLabel: String = "Watch Live",
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val programSubtitle = when {
        program != null -> {
            val time = "${formatEpgTime(program.startTime)} – ${formatEpgTime(program.endTime)}"
            "${program.title}\n$time"
        }
        channel.currentProgram != null -> channel.currentProgram
        else -> "No EPG data available for this channel"
    }
    val isLiveNow = program?.let { now in it.startTime..it.endTime } ?: true
    val isReplay = program != null && now > program.endTime && primaryActionLabel == "Watch Replay"

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
            isReplay = isReplay,
            primaryActionLabel = primaryActionLabel,
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
            nextProgram = nextProgram,
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
    isReplay: Boolean,
    primaryActionLabel: String,
    detailActionFocused: Int,
    isFavorite: Boolean,
    attachSurface: Boolean,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }

    DisposableEffect(player, streamStatus) {
        if (streamStatus != null) {
            return@DisposableEffect onDispose { }
        }
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
        when (streamStatus) {
            null -> playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
            StreamPlaybackStatus.IDLE,
            StreamPlaybackStatus.ERROR,
            StreamPlaybackStatus.UNAVAILABLE -> false
            else -> true
        }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0xFF0A0A12))
            .border(1.dp, EpgColors.BorderSubtle, shape)
    ) {
        if (player != null && attachSurface) {
            key(attachSurface) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            this.player = player
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }.also { view ->
                            LiveFullscreenLogger.surfaceCreated("epg_preview", player)
                            PlaybackSurfaceInstrument.attach("epg_preview", player, view)
                            LiveFullscreenLogger.attachPlayerToFullscreen(player, "epg_preview")
                        }
                    },
                    update = { view ->
                        if (view.player !== player) {
                            view.player = null
                            view.player = player
                            LiveFullscreenLogger.attachPlayerToFullscreen(player, "epg_preview")
                        }
                        view.isFocusable = false
                        view.isFocusableInTouchMode = false
                    },
                    onRelease = { view ->
                        LiveFullscreenLogger.surfaceDestroyed("epg_preview", player)
                        PlaybackSurfaceInstrument.detach("epg_preview", player, view)
                        LiveFullscreenLogger.detachPlayerFromFullscreen(player, "epg_preview")
                        view.player = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = false }
                )
            }
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
                Text(text = "●", color = LiveRed, fontSize = 10.sp)
                Text(
                    text = "Live",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (isReplay) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "⏪", color = Color(0xFF60A5FA), fontSize = 12.sp)
                Text(
                    text = "Replay",
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val watchIcon = when (primaryActionLabel) {
                        "Watch Replay" -> "⏪"
                        "Reminder" -> "🔔"
                        else -> "▶"
                    }
                    EpgActionButton(
                        label = "$watchIcon $primaryActionLabel",
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
    }
}

@Composable
private fun EpgPreviewInfoSidebar(
    channel: Channel,
    program: Program?,
    nextProgram: Program?,
    streamStatus: StreamPlaybackStatus?,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val shape = RoundedCornerShape(10.dp)
    val scrollState = rememberScrollState()
    val isLiveNow = program?.let { now in it.startTime..it.endTime } ?: false
    val episodeInfo = program?.title?.let(::parseProgramEpisodeInfo)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0xFF12121C))
            .border(1.dp, EpgColors.BorderSubtle, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (program != null) {
            Text(
                text = program.title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(formatEpgTime(program.startTime))
                    append(" – ")
                    append(formatEpgTime(program.endTime))
                    append("  ·  ")
                    append(programDurationMinutes(program))
                    append(" min")
                },
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 1
            )
            if (isLiveNow) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "●", color = LiveRed, fontSize = 9.sp)
                    Text(
                        text = "On now",
                        color = LiveRed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            episodeInfo?.let { episode ->
                EpgInfoField(label = "EPISODE", value = episode)
            }
            val synopsis = program.description.trim()
            if (synopsis.isNotBlank()) {
                EpgInfoField(
                    label = "DESCRIPTION",
                    value = synopsis,
                    maxLines = 5
                )
            }
        } else {
            Text(
                text = channel.name,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel.currentProgram?.trim()?.takeIf { it.isNotBlank() }
                    ?: "No EPG data available for this channel.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EpgColors.BorderSubtle.copy(alpha = 0.6f))
        )

        Text(
            text = "UP NEXT",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        if (nextProgram != null) {
            Text(
                text = nextProgram.title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Starts ${formatEpgTime(nextProgram.startTime)}",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 1
            )
        } else {
            Text(
                text = "No upcoming program listed.",
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 2
            )
        }

        streamStatus?.let { status ->
            if (status.userLabel().isNotBlank()) {
                StreamStatusBadge(status = status, compact = true)
            }
        }
    }
}

@Composable
private fun EpgInfoField(
    label: String,
    value: String,
    valueColor: Color = EpgColors.TextPrimary,
    maxLines: Int = 2
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
            fontSize = if (label == "DESCRIPTION") 12.sp else 14.sp,
            fontWeight = if (label == "DESCRIPTION") FontWeight.Normal else FontWeight.SemiBold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            lineHeight = if (label == "DESCRIPTION") 16.sp else 18.sp
        )
    }
}
