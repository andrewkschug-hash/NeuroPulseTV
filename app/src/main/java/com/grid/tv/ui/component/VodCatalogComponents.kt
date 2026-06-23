package com.grid.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import com.grid.tv.util.TvImageSizing
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodCatalogProgress
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors
import java.text.NumberFormat

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
    modifier: Modifier = Modifier,
    ratingBadge: String? = null,
    posterHeight: androidx.compose.ui.unit.Dp = 168.dp,
    externallyFocused: Boolean = false
) {
    val displayTitle = remember(title) { cleanVodDisplayTitle(title) }
    val languageBadge = remember(title) { parseVodLanguageBadge(title) }
    val resolutionBadge = remember(title) {
        parseVodResolutionBadge(title) ?: if (showHdBadge) "HD" else null
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .width(112.dp)
            .then(
                if (externallyFocused) {
                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF13131A),
            focusedContainerColor = Color(0xFF13131A)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(posterHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Color(0xFF1A1A22))
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    TvPosterImage(
                        url = posterUrl,
                        contentDescription = displayTitle,
                        kind = PosterImageKind.VodGrid,
                        placeholderLetter = displayTitle,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = displayTitle.take(2).uppercase(),
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 18.sp
                        )
                    }
                }
                languageBadge?.let { badge ->
                    Text(
                        text = badge,
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                resolutionBadge?.let { badge ->
                    Text(
                        text = badge,
                        color = Color(0xFFFFD54F),
                        fontFamily = DmSansFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color(0xCC78350F), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (resolutionBadge == null && !ratingBadge.isNullOrBlank()) {
                    Text(
                        text = ratingBadge,
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
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
                text = displayTitle,
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
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VodPosterCard(
        title = title,
        posterUrl = posterUrl,
        progressFraction = null,
        showHdBadge = false,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun SeriesPosterCard(
    show: SeriesShow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SeriesPosterCard(
        title = show.name,
        posterUrl = show.coverUrl,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun SeeAllVodCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .width(112.dp)
            .height(168.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1C3A6B),
            focusedContainerColor = Color(0xFF1C3A6B)
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
    PersonalizedVodRow(
        title = "Movies",
        movies = movies,
        progressByStreamId = progressByStreamId,
        onPlayMovie = onPlayMovie,
        onSeeAll = onSeeAll
    )
}

@Composable
fun PersonalizedVodRow(
    title: String,
    movies: List<VodItem>,
    progressByStreamId: Map<Long, Long>,
    onPlayMovie: (VodItem) -> Unit,
    onSeeAll: () -> Unit,
    ratingForMovie: (VodItem) -> String? = { null },
    posterUrlForMovie: (VodItem) -> String? = { it.posterUrl },
    titleColor: Color = EpgColors.TextPrimary
) {
    if (movies.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = titleColor,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "View All",
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
        }
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
                    posterUrl = posterUrlForMovie(movie),
                    progressFraction = fraction,
                    showHdBadge = movie.showsHdBadge(),
                    ratingBadge = ratingForMovie(movie),
                    onClick = { onPlayMovie(movie) }
                )
            }
            item(key = "see_all_$title") {
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
    GridFocusSurface(
        onClick = { },
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF13131A),
            focusedContainerColor = Color(0xFF13131A)
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
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    externallyFocused: Boolean = false
) {
    val shape = RoundedCornerShape(8.dp)
    val displayTitle = remember(title) { cleanVodDisplayTitle(title) }
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused || externallyFocused
    val scale by animateFloatAsState(
        targetValue = if (showFocused) 1.03f else 1f,
        animationSpec = tween(150),
        label = "episodeCardScale"
    )
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(
                focused = showFocused,
                shape = shape,
                width = 3.dp,
                unfocusedWidth = 1.dp,
                unfocusedColor = EpgColors.BorderSubtle.copy(alpha = 0.45f),
                focusedColor = EpgColors.FocusBorder
            ),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF13131A),
            focusedContainerColor = Color(0xFF1E1E2A)
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
                text = displayTitle,
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
fun VodHeroSection(
    movie: VodItem,
    enrichment: TitleEnrichmentEntity?,
    carouselSize: Int,
    carouselIndex: Int,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
    moreInfoFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val backdropUrl = enrichment?.backdropUrl ?: enrichment?.posterUrl ?: movie.posterUrl
    val displayTitle = remember(movie.title) { cleanVodDisplayTitle(movie.title) }
    val rating = enrichment?.rating?.takeIf { it > 0.0 }?.let { String.format("%.1f", it) }
        ?: movie.rating?.trim()?.takeIf { it.isNotBlank() }
    val year = enrichment?.releaseDate?.take(4)
        ?: parseVodReleaseYear(movie.title)
    val ageCert = enrichment?.ageCertification?.takeIf { it.isNotBlank() }
    val genres = (enrichment?.genres ?: movie.genre)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.take(3)
        .orEmpty()
    val overview = resolveMovieOverview(movie, enrichment).orEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            TvPosterImage(
                url = backdropUrl,
                contentDescription = displayTitle,
                kind = PosterImageKind.Backdrop,
                placeholderLetter = displayTitle,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VodNetflixColors.CardPlaceholder)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.1f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f),
                            VodNetflixColors.Background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "FEATURED",
                color = VodNetflixColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = displayTitle,
                color = VodNetflixColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!year.isNullOrBlank()) {
                    Text(
                        text = year,
                        color = VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!rating.isNullOrBlank()) {
                    Text(
                        text = "★ $rating",
                        color = Color(0xFFFFD54F),
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!ageCert.isNullOrBlank()) {
                    Text(
                        text = ageCert,
                        color = VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (overview.isNotBlank()) {
                Text(
                    text = overview,
                    color = VodNetflixColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val playModifier = if (playFocusRequester != null) {
                    Modifier.focusRequester(playFocusRequester)
                } else {
                    Modifier
                }
                GridFocusSurface(
                    onClick = onPlay,
                    modifier = playModifier,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.92f)
                    )
                ) {
                    Text(
                        text = "▶  Play",
                        color = Color.Black,
                        fontFamily = DmSansFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                    )
                }
                val moreModifier = if (moreInfoFocusRequester != null) {
                    Modifier.focusRequester(moreInfoFocusRequester)
                } else {
                    Modifier
                }
                GridFocusSurface(
                    onClick = onMoreInfo,
                    modifier = moreModifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = "More info",
                        color = VodNetflixColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                    )
                }
            }

            if (carouselSize > 1) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(carouselSize) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == carouselIndex) 6.dp else 4.dp)
                                .background(
                                    if (index == carouselIndex) Color.White else Color.White.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        )
                    }
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
fun VodPosterSkeleton(
    modifier: Modifier = Modifier,
    posterHeight: androidx.compose.ui.unit.Dp = 168.dp
) {
    Column(modifier = modifier.width(112.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Color(0xFF1E1E28))
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .fillMaxWidth(0.72f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF252532))
        )
    }
}

@Composable
fun VodCatalogProgressBar(
    progress: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(400)) + shrinkVertically(animationSpec = tween(400))
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0xFF14141C))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(EpgColors.Accent)
            )
        }
    }
}

