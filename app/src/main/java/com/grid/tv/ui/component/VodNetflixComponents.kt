package com.grid.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.util.TvTextInputSession

private val PosterWidth = 130.dp
private val PosterHeight = 195.dp
private val PosterShape = RoundedCornerShape(6.dp)
private val LandscapeWidth = 300.dp
private val LandscapeHeight = 168.dp

fun vodBrowseRowDisplayTitle(rowId: String, title: String): String = when (rowId) {
    "top_imdb" -> "Top IMDB picks"
    "4k" -> "4K Ultra HD"
    "recent" -> "Recently Added"
    else -> title
}

fun formatContinueWatchingSubtitle(item: ContinueWatchingItem): String {
    val remaining = formatCwRemaining(item.remainingMs)
    return when {
        item.subtitle.isNotBlank() -> "$remaining · ${item.subtitle}"
        else -> remaining
    }
}

private fun formatCwRemaining(remainingMs: Long): String {
    if (remainingMs <= 0L) return "Resume"
    val totalMin = (remainingMs + 30_000L) / 60_000L
    val hours = totalMin / 60
    val minutes = totalMin % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m remaining"
        else -> "${minutes}m remaining"
    }
}

fun movieMetaSubtitle(movie: VodItem, rating: String?): String {
    val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(movie.title)?.value
    return listOfNotNull(
        rating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
        year
    ).joinToString(" · ")
}

@Composable
fun VodFocusInfoPopup(
    title: String,
    rating: String?,
    meta: String?,
    overview: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(PosterWidth + 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A).copy(alpha = 0.96f))
            .border(1.dp, VodNetflixColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = VodNetflixColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val metaLine = listOfNotNull(
            rating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
            meta?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                color = Color(0xFFFFD54F),
                fontFamily = DmSansFamily,
                fontSize = 11.sp
            )
        }
        if (!overview.isNullOrBlank()) {
            Text(
                text = overview,
                color = VodNetflixColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun NetflixPosterCard(
    title: String,
    posterUrl: String?,
    progressFraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRating: String? = null,
    focusMeta: String? = null,
    focusOverview: String? = null,
    externallyFocused: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val interactionFocused by interactionSource.collectIsFocusedAsState()
    val focused = interactionFocused || externallyFocused
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(150),
        label = "posterScale"
    )
    val border = if (focused) {
        ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, VodNetflixColors.FocusBorder)
            )
        )
    } else {
        TvFocusDefaults.noBorder()
    }

    val hasFocusPopup = focused && (
        !focusOverview.isNullOrBlank() || !focusRating.isNullOrBlank() || !focusMeta.isNullOrBlank()
        )
    Box(
        modifier = modifier
            .width(PosterWidth)
            .height(PosterHeight + if (hasFocusPopup) 84.dp else 0.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
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
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .background(VodNetflixColors.Accent)
                    )
                }
            }

            if (!focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = title,
                        color = VodNetflixColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        }

        if (hasFocusPopup) {
            VodFocusInfoPopup(
                title = title,
                rating = focusRating,
                meta = focusMeta,
                overview = focusOverview,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = PosterHeight + 6.dp)
                    .zIndex(2f)
            )
        }
    }
}

