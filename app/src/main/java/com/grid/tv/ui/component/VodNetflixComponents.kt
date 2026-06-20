package com.grid.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.VodNetflixColors

private val PosterWidth = 120.dp
private val PosterHeight = 180.dp
private val PosterShape = RoundedCornerShape(4.dp)

@Composable
fun NetflixPosterCard(
    title: String,
    posterUrl: String?,
    progressFraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = tween(150),
        label = "posterScale"
    )
    val border = if (focused) {
        ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, VodNetflixColors.FocusBorder)
            )
        )
    } else {
        TvFocusDefaults.noBorder()
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(PosterWidth)
            .height(PosterHeight)
            .scale(scale)
            .zIndex(if (focused) 1f else 0f),
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(PosterShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = VodNetflixColors.CardPlaceholder,
            focusedContainerColor = VodNetflixColors.CardPlaceholder
        ),
        scale = TvFocusDefaults.NoScale,
        border = border,
        glow = TvFocusDefaults.noGlow()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!posterUrl.isNullOrBlank()) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(posterUrl)
                        .size(Size(240, 360))
                        .crossfade(200)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VodNetflixColors.CardPlaceholder),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(2).uppercase(),
                        color = VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 18.sp
                    )
                }
            }

            progressFraction?.takeIf { it in 0.01f..0.98f }?.let { fraction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(VodNetflixColors.Accent)
                    )
                }
            }

            AnimatedVisibility(
                visible = focused,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(80)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = title,
                        color = VodNetflixColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixCategoryRow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = VodNetflixColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun NetflixMovieRow(
    movies: List<VodItem>,
    progressByStreamId: Map<Long, Long>,
    posterUrlFor: (VodItem) -> String?,
    onPlayMovie: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null
) {
    if (movies.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(movies, key = { "${it.playlistId}_${it.streamId}" }) { movie ->
            val durationMs = parseVodDurationMs(movie.duration)
            val progressMs = progressByStreamId[movie.streamId]
            val fraction = if (durationMs != null && progressMs != null && durationMs > 0L) {
                (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
            val itemModifier = if (movie == movies.first() && firstItemFocusRequester != null) {
                Modifier.focusRequester(firstItemFocusRequester)
            } else {
                Modifier
            }
            NetflixPosterCard(
                title = movie.title,
                posterUrl = posterUrlFor(movie),
                progressFraction = fraction,
                onClick = { onPlayMovie(movie) },
                modifier = itemModifier
            )
        }
    }
}

@Composable
fun NetflixSeriesRow(
    shows: List<SeriesShow>,
    onOpenSeries: (SeriesShow) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null
) {
    if (shows.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shows, key = { "${it.playlistId}_${it.id}" }) { show ->
            val itemModifier = if (show == shows.first() && firstItemFocusRequester != null) {
                Modifier.focusRequester(firstItemFocusRequester)
            } else {
                Modifier
            }
            NetflixPosterCard(
                title = show.name,
                posterUrl = show.coverUrl,
                progressFraction = null,
                onClick = { onOpenSeries(show) },
                modifier = itemModifier
            )
        }
    }
}

fun LazyListScope.netflixMovieBrowseRows(
    rows: List<VodBrowseRow>,
    progressByStreamId: Map<Long, Long>,
    posterUrlFor: (VodItem) -> String?,
    onPlayMovie: (VodItem) -> Unit,
    firstRowFocusRequester: FocusRequester? = null
) {
    rows.forEachIndexed { index, row ->
        if (row.movies.isEmpty()) return@forEachIndexed
        item(key = "movie_row_${row.id}") {
            NetflixCategoryRow(title = row.title) {
                NetflixMovieRow(
                    movies = row.movies,
                    progressByStreamId = progressByStreamId,
                    posterUrlFor = posterUrlFor,
                    onPlayMovie = onPlayMovie,
                    firstItemFocusRequester = if (index == 0) firstRowFocusRequester else null
                )
            }
        }
    }
}

fun LazyListScope.netflixSeriesBrowseRows(
    rows: List<VodBrowseRow>,
    onOpenSeries: (SeriesShow) -> Unit,
    firstRowFocusRequester: FocusRequester? = null
) {
    rows.forEachIndexed { index, row ->
        if (row.series.isEmpty()) return@forEachIndexed
        item(key = "series_row_${row.id}") {
            NetflixCategoryRow(title = row.title) {
                NetflixSeriesRow(
                    shows = row.series,
                    onOpenSeries = onOpenSeries,
                    firstItemFocusRequester = if (index == 0) firstRowFocusRequester else null
                )
            }
        }
    }
}

@Composable
fun NetflixUnderlineTabs(
    labels: List<String>,
    activeIndex: Int,
    focusedIndex: Int,
    barFocused: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VodNetflixColors.Background)
            .padding(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == activeIndex
            val focused = barFocused && index == focusedIndex
            val textColor = when {
                focused -> VodNetflixColors.Accent
                selected -> VodNetflixColors.TextPrimary
                else -> VodNetflixColors.TextSecondary
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Transparent)
            ) {
                GridFocusSurface(
                    onClick = { onTabSelected(index) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            color = textColor,
                            fontFamily = DmSansFamily,
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(if (selected || focused) 28.dp else 0.dp)
                                .height(3.dp)
                                .background(
                                    if (focused) VodNetflixColors.Accent else VodNetflixColors.TextPrimary
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VodTopBarSearchIcon(
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = if (focused) 0.12f else 0.06f))
            .padding(0.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Text(
            text = "⌕",
            color = if (focused) VodNetflixColors.Accent else VodNetflixColors.TextPrimary,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun VodSearchOverlay(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Search",
                color = VodNetflixColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            TvDialogSearchBar(
                value = query,
                onValueChange = onQueryChange,
                placeholder = placeholder,
                focusRequester = focusRequester,
                label = "Search",
                confirmLabel = "Done",
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusable()
            )
            Text(
                text = "Press Back to close",
                color = VodNetflixColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
        }
    }
}
