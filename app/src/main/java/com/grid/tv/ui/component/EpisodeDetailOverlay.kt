package com.grid.tv.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors

data class EpisodeWatchStatus(
    val positionMs: Long,
    val durationMs: Long,
    val progressFraction: Float?,
    val label: String
)

fun episodeWatchStatus(
    progressMs: Long?,
    durationMs: Long?,
    completionThreshold: Float = 0.95f
): EpisodeWatchStatus {
    val position = progressMs?.coerceAtLeast(0L) ?: 0L
    val duration = durationMs?.coerceAtLeast(0L) ?: 0L
    val fraction = if (duration > 0L && position > 0L) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val label = when {
        fraction != null && fraction >= completionThreshold -> "Completed"
        position > 5_000L && duration > 0L ->
            "Left off at ${formatPlayerTime(position)} / ${formatPlayerTime(duration)}"
        position > 5_000L -> "Left off at ${formatPlayerTime(position)}"
        else -> "Not started"
    }
    return EpisodeWatchStatus(
        positionMs = position,
        durationMs = duration,
        progressFraction = fraction,
        label = label
    )
}

@Composable
fun EpisodeDetailOverlay(
    seasonNumber: Int,
    episodeNumber: Int,
    episode: SeriesEpisode,
    watchStatus: EpisodeWatchStatus,
    onWatchNow: () -> Unit,
    onBack: () -> Unit,
    watchFocusRequester: FocusRequester,
    useDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (useDarkTheme) {
        VodNetflixColors.Background.copy(alpha = 0.94f)
    } else {
        EpgColors.Background.copy(alpha = 0.96f)
    }
    val titleColor = if (useDarkTheme) VodNetflixColors.TextPrimary else EpgColors.TextPrimary
    val secondaryColor = if (useDarkTheme) VodNetflixColors.TextSecondary else EpgColors.TextSecondary
    val accentColor = if (useDarkTheme) VodNetflixColors.Accent else EpgColors.Accent
    val synopsis = episode.plot?.takeIf { it.isNotBlank() } ?: "No description available."
    val episodeLabel =
        "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')} — ${episode.title}"

    BackHandler(onBack = onBack)

    LaunchedEffect(episode.id) {
        watchFocusRequester.requestFocusSafelyAfterLayout()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(30f)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = episodeLabel,
                color = titleColor,
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = watchStatus.label,
                color = accentColor,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            watchStatus.progressFraction?.takeIf { it in 0.01f..0.99f }?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accentColor,
                    trackColor = secondaryColor.copy(alpha = 0.35f)
                )
            }

            val scrollState = rememberScrollState()
            Text(
                text = synopsis,
                color = Color(0xB3FFFFFF),
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .verticalScroll(scrollState)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowFocusButton(
                    onClick = onWatchNow,
                    modifier = Modifier.focusRequester(watchFocusRequester)
                ) {
                    Text("Watch Now", fontFamily = DmSansFamily, fontSize = 15.sp)
                }
                GlowFocusButton(onClick = onBack) {
                    Text("Back", fontFamily = DmSansFamily, fontSize = 15.sp)
                }
            }
        }
    }
}
