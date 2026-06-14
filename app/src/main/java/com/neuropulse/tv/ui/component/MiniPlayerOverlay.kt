package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.util.MediaAttribution

private val OverlayWidth = EpgLayout.MiniPlayerWidth
private val OverlayHeight = EpgLayout.MiniPlayerHeight
private val OverlayCorner = 8.dp
private val LiveGreen = Color(0xFF4ADE80)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniPlayerOverlay(
    channel: Channel?,
    streamUrl: String,
    isFocused: Boolean,
    isIdleShrunk: Boolean,
    miniAudioEnabled: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (channel == null || streamUrl.isBlank()) return

    val context = LocalContext.current
    val playbackContext = remember(context) {
        MediaAttribution.appContext(context, MediaAttribution.MEDIA_PLAYBACK)
    }

    val player = remember(playbackContext) {
        val renderersFactory = DefaultRenderersFactory(playbackContext)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(playbackContext, renderersFactory).build()
    }

    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                playbackState = player.playbackState
            }
        }
        player.addListener(listener)
        playbackState = player.playbackState
        isPlaying = player.isPlaying
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(streamUrl, miniAudioEnabled) {
        player.volume = if (miniAudioEnabled) 1f else 0f
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.playWhenReady = true
        onDispose { }
    }

    val showPlaceholder = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED
    val isLive = isPlaying ||
        playbackState == Player.STATE_BUFFERING ||
        playbackState == Player.STATE_READY

    val scale = if (isIdleShrunk && !isFocused) 0.95f else 1f

    val shape = RoundedCornerShape(OverlayCorner)
    val borderColor = if (isFocused) EpgColors.Accent else Color.White.copy(alpha = 0.3f)
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val glowColor = EpgColors.Accent.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .size(OverlayWidth, OverlayHeight)
            .clip(shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
            .then(
                if (isFocused) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = glowColor,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(OverlayCorner.toPx()),
                            style = Stroke(width = 8.dp.toPx())
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
        ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view ->
                if (view.player != player) view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showPlaceholder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiniPlayerLayout.IdleBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(2).uppercase(),
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isLive) "● LIVE" else channel.number.toString(),
                color = if (isLive) LiveGreen else Color.White.copy(alpha = 0.9f),
                fontFamily = DmSansFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        ),
                        startY = 100f
                    )
                )
        )

        Text(
            text = channel.name,
            color = Color.White,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )

        if (isFocused) {
            Text(
                text = "▶ Resume",
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
    }
}