@Composable
fun NetflixCategoryRow(
    title: String,
    modifier: Modifier = Modifier,
    seeAllLabel: String? = null,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = VodNetflixColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (seeAllLabel != null && onSeeAll != null) {
                GridFocusSurface(
                    onClick = onSeeAll,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                ) {
                    Text(
                        text = seeAllLabel,
                        color = VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun NetflixPosterCardWithMeta(
    title: String,
    posterUrl: String?,
    subtitle: String?,
    progressFraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(PosterWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NetflixPosterCard(
            title = title,
            posterUrl = posterUrl,
            progressFraction = progressFraction,
            onClick = onClick
        )
        Text(
            text = title,
            color = VodNetflixColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = VodNetflixColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ContinueWatchingLandscapeCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "cwScale"
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

    Column(
        modifier = modifier.width(LandscapeWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .width(LandscapeWidth)
                .height(LandscapeHeight)
                .scale(scale),
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = VodNetflixColors.CardPlaceholder,
                focusedContainerColor = VodNetflixColors.CardPlaceholder
            ),
            scale = TvFocusDefaults.NoScale,
            border = border,
            glow = TvFocusDefaults.noGlow()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!item.posterUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.posterUrl)
                            .size(Size(600, 336))
                            .crossfade(200)
                            .build(),
                        contentDescription = item.title,
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
                            text = item.title.take(2).uppercase(),
                            color = VodNetflixColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 22.sp
                        )
                    }
                }
                item.progressFraction.takeIf { it in 0.01f..0.98f }?.let { fraction ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .background(VodNetflixColors.Accent)
                        )
                    }
                }
            }
        }
        Text(
            text = item.title,
            color = VodNetflixColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatContinueWatchingSubtitle(item),
            color = VodNetflixColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VodCategoryFilterRow(
    chips: List<String>,
    selectedIndex: Int,
    focusedIndex: Int,
    rowFocused: Boolean,
    onChipSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    firstChipFocusRequester: FocusRequester? = null
) {
    if (chips.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(chips.size) { index ->
            val label = chips[index]
            val selected = index == selectedIndex
            val focused = rowFocused && index == focusedIndex
            val chipModifier = if (index == 0 && firstChipFocusRequester != null) {
                Modifier.focusRequester(firstChipFocusRequester)
            } else {
                Modifier
            }
            GridFocusSurface(
                onClick = { onChipSelected(index) },
                modifier = chipModifier.then(
                    if (!selected && focused) {
                        Modifier.border(1.dp, Color.White, RoundedCornerShape(20.dp))
                    } else {
                        Modifier
                    }
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = when {
                        selected -> VodNetflixColors.Accent
                        else -> Color(0xFF2A2A2A)
                    },
                    focusedContainerColor = when {
                        selected -> VodNetflixColors.Accent
                        else -> Color(0xFF3A3A3A)
                    }
                )
            ) {
                Text(
                    text = label,
                    color = if (selected || focused) Color.White else VodNetflixColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun NetflixMovieRow(
    movies: List<VodItem>,
    progressByStreamId: Map<Long, Long>,
    posterUrlFor: (VodItem) -> String?,
    metaSubtitleFor: (VodItem) -> String?,
    onPlayMovie: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null
) {
    if (movies.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
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
            NetflixPosterCardWithMeta(
                title = movie.title,
                posterUrl = posterUrlFor(movie),
                subtitle = metaSubtitleFor(movie),
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
    metaSubtitleFor: (VodItem) -> String?,
    onPlayMovie: (VodItem) -> Unit,
    onSeeAllRow: ((VodBrowseRow) -> Unit)? = null,
    firstRowFocusRequester: FocusRequester? = null
) {
    rows.forEachIndexed { index, row ->
        if (row.movies.isEmpty()) return@forEachIndexed
        item(key = "movie_row_${row.id}") {
            NetflixCategoryRow(
                title = vodBrowseRowDisplayTitle(row.id, row.title),
                seeAllLabel = if (onSeeAllRow != null) "See all →" else null,
                onSeeAll = onSeeAllRow?.let { { it(row) } }
            ) {
                NetflixMovieRow(
                    movies = row.movies,
                    progressByStreamId = progressByStreamId,
                    posterUrlFor = posterUrlFor,
                    metaSubtitleFor = metaSubtitleFor,
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
fun VodGenreSidePanel(
    genres: List<String>,
    selectedIndex: Int,
    focusedIndex: Int,
    panelFocused: Boolean,
    onGenreSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return
    val listState = rememberTvLazyListState()

    LaunchedEffect(focusedIndex, panelFocused, genres.size) {
        if (!panelFocused || genres.isEmpty()) return@LaunchedEffect
        val targetIndex = focusedIndex.coerceIn(0, genres.lastIndex)
        listState.animateScrollToItem(targetIndex)
    }

    Column(
        modifier = modifier
            .width(168.dp)
            .fillMaxHeight()
            .background(Color(0xFF101010))
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .focusProperties { canFocus = false },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Genres",
            color = VodNetflixColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        TvLazyColumn(
            state = listState,
            pivotOffsets = PivotOffsets(parentFraction = 0.3f, childFraction = 0f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusProperties { canFocus = false }
        ) {
            items(
                count = genres.size,
                key = { index -> genres[index] }
            ) { index ->
                val label = genres[index]
                val selected = index == selectedIndex
                val focused = panelFocused && index == focusedIndex
                val chipModifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = false }
                    .then(
                        if (focused) {
                            Modifier.border(1.dp, VodNetflixColors.Accent, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
                GridFocusSurface(
                    onClick = { onGenreSelected(index) },
                    modifier = chipModifier,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = when {
                            selected -> Color(0xFF2A2A2A)
                            else -> Color(0xFF161616)
                        },
                        focusedContainerColor = Color(0xFF333333)
                    )
                ) {
                    Text(
                        text = label,
                        color = if (selected || focused) VodNetflixColors.TextPrimary else VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VodContentFilterPanel(
    selectedFilter: VodContentFilter,
    focusedFilter: VodContentFilter,
    panelFocused: Boolean,
    onFilterSelected: (VodContentFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = VodContentFilter.entries
    Column(
        modifier = modifier
            .width(56.dp)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        filters.forEach { filter ->
            val selected = filter == selectedFilter
            val focused = panelFocused && filter == focusedFilter
            val icon = when (filter) {
                VodContentFilter.ALL -> "☰"
                VodContentFilter.MOVIES -> "▣"
                VodContentFilter.SERIES -> "▤"
            }
            val label = when (filter) {
                VodContentFilter.ALL -> "All"
                VodContentFilter.MOVIES -> "Movies"
                VodContentFilter.SERIES -> "Series"
            }
            val chipModifier = Modifier
                .size(44.dp)
                .focusProperties { canFocus = false }
                .then(
                    if (focused) {
                        Modifier.border(2.dp, VodNetflixColors.Accent, RoundedCornerShape(10.dp))
                    } else if (selected) {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    } else {
                        Modifier
                    }
                )
            GridFocusSurface(
                onClick = { onFilterSelected(filter) },
                modifier = chipModifier,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) Color(0xFF2A2A2A) else Color(0xFF141414),
                    focusedContainerColor = Color(0xFF333333)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        color = if (focused || selected) VodNetflixColors.Accent else VodNetflixColors.TextSecondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = label,
                        color = if (focused || selected) VodNetflixColors.TextPrimary else VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixContentWallRow(
    row: VodWallRow,
    rowIndex: Int,
    focusedColumn: Int,
    rowFocused: Boolean,
    listState: LazyListState,
    progressByStreamId: Map<Long, Long>,
    posterUrlForMovie: (VodItem) -> String?,
    ratingForMovie: (VodItem) -> String?,
    metaForMovie: (VodItem) -> String?,
    overviewForMovie: (VodItem) -> String?,
    overviewForSeries: (SeriesShow) -> String?,
    onActivateItem: (VodWallItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(rowFocused, focusedColumn) {
        if (rowFocused && focusedColumn in row.items.indices) {
            listState.animateScrollToItem(focusedColumn.coerceAtLeast(0))
        }
    }

    NetflixCategoryRow(title = row.title, modifier = modifier) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(row.items.size, key = { row.items[it].key }) { index ->
                val item = row.items[index]
                val externallyFocused = rowFocused && index == focusedColumn
                val itemModifier = Modifier.focusProperties { canFocus = false }
                when (item) {
                    is VodWallItem.ContinueItem -> {
                        val cw = item.item
                        NetflixPosterCard(
                            title = cw.title,
                            posterUrl = cw.posterUrl,
                            progressFraction = cw.progressFraction.takeIf { it in 0.01f..0.98f },
                            onClick = { onActivateItem(item) },
                            focusMeta = formatContinueWatchingSubtitle(cw),
                            focusOverview = cw.subtitle.ifBlank { "Continue watching" },
                            externallyFocused = externallyFocused,
                            modifier = itemModifier
                        )
                    }
                    is VodWallItem.MovieItem -> {
                        val movie = item.movie
                        val durationMs = parseVodDurationMs(movie.duration)
                        val progressMs = progressByStreamId[movie.streamId]
                        val fraction = if (durationMs != null && progressMs != null && durationMs > 0L) {
                            (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        NetflixPosterCard(
                            title = movie.title,
                            posterUrl = posterUrlForMovie(movie),
                            progressFraction = fraction,
                            onClick = { onActivateItem(item) },
                            focusRating = ratingForMovie(movie),
                            focusMeta = metaForMovie(movie),
                            focusOverview = overviewForMovie(movie) ?: movie.plot,
                            externallyFocused = externallyFocused,
                            modifier = itemModifier
                        )
                    }
                    is VodWallItem.SeriesItem -> {
                        val show = item.show
                        NetflixPosterCard(
                            title = show.name,
                            posterUrl = show.coverUrl,
                            progressFraction = null,
                            onClick = { onActivateItem(item) },
                            focusMeta = "Series",
                            focusOverview = overviewForSeries(show) ?: show.genre,
                            externallyFocused = externallyFocused,
                            modifier = itemModifier
                        )
                    }
                }
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
private fun VodSearchCategoryHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = VodNetflixColors.TextPrimary,
        fontFamily = DmSansFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(start = 16.dp, top = 8.dp, bottom = 12.dp)
            .focusProperties { canFocus = false }
    )
}

/**
 * Inline VOD search: query field plus poster card grids grouped by category.
 * Replaces the old full-screen text-list overlay so focus stays in one hierarchy.
 */
@Composable
fun VodInlineSearchContent(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    resultsFocusRequester: FocusRequester,
    contentFilter: VodContentFilter,
    moviePagingItems: LazyPagingItems<VodItem>,
    seriesPagingItems: LazyPagingItems<SeriesShow>,
    progressByStreamId: Map<Long, Long>,
    movieProgressFraction: (VodGridCardModel, Map<Long, Long>) -> Float?,
    onMovieClick: (VodItem) -> Unit,
    onSeriesCardClick: (VodGridCardModel) -> Unit,
    onFocusSearchField: () -> Unit = {},
    onFocusResults: () -> Unit = {},
    onNavigateUpFromResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val movieResults = moviePagingItems.itemSnapshotList.items
    val seriesResults = seriesPagingItems.itemSnapshotList.items
    val showMovies = contentFilter != VodContentFilter.SERIES
    val showSeries = contentFilter != VodContentFilter.MOVIES
    val hasMovieResults = showMovies && movieResults.isNotEmpty()
    val hasSeriesResults = showSeries && seriesResults.isNotEmpty()
    val hasResults = hasMovieResults || hasSeriesResults
    val firstResultsFocusRequester = if (hasMovieResults) {
        resultsFocusRequester
    } else if (hasSeriesResults) {
        resultsFocusRequester
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Search movies & series",
            color = VodNetflixColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.focusProperties { canFocus = false }
        )
        TvDialogSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            focusRequester = searchFocusRequester,
            label = "Search",
            confirmLabel = "Done",
            modifier = Modifier.fillMaxWidth(),
            downFocusRequester = firstResultsFocusRequester,
            onFocusChanged = { focused -> if (focused) onFocusSearchField() },
            onImeSubmitted = onFocusResults
        )

        when {
            query.isBlank() -> {
                Text(
                    text = "Type to search your VOD catalog",
                    color = VodNetflixColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.focusProperties { canFocus = false }
                )
            }
            !hasResults -> {
                Text(
                    text = "No results for \"$query\"",
                    color = VodNetflixColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.focusProperties { canFocus = false }
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasMovieResults) {
                        VodSearchCategoryHeader(title = "Movies")
                        VodMoviePagedGrid(
                            pagingItems = moviePagingItems,
                            progressByStreamId = progressByStreamId,
                            progressFraction = movieProgressFraction,
                            onItemClick = onMovieClick,
                            firstItemFocusRequester = resultsFocusRequester,
                            onNavigateUpFromFirstRow = onNavigateUpFromResults,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    if (hasSeriesResults) {
                        VodSearchCategoryHeader(title = "Series")
                        VodPagedVerticalGrid(
                            pagingItems = seriesPagingItems,
                            progressByStreamId = progressByStreamId,
                            progressFraction = { _, _ -> null },
                            onItemClick = onSeriesCardClick,
                            firstItemFocusRequester = if (!hasMovieResults) resultsFocusRequester else null,
                            onNavigateUpFromFirstRow = onNavigateUpFromResults,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}
