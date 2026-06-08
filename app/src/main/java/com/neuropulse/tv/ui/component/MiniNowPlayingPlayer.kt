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
    val Margin = 16.dp
    val CornerRadius = 10.dp
    val ReservedWidth = Width + Margin
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniNowPlayingPlayer(
    channel: Channel?,
    program: Program?,
    player: ExoPlayer?,
    isFocused: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isFocused) EpgColors.Accent else Color.White.copy(alpha = 0.12f)
    val borderWidth = if (isFocused) 2.dp else 1.5.dp

    Box(
        modifier = modifier
            .size(MiniPlayerLayout.Width, MiniPlayerLayout.Height)
            .clip(RoundedCornerShape(MiniPlayerLayout.CornerRadius))
            .border(borderWidth, borderColor, RoundedCornerShape(MiniPlayerLayout.CornerRadius))
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocus() }
    ) {
        if (player != null) {
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
                    .background(EpgColors.GridBg)
            )
        }

        channel?.let { ch ->
            AsyncImage(
                model = ch.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
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
