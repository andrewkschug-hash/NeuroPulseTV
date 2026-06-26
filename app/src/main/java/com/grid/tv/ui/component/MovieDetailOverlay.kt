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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.domain.model.VodItem
import com.grid.tv.feature.vod.personalization.RecommendationVote
import com.grid.tv.ui.component.GlassFocusButton
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.util.TvImageSizing

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
    modifier: Modifier = Modifier,
    recommendationVote: RecommendationVote? = null,
    onRecommendationVote: ((RecommendationVote) -> Unit)? = null
) {
    val context = LocalContext.current
    val backdropUrl = enrichment?.backdropUrl ?: enrichment?.posterUrl ?: movie.posterUrl
    val posterUrl = enrichment?.posterUrl ?: movie.posterUrl
    val displayTitle = remember(movie.title) { cleanVodDisplayTitle(movie.title) }
    val metadataItems = buildMovieDetailMetadata(movie, enrichment, runtimeLabel)
    val synopsis = resolveMovieOverview(movie, enrichment)
        ?: overview?.takeIf { it.isNotBlank() }
        ?: "No description available."
    val modalTrapFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(movie.streamId, enrichment?.providerKey) {
        modalTrapFocusRequester.requestFocusSafelyAfterLayout()
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
            val (backdropW, backdropH) = TvImageSizing.vodBackdropSize(context)
            AsyncImage(
                model = TvImageSizing.sizedRequest(
                    context = context,
                    data = backdropUrl,
                    widthPx = backdropW,
                    heightPx = backdropH,
                    crossfadeMs = if (TvImageSizing.crossfadeMs(context) == 0) 0 else 300
                ),
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
                    val (posterW, posterH) = TvImageSizing.vodPosterSize(context)
                    AsyncImage(
                        model = TvImageSizing.sizedRequest(
                            context = context,
                            data = posterUrl,
                            widthPx = posterW,
                            heightPx = posterH
                        ),
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
                RatingChipsRow(
                    chips = buildRatingChips(
                        providerRating = movie.rating,
                        tmdbRating = enrichment?.rating
                    )
                )

                if (metadataItems.isNotEmpty()) {
                    MovieDetailMetadataRow(items = metadataItems)
                }

                Spacer(modifier = Modifier.height(4.dp))

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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassFocusButton(
                        onClick = onWatchNow,
                        primary = true,
                        modifier = Modifier.focusRequester(watchFocusRequester),
                        contentDescription = "Play"
                    ) {
                        Text("Play", fontFamily = DmSansFamily, fontSize = 15.sp)
                    }
                    GlassFocusButton(
                        onClick = onBack,
                        contentDescription = "Back"
                    ) {
                        Text("Back", fontFamily = DmSansFamily, fontSize = 15.sp)
                    }
                    if (onRecommendationVote != null) {
                        RecommendationFeedbackButtons(
                            contentKey = movie.streamId.toString(),
                            currentVote = recommendationVote,
                            onVote = onRecommendationVote
                        )
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

fun buildMovieDetailMetadata(
    movie: VodItem,
    enrichment: TitleEnrichmentEntity?,
    runtimeLabel: String?
): List<String> {
    val parts = mutableListOf<String>()
    runtimeLabel?.trim()?.takeIf { it.isNotBlank() }?.let { parts += it }

    val rating = enrichment?.rating?.takeIf { it > 0.0 }?.let { String.format("%.1f", it) }
        ?: movie.rating?.trim()?.takeIf { it.isNotBlank() }
    rating?.let { parts += "★ $it" }

    val year = enrichment?.releaseYear?.takeIf { it in 1900..2100 }
        ?: enrichment?.releaseDate?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
        ?: Regex("\\b(19\\d{2}|20\\d{2})\\b").find(movie.title)?.value?.toIntOrNull()
    year?.let { parts += it.toString() }

    return parts
}

@Composable
private fun MovieDetailMetadataRow(items: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEachIndexed { index, label ->
            if (index > 0) {
                Text(
                    text = "·",
                    color = Color.White.copy(alpha = 0.35f),
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(8.dp)
                )
            }
            Text(
                text = label,
                color = EpgColors.TextSecondary.copy(alpha = 0.95f),
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
            )
        }
    }
}
