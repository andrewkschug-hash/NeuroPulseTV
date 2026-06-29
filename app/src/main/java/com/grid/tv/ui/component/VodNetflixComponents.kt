package com.grid.tv.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.request.ImageRequest
import coil.size.Size
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.util.TvImageSizing
import com.grid.tv.util.TvTextInputSession

private val PosterWidth = VodPosterFocusLayout.POSTER_WIDTH
private val PosterHeight = VodPosterFocusLayout.POSTER_HEIGHT
private val PosterTitleHeight = VodPosterFocusLayout.POSTER_TITLE_HEIGHT
private val PosterShape = RoundedCornerShape(6.dp)

/** Hub top bar tabs — search lives in the left nav rail, not here. */
val VodHubTabFilters = listOf(
    VodContentFilter.ALL,
    VodContentFilter.MOVIES,
    VodContentFilter.SERIES
)

fun vodHubTabFilterIndex(filter: VodContentFilter): Int =
    VodHubTabFilters.indexOf(filter).coerceAtLeast(0)

fun vodHubTabFilterLabel(filter: VodContentFilter): String = when (filter) {
    VodContentFilter.ALL -> "Home"
    VodContentFilter.MOVIES -> "Movies"
    VodContentFilter.SERIES -> "Series"
    VodContentFilter.SEARCH -> "Search"
}

/** Home wall content-type chips — "All" filters the feed, not the library tab. */
fun vodHomeWallTypeFilterLabel(filter: VodContentFilter): String = when (filter) {
    VodContentFilter.ALL -> "All"
    VodContentFilter.MOVIES -> "Movies"
    VodContentFilter.SERIES -> "Series"
    VodContentFilter.SEARCH -> "Search"
}

/** Focus index for the Languages control (after [VodHubTabFilters]). */
val VodHubLanguageFilterFocusIndex = VodHubTabFilters.size

fun vodBrowseRowDisplayTitle(rowId: String, title: String): String = when (rowId) {
    "top_imdb" -> "Top IMDB picks"
    "4k" -> "4K Ultra HD"
    "recent" -> "Recently Added"
    else -> title
}

fun movieMetaSubtitle(movie: VodItem, rating: String?): String {
    val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(movie.title)?.value
    return listOfNotNull(
        rating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
        year
    ).joinToString(" · ")
}