fun formatVodCatalogCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

fun vodCatalogStatusMessage(
    baseMessage: String,
    loaded: Int,
    total: Int
): String {
    val countLine = when {
        total > 0 -> "Loaded ${formatVodCatalogCount(loaded)} / ${formatVodCatalogCount(total)} titles"
        loaded > 0 -> "Loaded ${formatVodCatalogCount(loaded)} titles"
        else -> null
    }
    return if (countLine != null) "$baseMessage\n$countLine" else baseMessage
}

@Composable
fun VodCatalogLoadingBanner(
    baseMessage: String,
    progress: VodCatalogProgress,
    isMovies: Boolean,
    modifier: Modifier = Modifier
) {
    val loaded = if (isMovies) progress.moviesLoaded else progress.seriesLoaded
    val total = if (isMovies) progress.moviesTotal else progress.seriesTotal
    val phaseComplete = if (isMovies) progress.isMoviesPhaseComplete else progress.isSeriesPhaseComplete
    val showBanner = if (isMovies) {
        progress.isLoading && !phaseComplete
    } else {
        progress.isLoading && progress.isMoviesPhaseComplete && !phaseComplete
    }
    if (!showBanner) return

    Text(
        text = vodCatalogStatusMessage(baseMessage, loaded, total),
        color = EpgColors.TextSecondary,
        fontFamily = DmSansFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
fun VodCatalogRefreshWarningBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        color = VodNetflixColors.TextSecondary,
        fontFamily = DmSansFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
fun VodEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
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
        if (onRetry != null) {
            GlowFocusButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("Retry", fontFamily = DmSansFamily)
            }
        }
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
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(20.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
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
