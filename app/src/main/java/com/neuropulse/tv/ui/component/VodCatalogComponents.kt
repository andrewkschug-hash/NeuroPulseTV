package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.domain.model.SeriesShow
import com.neuropulse.tv.domain.model.VodItem
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

fun parseVodDurationMs(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    raw.trim().toLongOrNull()?.let { return it * 1000L }
    val parts = raw.split(":").mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
        2 -> (parts[0] * 60 + parts[1]) * 1000L
        else -> null
    }
}

fun VodItem.showsHdBadge(): Boolean {
    val upper = title.uppercase()
    return upper.contains(" HD") || upper.contains(" FHD") || upper.contains("4K") || upper.contains("UHD")
}

@Composable
fun VodPosterCard(
    title: String,
    posterUrl: String?,
    progressFraction: Float?,
    showHdBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(112.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF13131A),
            focusedContainerColor = EpgColors.ChannelRowFocusBg
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Color(0xFF1A1A22))
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = title.take(2).uppercase(),
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 18.sp
                        )
                    }
                }
                if (showHdBadge) {
                    Text(
                        text = "HD",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(EpgColors.Accent.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                progressFraction?.takeIf { it in 0.01f..0.98f }?.let { fraction ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .background(EpgColors.Accent)
                        )
                    }
                }
            }
            Text(
                text = title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun SeriesPosterCard(
    show: SeriesShow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VodPosterCard(
        title = show.name,
        posterUrl = show.coverUrl,
        progressFraction = null,
        showHdBadge = false,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun SeeAllVodCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(112.dp)
            .height(168.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1C3A6B),
            focusedContainerColor = EpgColors.ChannelRowFocusBg
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "See All →",
                color = EpgColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun VodCatalogRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun MoviesHomeRow(
    movies: List<VodItem>,
    progressByStreamId: Map<Long, Long>,
    onPlayMovie: (VodItem) -> Unit,
    onSeeAll: () -> Unit
) {
    if (movies.isEmpty()) return
    VodCatalogRow(title = "Movies") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(movies, key = { "${it.playlistId}_${it.streamId}" }) { movie ->
                val durationMs = parseVodDurationMs(movie.duration)
                val progressMs = progressByStreamId[movie.streamId]
                val fraction = if (durationMs != null && progressMs != null && durationMs > 0L) {
                    (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
                VodPosterCard(
                    title = movie.title,
                    posterUrl = movie.posterUrl,
                    progressFraction = fraction,
                    showHdBadge = movie.showsHdBadge(),
                    onClick = { onPlayMovie(movie) }
                )
            }
            item(key = "see_all_movies") {
                SeeAllVodCard(onClick = onSeeAll)
            }
        }
    }
}

@Composable
fun SeriesHomeRow(
    shows: List<SeriesShow>,
    onOpenSeries: (SeriesShow) -> Unit,
    onSeeAll: () -> Unit
) {
    if (shows.isEmpty()) return
    VodCatalogRow(title = "Series") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(shows, key = { "${it.playlistId}_${it.id}" }) { show ->
                SeriesPosterCard(show = show, onClick = { onOpenSeries(show) })
            }
            item(key = "see_all_series") {
                SeeAllVodCard(onClick = onSeeAll)
            }
        }
    }
}

@Composable
fun VodSearchField(
    query: String,
    placeholder: String,
    focused: Boolean,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (focused) EpgColors.Accent else EpgColors.BorderSubtle
    Surface(
        onClick = { },
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF13131A),
            focusedContainerColor = EpgColors.ChannelRowFocusBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⌕",
                color = if (focused) EpgColors.Accent else EpgColors.TextDimmed,
                fontSize = 16.sp
            )
            Text(
                text = when {
                    query.isNotBlank() -> query
                    focused -> "Type to search…"
                    else -> placeholder
                },
                color = if (query.isNotBlank()) EpgColors.TextPrimary else EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (query.isNotBlank()) {
                Text(
                    text = "Clear",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun VodEpisodeCard(
    episodeNumber: Int,
    title: String,
    duration: String?,
    progressFraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF13131A),
            focusedContainerColor = EpgColors.ChannelRowFocusBg
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "E${episodeNumber.toString().padStart(2, '0')}",
                    color = EpgColors.Accent,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!duration.isNullOrBlank()) {
                    Text(
                        text = duration,
                        color = EpgColors.TextDimmed,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            progressFraction?.takeIf { it in 0.01f..0.98f }?.let { fraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(EpgColors.Accent, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun VodHubHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp
        )
    }
}

@Composable
fun VodEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "▦",
            color = EpgColors.TextDimmed.copy(alpha = 0.45f),
            fontSize = 36.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = title,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = message,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun VodCategoryChip(
    label: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        focused -> EpgColors.Accent.copy(alpha = 0.22f)
        selected -> EpgColors.ChannelRowFocusBg
        else -> Color.Transparent
    }
    val borderColor = when {
        focused -> EpgColors.Accent
        selected -> EpgColors.Accent.copy(alpha = 0.45f)
        else -> EpgColors.BorderSubtle.copy(alpha = 0.65f)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(20.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = EpgColors.Accent.copy(alpha = 0.22f)
        )
    ) {
        Text(
            text = label,
            color = when {
                focused || selected -> EpgColors.TextPrimary
                else -> EpgColors.TextSecondary
            },
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