@Composable
fun NetflixPosterCard(
    rawTitle: String,
    posterUrl: String?,
    progressFraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    externallyFocused: Boolean = false
) {
    val displayTitle = remember(rawTitle) { cleanVodDisplayTitle(rawTitle) }
    val languageBadge = remember(rawTitle) { parseVodLanguageBadge(rawTitle) }
    val resolutionBadge = remember(rawTitle) { parseVodResolutionBadge(rawTitle) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectVodCardFocused(externallyFocused)
    val border = if (focused) {
        ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    VodPosterFocusLayout.NETFLIX_FOCUS_BORDER,
                    VodNetflixColors.FocusBorder
                )
            )
        )
    } else {
        TvFocusDefaults.noBorder()
    }

    val edgeH = VodPosterFocusLayout.netflixEdgePaddingHorizontal
    val edgeV = VodPosterFocusLayout.netflixEdgePaddingVertical
    val overflowY = VodPosterFocusLayout.scaleOverflowY
    val titleTop = edgeV + PosterHeight + overflowY + VodPosterFocusLayout.POSTER_TITLE_GAP

    Column(
        modifier = modifier.width(VodPosterFocusLayout.netflixCardWidth)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VodPosterFocusLayout.netflixCardHeight)
        ) {
            Surface(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = edgeV)
                    .width(PosterWidth)
                    .height(PosterHeight)
                    .vodCardFocusPop(interactionSource, externallyFocused)
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
                        TvPosterImage(
                            url = posterUrl,
                            contentDescription = displayTitle,
                            kind = PosterImageKind.VodGrid,
                            placeholderLetter = displayTitle,
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
                                text = displayTitle.take(2).uppercase(),
                                color = VodNetflixColors.TextSecondary,
                                fontFamily = DmSansFamily,
                                fontSize = 18.sp
                            )
                        }
                    }

                    languageBadge?.let { badge ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                color = Color.White,
                                fontFamily = DmSansFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    resolutionBadge?.let { badge ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color(0xCC78350F), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                color = Color(0xFFFFD54F),
                                fontFamily = DmSansFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    progressFraction?.takeIf { it in 0.01f..0.98f }?.let { fraction ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(3.dp)
                                    .background(VodNetflixColors.Accent)
                            )
                        }
                    }
                }
            }

            Text(
                text = displayTitle,
                color = VodNetflixColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = titleTop)
                    .width(PosterWidth)
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
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = VodPosterFocusLayout.categoryRowTopPadding,
                bottom = VodPosterFocusLayout.categoryRowBottomPadding
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = VodPosterFocusLayout.categoryTitleBottomGap
                ),
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
            titleTrailing?.invoke()
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
        modifier = modifier.width(VodPosterFocusLayout.netflixCardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NetflixPosterCard(
            rawTitle = title,
            posterUrl = posterUrl,
            progressFraction = progressFraction,
            onClick = onClick
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
    progressByKey: Map<Pair<Long, Long>, Long>,
    posterUrlFor: (VodItem) -> String?,
    metaSubtitleFor: (VodItem) -> String?,
    onPlayMovie: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null
) {
    if (movies.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = 48.dp,
            vertical = VodPosterFocusLayout.lazyRowVerticalPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(movies, key = { "${it.playlistId}_${it.streamId}" }) { movie ->
            val durationMs = parseVodDurationMs(movie.duration)
            val progressMs = progressByKey[movie.playlistId to movie.streamId]
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
        contentPadding = PaddingValues(
            horizontal = 48.dp,
            vertical = VodPosterFocusLayout.lazyRowVerticalPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shows, key = { "${it.playlistId}_${it.id}" }) { show ->
            val itemModifier = if (show == shows.first() && firstItemFocusRequester != null) {
                Modifier.focusRequester(firstItemFocusRequester)
            } else {
                Modifier
            }
            NetflixPosterCard(
                rawTitle = show.name,
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
    progressByKey: Map<Pair<Long, Long>, Long>,
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
                    progressByKey = progressByKey,
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

/** Full-width library popout panel (Home / Movies / Series / Languages). */
val VodLibraryNavPanelExpandedWidth = 200.dp

@Deprecated("Icon-only rail removed — library is a labeled popout only.", level = DeprecationLevel.HIDDEN)
val VodLibraryNavPanelCollapsedWidth = VodLibraryNavPanelExpandedWidth

/** Genre / language sub-panels overlay at the expanded sidebar's trailing edge. */
val VodLibrarySubPanelOffsetExpanded = VodLibraryNavPanelExpandedWidth

/** Overlay width for genre and language sub-panels. */
val VodLibrarySubPanelWidth = 180.dp

@Deprecated("Use VodLibraryNavPanelExpandedWidth", ReplaceWith("VodLibraryNavPanelExpandedWidth"))
val VodLibraryNavPanelWidth = VodLibraryNavPanelExpandedWidth

fun vodLibraryNavItemIcon(index: Int, filter: VodContentFilter?): String = when {
    index == VodHubLanguageFilterFocusIndex -> "◉"
    filter == VodContentFilter.ALL -> "⌂"
    filter == VodContentFilter.MOVIES -> "▣"
    filter == VodContentFilter.SERIES -> "▤"
    else -> "•"
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodLibraryNavPanel(
    selectedFilter: VodContentFilter,
    focusedIndex: Int,
    panelFocused: Boolean,
    languageFilterActive: Boolean,
    tabsNavigable: Boolean,
    panelFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    navDrawerFocusRequester: FocusRequester,
    genrePanelFocusRequester: FocusRequester? = null,
    onPanelFocused: () -> Unit,
    onFocusedIndexChange: (Int) -> Unit,
    onItemSelected: (Int) -> Unit,
    onPreviewKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val itemCount = VodHubLanguageFilterFocusIndex + 1
    val listState = rememberLazyListState()
    val accent = VodNetflixColors.Accent
    val chipShape = RoundedCornerShape(10.dp)

    LaunchedEffect(focusedIndex, panelFocused, itemCount) {
        if (!panelFocused || itemCount <= 0) return@LaunchedEffect
        listState.animateScrollToItem(focusedIndex.coerceIn(0, itemCount - 1))
    }

    Column(
        modifier = modifier
            .width(VodLibraryNavPanelExpandedWidth)
            .fillMaxHeight()
            .zIndex(2f)
            .background(EpgColors.SidebarPanelBg.copy(alpha = 0.97f))
            .border(
                width = 1.dp,
                color = EpgColors.BorderSubtle,
                shape = RoundedCornerShape(0.dp),
            )
            .focusRequester(panelFocusRequester)
            .focusable()
            .focusProperties {
                left = navDrawerFocusRequester
                right = genrePanelFocusRequester ?: contentFocusRequester
            }
            .onFocusChanged { if (it.isFocused) onPanelFocused() }
            .onPreviewKeyEvent(onPreviewKey),
    ) {
        Text(
            text = "Library",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(itemCount) { index ->
                val isLanguages = index == VodHubLanguageFilterFocusIndex
                val filter = VodHubTabFilters.getOrNull(index)
                val label = if (isLanguages) {
                    "Languages"
                } else {
                    filter?.let { vodHubTabFilterLabel(it) } ?: ""
                }
                val icon = vodLibraryNavItemIcon(index, filter)
                val tabSelected = !isLanguages && filter == selectedFilter
                val focused = panelFocused && index == focusedIndex
                val showLanguageDot = isLanguages && languageFilterActive
                val enabled = tabsNavigable || tabSelected || isLanguages
                val chipModifier = Modifier
                    .clip(chipShape)
                    .then(
                        when {
                            focused -> Modifier
                                .background(accent.copy(alpha = 0.28f), chipShape)
                                .border(2.dp, accent.copy(alpha = 0.95f), chipShape)
                            tabSelected -> Modifier.background(accent.copy(alpha = 0.18f), chipShape)
                            else -> Modifier.background(Color(0xFF161616), chipShape)
                        },
                    )
                    .then(
                        if (enabled) {
                            Modifier.clickable { onItemSelected(index) }
                        } else {
                            Modifier
                        },
                    )
                    .focusProperties { canFocus = false }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .then(chipModifier)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier.width(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = icon,
                                color = if (focused || tabSelected) Color.White else Color(0xFFB8BEC8),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                            )
                            if (showLanguageDot) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(6.dp)
                                        .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                                )
                            }
                        }
                        Text(
                            text = label,
                            color = when {
                                focused || tabSelected -> Color.White
                                enabled -> Color(0xFFB8BEC8)
                                else -> Color(0xFF6B7280)
                            },
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            fontWeight = if (tabSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodGenreSidePanel(
    genres: List<String>,
    selectedIndex: Int,
    focusedIndex: Int,
    panelFocused: Boolean,
    onGenreSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentGridFocusRequester: FocusRequester? = null,
    libraryNavFocusRequester: FocusRequester? = null,
    entryFocusRequester: FocusRequester? = null,
    onFocusedIndexChange: ((Int) -> Unit)? = null,
    onPreviewKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null
) {
    if (genres.isEmpty()) return
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex, panelFocused, genres.size) {
        if (!panelFocused || genres.isEmpty()) return@LaunchedEffect
        val targetIndex = focusedIndex.coerceIn(0, genres.lastIndex)
        listState.animateScrollToItem(targetIndex)
    }

    Column(
        modifier = modifier
            .width(VodLibrarySubPanelWidth)
            .fillMaxHeight()
            .zIndex(3f)
            .background(EpgColors.SidebarPanelBg.copy(alpha = 0.97f))
            .border(
                width = 1.dp,
                color = EpgColors.BorderSubtle,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .then(
                if (entryFocusRequester != null) {
                    Modifier
                        .focusRequester(entryFocusRequester)
                        .focusable()
                        .focusProperties {
                            canFocus = panelFocused
                            if (libraryNavFocusRequester != null) {
                                left = libraryNavFocusRequester
                            }
                            if (contentGridFocusRequester != null) {
                                right = contentGridFocusRequester
                            }
                        }
                        .onPreviewKeyEvent { onPreviewKey?.invoke(it) ?: false }
                } else {
                    Modifier.focusProperties { canFocus = false }
                }
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Genres",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .focusProperties { canFocus = false }
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(
                count = genres.size,
                key = { index -> index }
            ) { index ->
                val label = genres[index]
                val selected = index == selectedIndex
                val focused = panelFocused && index == focusedIndex
                val containerColor = when {
                    selected -> Color(0xFF2A2A2A)
                    focused -> Color(0xFF333333)
                    else -> Color(0xFF161616)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerColor)
                        .clickable { onGenreSelected(index) }
                        .focusProperties { canFocus = false }
                        .then(
                            if (focused) {
                                Modifier.border(1.dp, VodNetflixColors.Accent, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodLanguageSubmenuPanel(
    availableLanguages: List<String>,
    selectedLanguages: Set<String>,
    focusedIndex: Int,
    panelFocused: Boolean,
    onFocusedIndexChange: (Int) -> Unit,
    onLanguageToggle: (String?) -> Unit,
    onPreviewKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester? = null,
    libraryNavFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
) {
    val itemCount = availableLanguages.size + 1
    val listState = rememberLazyListState()
    val chipShape = RoundedCornerShape(8.dp)
    val accent = VodNetflixColors.Accent

    LaunchedEffect(focusedIndex, panelFocused, itemCount) {
        if (!panelFocused || itemCount <= 0) return@LaunchedEffect
        listState.animateScrollToItem(focusedIndex.coerceIn(0, itemCount - 1))
    }

    Column(
        modifier = modifier
            .width(VodLibrarySubPanelWidth)
            .fillMaxHeight()
            .zIndex(3f)
            .background(EpgColors.SidebarPanelBg.copy(alpha = 0.97f))
            .border(
                width = 1.dp,
                color = EpgColors.BorderSubtle,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .then(
                if (entryFocusRequester != null) {
                    Modifier
                        .focusRequester(entryFocusRequester)
                        .focusable()
                        .focusProperties {
                            canFocus = panelFocused
                            if (libraryNavFocusRequester != null) {
                                left = libraryNavFocusRequester
                            }
                            if (contentFocusRequester != null) {
                                right = contentFocusRequester
                            }
                        }
                        .onPreviewKeyEvent(onPreviewKey)
                } else {
                    Modifier.focusProperties { canFocus = false }
                }
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Languages",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .focusProperties { canFocus = false },
        )
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(
                count = itemCount,
                key = { index -> index },
            ) { index ->
                val isAllLanguages = index == 0
                val code = availableLanguages.getOrNull(index - 1)
                val label = if (isAllLanguages) {
                    "All Languages"
                } else {
                    com.grid.tv.feature.vod.displayLanguageName(code!!)
                }
                val selected = if (isAllLanguages) {
                    selectedLanguages.isEmpty()
                } else {
                    code!!.uppercase() in selectedLanguages.map { it.uppercase() }.toSet()
                }
                val focused = panelFocused && index == focusedIndex
                val emphasized = selected || focused
                val containerColor = when {
                    emphasized -> accent.copy(alpha = 0.28f)
                    else -> Color(0xFF161616)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(chipShape)
                        .background(containerColor, chipShape)
                        .then(
                            if (focused) {
                                Modifier.border(2.dp, accent.copy(alpha = 0.95f), chipShape)
                            } else if (selected) {
                                Modifier.border(1.dp, accent.copy(alpha = 0.65f), chipShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onLanguageToggle(if (isAllLanguages) null else code) }
                        .focusProperties { canFocus = false }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            color = if (emphasized) Color.White else Color(0xFFB8BEC8),
                            fontFamily = DmSansFamily,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(
                                text = "✓",
                                color = accent,
                                fontFamily = DmSansFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodContentFilterTabBar(
    selectedFilter: VodContentFilter,
    focusedFilter: VodContentFilter,
    barFocused: Boolean,
    onFilterSelected: (VodContentFilter) -> Unit,
    tabsNavigable: Boolean = true,
    languageFilterActive: Boolean = false,
    languageFilterFocused: Boolean = false,
    onLanguageFilterClick: () -> Unit = {},
    tabBarFocusRequester: FocusRequester? = null,
    heroPlayFocusRequester: FocusRequester? = null,
    sidebarFocusRequester: FocusRequester? = null,
    linkDownToHero: Boolean = false,
    onBarFocused: () -> Unit = {},
    onPreviewKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val filters = VodHubTabFilters
    val pillShape = RoundedCornerShape(999.dp)
    val chipShape = RoundedCornerShape(999.dp)
    val accent = VodNetflixColors.Accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .then(
                if (tabBarFocusRequester != null) {
                    Modifier
                        .focusRequester(tabBarFocusRequester)
                        .focusable(enabled = barFocused)
                        .focusProperties {
                            if (linkDownToHero && heroPlayFocusRequester != null) {
                                down = heroPlayFocusRequester
                            }
                            if (sidebarFocusRequester != null) left = sidebarFocusRequester
                        }
                        .onFocusChanged { if (it.isFocused) onBarFocused() }
                        .onPreviewKeyEvent { onPreviewKey?.invoke(it) ?: false }
                } else {
                    Modifier.focusProperties { canFocus = false }
                }
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color(0xFF121820).copy(alpha = 0.72f)
                        )
                    ),
                    pillShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.22f), pillShape)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filters.forEach { filter ->
                val selected = filter == selectedFilter
                val focused = barFocused && filter == focusedFilter && !languageFilterFocused
                val emphasized = selected || focused
                val label = vodHomeWallTypeFilterLabel(filter)
                val tabEnabled = tabsNavigable || filter == selectedFilter
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .then(
                            if (emphasized) {
                                Modifier
                                    .background(accent.copy(alpha = 0.28f), chipShape)
                                    .border(
                                        width = if (focused) 2.dp else 1.dp,
                                        color = accent.copy(alpha = if (focused) 0.95f else 0.65f),
                                        shape = chipShape
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (tabEnabled) {
                                Modifier.clickable { onFilterSelected(filter) }
                            } else {
                                Modifier
                            }
                        )
                        .focusProperties { canFocus = false }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        color = if (emphasized) {
                            Color.White
                        } else if (tabEnabled) {
                            Color(0xFFB8BEC8)
                        } else {
                            Color(0xFF6B7280)
                        },
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        val languageFocused = barFocused && languageFilterFocused
        val languageEmphasized = languageFilterActive || languageFocused
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (languageEmphasized) 0.16f else 0.10f),
                            Color(0xFF121820).copy(alpha = 0.68f)
                        )
                    ),
                    pillShape
                )
                .border(
                    width = if (languageFocused) 2.dp else 1.dp,
                    color = when {
                        languageFocused -> accent.copy(alpha = 0.95f)
                        languageFilterActive -> accent.copy(alpha = 0.65f)
                        else -> Color.White.copy(alpha = 0.20f)
                    },
                    shape = pillShape
                )
                .clickable(onClick = onLanguageFilterClick)
                .focusProperties { canFocus = false }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "🌐", fontSize = 16.sp)
            Text(
                text = if (languageFilterActive) "Languages ●" else "Languages",
                color = if (languageEmphasized) Color.White else Color(0xFFB8BEC8),
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = if (languageFilterActive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VodContentFilterPanel(
    selectedFilter: VodContentFilter,
    focusedFilter: VodContentFilter,
    panelFocused: Boolean,
    onFilterSelected: (VodContentFilter) -> Unit,
    modifier: Modifier = Modifier,
    showGenrePanel: Boolean = false,
    genrePanelFocusRequester: FocusRequester? = null,
    contentGridFocusRequester: FocusRequester? = null,
    entryFocusRequester: FocusRequester? = null
) {
    val filters = VodContentFilter.entries
    val filterFocusRequesters = remember { List(filters.size) { FocusRequester() } }

    LaunchedEffect(focusedFilter, panelFocused) {
        if (!panelFocused) return@LaunchedEffect
        val index = filters.indexOf(focusedFilter).coerceAtLeast(0)
        val requester = if (entryFocusRequester != null) {
            entryFocusRequester
        } else {
            filterFocusRequesters.getOrNull(index)
        }
        requester?.requestFocusSafelyAfterLayout()
    }

    Column(
        modifier = modifier
            .width(56.dp)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        filters.forEachIndexed { index, filter ->
            val selected = filter == selectedFilter
            val focused = panelFocused && filter == focusedFilter
            val icon = when (filter) {
                VodContentFilter.ALL -> "☰"
                VodContentFilter.MOVIES -> "▣"
                VodContentFilter.SERIES -> "▤"
                VodContentFilter.SEARCH -> "⌕"
            }
            val label = when (filter) {
                VodContentFilter.ALL -> "All"
                VodContentFilter.MOVIES -> "Movies"
                VodContentFilter.SERIES -> "Series"
                VodContentFilter.SEARCH -> "Search"
            }
            val containerColor = when {
                selected -> Color(0xFF2A2A2A)
                focused -> Color(0xFF333333)
                else -> Color(0xFF141414)
            }
            val rowFocusRequester = if (focused && entryFocusRequester != null) {
                entryFocusRequester
            } else {
                filterFocusRequesters[index]
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor)
                    .clickable { onFilterSelected(filter) }
                    .focusRequester(rowFocusRequester)
                    .focusable(enabled = panelFocused)
                    .focusProperties {
                        right = when {
                            showGenrePanel && genrePanelFocusRequester != null -> genrePanelFocusRequester
                            contentGridFocusRequester != null -> contentGridFocusRequester
                            else -> FocusRequester.Default
                        }
                    }
                    .onFocusChanged { /* UI-only — filter apply happens on Enter/click via onFilterSelected */ }
                    .then(
                        if (focused) {
                            Modifier.border(2.dp, VodNetflixColors.Accent, RoundedCornerShape(10.dp))
                        } else if (selected) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NetflixContentWallRow(
    row: VodWallRow,
    rowIndex: Int,
    focusedColumn: Int,
    rowFocused: Boolean,
    listState: LazyListState,
    progressByKey: Map<Pair<Long, Long>, Long>,
    posterUrlForMovie: (VodItem) -> String?,
    ratingForMovie: (VodItem) -> String?,
    metaForMovie: (VodItem) -> String?,
    overviewForMovie: (VodItem) -> String?,
    overviewForSeries: (SeriesShow) -> String?,
    onActivateItem: (VodWallItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    heroPlayFocusRequester: FocusRequester? = null,
    sidebarFocusRequester: FocusRequester? = null,
    linkUpToHero: Boolean = false,
) {
    LaunchedEffect(rowFocused, focusedColumn) {
        if (rowFocused && focusedColumn in row.items.indices) {
            listState.animateScrollToItem(focusedColumn.coerceAtLeast(0))
        }
    }

    // Title + poster row share one Column so LazyColumn viewport passes cannot clip the header.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VodPosterFocusLayout.estimatedWallRowHeight)
            .graphicsLayer { clip = false }
    ) {
        Text(
            text = row.title,
            color = VodNetflixColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = VodPosterFocusLayout.categoryRowTopPadding,
                    bottom = VodPosterFocusLayout.categoryTitleBottomGap
                )
                .heightIn(min = VodPosterFocusLayout.categoryTitleBandHeight)
        )
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(VodPosterFocusLayout.wallRowLazyRowHeight)
                .padding(bottom = VodPosterFocusLayout.categoryRowBottomPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = VodPosterFocusLayout.lazyRowVerticalPadding,
                bottom = VodPosterFocusLayout.lazyRowVerticalPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(row.items.size, key = { row.items[it].key }) { index ->
                val item = row.items[index]
                val externallyFocused = rowFocused && index == focusedColumn
                val itemModifier = Modifier
                    .then(
                        if (index == 0 && firstItemFocusRequester != null) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .focusProperties {
                        canFocus = false
                        if (index == 0 && sidebarFocusRequester != null) {
                            left = sidebarFocusRequester
                            if (rowIndex == 0) {
                                up = sidebarFocusRequester
                            }
                        }
                        if (linkUpToHero && index == 0 && heroPlayFocusRequester != null) {
                            up = heroPlayFocusRequester
                        }
                    }
                when (item) {
                    is VodWallItem.ContinueItem -> {
                        val cw = item.item
                        NetflixPosterCard(
                            rawTitle = cw.title,
                            posterUrl = cw.posterUrl,
                            progressFraction = cw.progressFraction.takeIf { it in 0.01f..0.98f },
                            onClick = { onActivateItem(item) },
                            externallyFocused = externallyFocused,
                            modifier = itemModifier
                        )
                    }
                    is VodWallItem.MovieItem -> {
                        val movie = item.movie
                        val durationMs = parseVodDurationMs(movie.duration)
                        val progressMs = progressByKey[movie.playlistId to movie.streamId]
                        val fraction = if (durationMs != null && progressMs != null && durationMs > 0L) {
                            (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        NetflixPosterCard(
                            rawTitle = movie.title,
                            posterUrl = posterUrlForMovie(movie),
                            progressFraction = fraction,
                            onClick = { onActivateItem(item) },
                            externallyFocused = externallyFocused,
                            modifier = itemModifier
                        )
                    }
                    is VodWallItem.SeriesItem -> {
                        val show = item.show
                        NetflixPosterCard(
                            rawTitle = show.name,
                            posterUrl = show.coverUrl,
                            progressFraction = null,
                            onClick = { onActivateItem(item) },
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
    moviePagingItems: LazyPagingItems<VodItem>?,
    seriesPagingItems: LazyPagingItems<SeriesShow>?,
    progressByKey: Map<Pair<Long, Long>, Long>,
    movieProgressFraction: (VodItem, Map<Pair<Long, Long>, Long>) -> Float?,
    onMovieClick: (VodItem) -> Unit,
    onSeriesCardClick: (VodGridCardModel) -> Unit,
    onFocusSearchField: () -> Unit = {},
    onFocusResults: () -> Unit = {},
    onNavigateUpFromResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showMovies = contentFilter != VodContentFilter.SERIES
    val showSeries = contentFilter != VodContentFilter.MOVIES
    val movieRefreshComplete = moviePagingItems?.loadState?.refresh !is LoadState.Loading
    val seriesRefreshComplete = seriesPagingItems?.loadState?.refresh !is LoadState.Loading
    val hasMovieResults = showMovies && (moviePagingItems?.itemCount ?: 0) > 0
    val hasSeriesResults = showSeries && (seriesPagingItems?.itemCount ?: 0) > 0
    val hasResults = hasMovieResults || hasSeriesResults
    val showNoResults = query.isNotBlank() &&
        (if (showMovies) movieRefreshComplete else true) &&
        (if (showSeries) seriesRefreshComplete else true) &&
        !hasResults
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
            showNoResults -> {
                Text(
                    text = "No results for \"$query\"",
                    color = VodNetflixColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.focusProperties { canFocus = false }
                )
            }
            moviePagingItems != null && seriesPagingItems != null -> {
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
                            progressByKey = progressByKey,
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
                            progressByKey = progressByKey,
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
