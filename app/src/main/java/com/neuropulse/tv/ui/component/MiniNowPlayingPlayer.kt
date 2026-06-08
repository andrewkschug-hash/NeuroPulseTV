package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import coil.compose.AsyncImage
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

object MiniPlayerLayout {
    val Width = 240.dp
    val Height = 135.dp
    val CornerRadius = 10.dp
    val IdleBg = Color(0xFF13131A)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniNowPlayingPlayer(
    channel: Channel?,
    program: Program?,
    player: ExoPlayer?,
    isFocused: Boolean,
    onFocus: () -> Unit,
    sleepCountdown: String? = null,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = player?.isPlaying == true
            }
        }
        player?.addListener(listener)
        isPlaying = player?.isPlaying == true && player.mediaItemCount > 0
        onDispose { player?.removeListener(listener) }
    }

    val showVideo = player != null && isPlaying
    val borderColor = if (isFocused) EpgColors.Accent else Color.White.copy(alpha = 0.15f)
    val borderWidth = if (isFocused) 2.dp else 1.5.dp
    val initials = channel?.name?.take(2)?.uppercase() ?: "TV"

    Box(
        modifier = modifier
            .size(MiniPlayerLayout.Width, MiniPlayerLayout.Height)
            .clip(RoundedCornerShape(MiniPlayerLayout.CornerRadius))
            .border(borderWidth, borderColor, RoundedCornerShape(MiniPlayerLayout.CornerRadius))
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocus() }
    ) {
        if (showVideo) {
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiniPlayerLayout.IdleBg),
                contentAlignment = Alignment.Center
            ) {
                if (channel?.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Text(
                        text = initials,
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (!sleepCountdown.isNullOrBlank()) {
            Text(
                text = "⏱ $sleepCountdown",
                color = Color(0xFFFFB020),
                fontFamily = DmSansFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            ),
                            startY = 60f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = channel?.name ?: "",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = program?.title ?: channel?.currentProgram ?: "Live",
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔊",
                    fontSize = 20.sp
                )
                Text(
                    text = "Press OK to go fullscreen",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp
                )
            }
        }
    }
}
