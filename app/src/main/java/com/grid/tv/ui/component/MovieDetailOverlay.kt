package com.grid.tv.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.VodNetflixColors

fun resolveMovieOverview(movie: VodItem, enrichment: TitleEnrichmentEntity?): String? =
    enrichment?.overview?.takeIf { it.isNotBlank() }
        ?: movie.plot?.takeIf { it.isNotBlank() }
        ?: movie.genre?.takeIf { it.isNotBlank() }

@Composable
fun MovieDetailOverlay(
    movie: VodItem,
    enrichment: TitleEnrichmentEntity?,
    overview: String?,
    runtimeLabel: String?,
    onWatchNow: () -> Unit,
    onBack: () -> Unit,
    watchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backdropUrl = enrichment?.backdropUrl ?: enrichment?.posterUrl ?: movie.posterUrl
    val posterUrl = enrichment?.posterUrl ?: movie.posterUrl
    val displayTitle = movie.title.replace(Regex("\\s*\\(\\d{4}\\)\\s*"), "").trim()
    val synopsis = resolveMovieOverview(movie, enrichment)
        ?: overview?.takeIf { it.isNotBlank() }
        ?: "No description available."
    val modalTrapFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(movie.streamId, enrichment?.providerKey) {
        watchFocusRequester.requestFocusSafelyAfterLayout()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(25f)
            .background(VodNetflixColors.Background)
            .focusRequester(modalTrapFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape)
                ) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .size(Size(1920, 800))
                    .crossfade(300)
                    .build(),
                contentDescription = displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.92f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.16f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF13131A))
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = displayTitle,
                    color = VodNetflixColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!runtimeLabel.isNullOrBlank()) {
                    Text(
                        text = runtimeLabel,
                        color = VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
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
                        .fillMaxWidth(0.85f)
                        .heightIn(max = 140.dp)
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
}

fun formatVodRuntimeLabel(raw: String?, runtimeMinutes: Int? = null): String? {
    runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
        val hours = mins / 60
        val minutes = mins % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
    parseVodDurationMs(raw)?.let { ms ->
        val mins = (ms / 60_000L).toInt().coerceAtLeast(1)
        val hours = mins / 60
        val minutes = mins % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
    return raw?.trim()?.takeIf { it.isNotBlank() }
}

fun runtimeLabelForMovie(movie: VodItem, enrichment: TitleEnrichmentEntity?): String? =
    formatVodRuntimeLabel(movie.duration, enrichment?.runtimeMinutes)
