package com.grid.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.domain.model.VodProgressPolicy
import com.grid.tv.feature.vod.VodNextUpItem
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import kotlinx.coroutines.delay

@Composable
fun VodNextUpOverlay(
    visible: Boolean,
    nextItem: VodNextUpItem?,
    autoPlay: Boolean,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var countdown by remember(nextItem) { mutableIntStateOf(VodProgressPolicy.NEXT_UP_COUNTDOWN_SEC) }

    LaunchedEffect(visible, nextItem, autoPlay) {
        if (!visible || nextItem == null) return@LaunchedEffect
        countdown = VodProgressPolicy.NEXT_UP_COUNTDOWN_SEC
        while (countdown > 0 && visible) {
            delay(1_000)
            countdown--
        }
        if (visible && autoPlay && countdown <= 0) onPlayNext()
    }

    AnimatedVisibility(
        visible = visible && nextItem != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val item = nextItem ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 32.dp)
                .background(
                    EpgColors.DetailPanelBg.copy(alpha = 0.92f),
                    RoundedCornerShape(12.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Next Up",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
            Text(
                text = item.title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "S${item.seasonNumber} · E${item.episodeNumber}",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlowFocusButton(onClick = onPlayNext) {
                    Text(
                        text = if (autoPlay) "Play (${countdown}s)" else "Play Next",
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                GlowFocusButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
