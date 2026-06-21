package com.grid.tv.ui.screen

import com.grid.tv.ui.component.GlowFocusButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.grid.tv.domain.model.SeriesEpisode
import com.grid.tv.domain.model.SeriesSeason
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import android.util.Log
import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.PlaylistType
import com.grid.tv.domain.model.VodCatalogEmptyReason
import com.grid.tv.domain.model.vodEmptyMessage
import com.grid.tv.domain.model.vodEmptyTitle
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.ui.component.VodPagedVerticalGrid
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodCatalogProgressBar
import com.grid.tv.ui.component.VodCategoryChip
import com.grid.tv.ui.component.VodEmptyState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.grid.tv.ui.component.EpisodeDetailOverlay
import com.grid.tv.ui.component.EpisodeWatchStatus
import com.grid.tv.ui.component.VodEpisodeCard
import com.grid.tv.ui.viewmodel.SelectedEpisodeDetail
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.ui.viewmodel.SeriesViewModel
import com.grid.tv.ui.viewmodel.SettingsViewModel
import com.grid.tv.ui.viewmodel.VodHubViewModel
import kotlinx.coroutines.launch

@Composable
fun SeriesBrowserScreen(
    initialSeriesId: Long? = null,
    onPlayUrl: (String, String, Boolean) -> Unit,
    onBack: () -> Unit = {},
    embedded: Boolean = false,
    hubSearchQuery: String = "",
    contentFocusRequester: FocusRequester? = null,
    onMoveFocusUp: (() -> Unit)? = null,
    hubViewModel: VodHubViewModel = hiltViewModel(),
    overlayDetail: Boolean = false,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    if (overlayDetail) {
        SeriesDetailOverlay(
            initialSeriesId = initialSeriesId,
            onPlayUrl = onPlayUrl,
            hubSearchQuery = hubSearchQuery,
            viewModel = viewModel,
            hubViewModel = hubViewModel
        )
        return
    }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val seriesPagingItems = viewModel.pagedSeries.collectAsLazyPagingItems()
    val catalogTotalCount by viewModel.catalogTotalCount.collectAsStateWithLifecycle()
    val filteredTotalCount by viewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val catalogProgress by viewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogStatus by viewModel.catalogStatus.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedShowId by viewModel.selectedShowId.collectAsStateWithLifecycle()
    val selectedShow by viewModel.selectedShow.collectAsStateWithLifecycle()
    val selectedShowOverview by viewModel.selectedShowOverview.collectAsStateWithLifecycle()
    val seasonsLoading by viewModel.seasonsLoading.collectAsStateWithLifecycle()
    val selectedSeasonNumber by viewModel.selectedSeasonNumber.collectAsStateWithLifecycle()
    val selectedSeasonEpisodes by viewModel.selectedSeasonEpisodes.collectAsStateWithLifecycle()
    val focusedEpisodeNumber by viewModel.focusedEpisodeNumber.collectAsStateWithLifecycle()
    val selectedEpisodeDetail by viewModel.selectedEpisodeDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val continueWatchingItems by hubViewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val playlists by settingsViewModel.playlists.collectAsStateWithLifecycle()
    val isM3uOnly = playlists.isNotEmpty() && playlists.all { it.type == PlaylistType.M3U }

    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val episodeWatchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(hubSearchQuery, embedded) {
        if (embedded) viewModel.setSearchQuery(hubSearchQuery)
    }

    LaunchedEffect(initialSeriesId, continueWatchingItems) {
        if (initialSeriesId == null) return@LaunchedEffect
        val cw = continueWatchingItems.firstOrNull {
            it.contentType == ContinueWatchingContentType.SERIES && it.seriesId == initialSeriesId
        }
        viewModel.selectShow(initialSeriesId, cw?.seasonNumber)
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("Series recording") },
            text = { Text(message.orEmpty()) },
            confirmButton = {
                GlowFocusButton(onClick = { viewModel.clearMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    var activeShow by remember { mutableStateOf<SeriesShow?>(null) }
    LaunchedEffect(selectedShowId, selectedShow, continueWatchingItems) {
        val showId = selectedShowId
        activeShow = when {
            showId == null -> null
            selectedShow != null -> selectedShow
            else -> viewModel.resolveShow(showId)
                ?: continueWatchingItems.firstOrNull {
                    it.contentType == ContinueWatchingContentType.SERIES && it.seriesId == showId
                }?.let { cw ->
                    SeriesShow(
                        id = showId,
                        name = cw.title.substringBefore(" · ").trim().ifBlank { cw.title },
                        coverUrl = cw.posterUrl
                    )
                }
        }
    }

    val detailOpen = selectedShowId != null
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(detailOpen, seasonsLoading, focusedEpisodeNumber) {
        if (detailOpen && !seasonsLoading && focusedEpisodeNumber == null) {
            backFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    val seriesLoading = catalogProgress.isLoading &&
        catalogProgress.isMoviesPhaseComplete &&
        !catalogProgress.isSeriesPhaseComplete
    val activeSearch = if (embedded) hubSearchQuery else searchQuery
    val seriesEmptyReason = catalogStatus.seriesEmptyReason(
        filteredCount = filteredTotalCount,
        catalogTotal = catalogTotalCount,
        category = selectedCategoryId ?: "All",
        searchQuery = activeSearch
    )

    LaunchedEffect(seriesEmptyReason, seriesPagingItems.itemCount, filteredTotalCount, catalogTotalCount, selectedCategoryId, seriesLoading, catalogProgress.seriesPhaseFinished) {
        Log.i(
            "VodCatalogPipeline",
            "Series empty-state: reason=$seriesEmptyReason filter=Series paged=${seriesPagingItems.itemCount} " +
                "filtered=$filteredTotalCount catalog=$catalogTotalCount category=${selectedCategoryId ?: "All"} " +
                "loading=$seriesLoading phaseFinished=${catalogProgress.seriesPhaseFinished}"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .padding(if (embedded) 0.dp else 20.dp)
    ) {
        if (seasons.isEmpty() && !detailOpen) {
            VodCatalogProgressBar(
                progress = catalogProgress.seriesProgressFraction(),
                visible = seriesLoading
            )
        }

        if (!embedded) {
            GlowFocusButton(onClick = onBack) {
                Text("← Back", fontFamily = DmSansFamily)
            }
            Text(
                text = "Series & Shows",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.setSearchQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search series", fontFamily = DmSansFamily) }
            )
        }

        if (seasons.isEmpty() && !detailOpen) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .then(focusUpModifier(onMoveFocusUp))
            ) {
                item(key = "all") {
                    VodCategoryChip(
                        label = "All",
                        selected = selectedCategoryId == null,
                        focused = false,
                        onClick = { viewModel.setCategory(null) },
                        modifier = contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    VodCategoryChip(
                        label = cat.name,
                        selected = selectedCategoryId == cat.id,
                        focused = false,
                        onClick = { viewModel.setCategory(cat.id) }
                    )
                }
            }
        }

        if (seasons.isEmpty() && !detailOpen) {
            VodCatalogLoadingBanner(
                baseMessage = "Fetching your provider's series catalog. Large libraries can take a minute.",
                progress = catalogProgress,
                isMovies = false
            )
        }

        if (detailOpen) {
            val show = activeShow ?: selectedShow ?: SeriesShow(
                id = selectedShowId ?: return@Column,
                name = "Loading…",
                coverUrl = null
            )
            BackHandler {
                if (selectedEpisodeDetail != null) {
                    viewModel.closeEpisodeDetail()
                } else {
                    viewModel.clearShowSelection()
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SeriesDetailHeader(
                        showName = show.name,
                        coverUrl = show.coverUrl,
                        description = resolveSeriesSynopsis(show, selectedShowOverview),
                        onBackToShows = { viewModel.clearShowSelection() },
                        onRecordSeries = { viewModel.recordSeries(show) },
                        showRecord = !isM3uOnly,
                        backFocusRequester = backFocusRequester,
                        useDarkTheme = false,
                        modifier = focusUpModifier(onMoveFocusUp)
                    )
                    if (seasonsLoading) {
                        SeriesDetailLoadingPanel(useDarkTheme = false)
                    } else if (seasons.isEmpty()) {
                        Text(
                            text = "No seasons available from your provider.",
                            color = EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    } else {
                        SeriesSeasonEpisodeSection(
                            seasons = seasons,
                            episodes = selectedSeasonEpisodes,
                            selectedSeasonNumber = selectedSeasonNumber,
                            focusedEpisodeNumber = focusedEpisodeNumber,
                            episodeDetailOpen = selectedEpisodeDetail != null,
                            onSeasonSelected = viewModel::selectSeason,
                            show = show,
                            onEpisodeClick = viewModel::openEpisodeDetail,
                            episodeProgressFraction = { episode ->
                                viewModel.episodeProgressFraction(episode.id, episode.duration)
                            },
                            useDarkTheme = false,
                            firstSeasonFocusRequester = null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                SeriesEpisodeDetailLayer(
                    show = show,
                    selectedEpisodeDetail = selectedEpisodeDetail,
                    useDarkTheme = false,
                    viewModel = viewModel,
                    scope = scope,
                    onPlayUrl = onPlayUrl,
                    watchFocusRequester = episodeWatchFocusRequester
                )
            }
        } else if (seriesPagingItems.itemCount == 0 && catalogProgress.seriesPhaseFinished && !seriesLoading) {
            val title = seriesEmptyReason.vodEmptyTitle(isMovies = false)
            val message = seriesEmptyReason.vodEmptyMessage(catalogStatus, isMovies = false)
            VodEmptyState(
                title = title,
                message = message,
                onRetry = if (seriesEmptyReason != VodCatalogEmptyReason.FILTERED_EMPTY) {
                    { viewModel.refreshCatalog() }
                } else {
                    null
                }
            )
        } else if (!detailOpen) {
            VodPagedVerticalGrid(
                pagingItems = seriesPagingItems,
                progressByStreamId = emptyMap(),
                progressFraction = { _, _ -> null },
                onItemClick = { card ->
                    val cw = continueWatchingItems.firstOrNull {
                        it.contentType == ContinueWatchingContentType.SERIES &&
                            it.seriesId == card.showId
                    }
                    viewModel.selectShow(card.showId, cw?.seasonNumber)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SeriesDetailHeader(
    showName: String,
    coverUrl: String?,
    description: String,
    onBackToShows: () -> Unit,
    onRecordSeries: () -> Unit,
    showRecord: Boolean,
    useDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    backFocusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.12f)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF13131A))
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = showName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = showName,
                color = if (useDarkTheme) VodNetflixColors.TextPrimary else EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                GlowFocusButton(
                    onClick = onBackToShows,
                    modifier = if (backFocusRequester != null) {
                        Modifier.focusRequester(backFocusRequester)
                    } else {
                        Modifier
                    }
                ) {
                    Text("← All Shows", fontFamily = DmSansFamily)
                }
                if (showRecord) {
                    GlowFocusButton(onClick = onRecordSeries) {
                        Text("Record Series", fontFamily = DmSansFamily)
                    }
                }
            }
            SeriesDetailSynopsis(
                text = description,
                useDarkTheme = useDarkTheme,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun SeriesDetailSynopsis(
    text: String,
    useDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val synopsisColor = if (useDarkTheme) {
        Color(0xB3FFFFFF)
    } else {
        EpgColors.TextSecondary
    }
    val scrollState = rememberScrollState()
    Text(
        text = text,
        color = synopsisColor,
        fontFamily = DmSansFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 110.dp)
            .verticalScroll(scrollState)
    )
}

private fun resolveSeriesSynopsis(show: SeriesShow, overview: String?): String =
    overview?.takeIf { it.isNotBlank() }
        ?: show.plot?.takeIf { it.isNotBlank() }
        ?: "No description available."

@Composable
private fun SeriesDetailLoadingPanel(useDarkTheme: Boolean) {
    val textColor = if (useDarkTheme) VodNetflixColors.TextPrimary else EpgColors.TextPrimary
    val secondaryColor = if (useDarkTheme) VodNetflixColors.TextSecondary else EpgColors.TextSecondary
    val trackColor = if (useDarkTheme) Color(0xFF2A2A2A) else EpgColors.BorderSubtle
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = if (useDarkTheme) VodNetflixColors.Accent else EpgColors.Accent,
                strokeWidth = 3.dp
            )
            Text(
                text = "Loading seasons and episodes…",
                color = textColor,
                fontFamily = DmSansFamily,
                fontSize = 15.sp
            )
        }
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = if (useDarkTheme) VodNetflixColors.Accent else EpgColors.Accent,
            trackColor = trackColor
        )
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 2) 0.55f else 1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor.copy(alpha = 0.55f))
            )
        }
        Text(
            text = "Fetching from your provider — this can take a few seconds.",
            color = secondaryColor,
            fontFamily = DmSansFamily,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SeriesSeasonEpisodeSection(
    seasons: List<SeriesSeason>,
    episodes: List<SeriesEpisode>,
    selectedSeasonNumber: Int?,
    focusedEpisodeNumber: Int?,
    episodeDetailOpen: Boolean,
    onSeasonSelected: (Int) -> Unit,
    show: SeriesShow,
    onEpisodeClick: (SeriesEpisode, Int, Int) -> Unit,
    episodeProgressFraction: (SeriesEpisode) -> Float?,
    useDarkTheme: Boolean,
    firstSeasonFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val titleColor = if (useDarkTheme) VodNetflixColors.TextPrimary else EpgColors.TextPrimary
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Seasons",
            color = titleColor,
            fontFamily = DmSansFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            itemsIndexed(seasons, key = { _, season -> season.number }) { index, season ->
                VodCategoryChip(
                    label = "Season ${season.number}",
                    selected = selectedSeasonNumber == season.number,
                    focused = false,
                    onClick = { onSeasonSelected(season.number) },
                    modifier = if (index == 0 && firstSeasonFocusRequester != null) {
                        Modifier.focusRequester(firstSeasonFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
        val episodeGridState = rememberLazyGridState()
        val episodeFocusRequesters = remember(selectedSeasonNumber, episodes.size) {
            List(episodes.size) { FocusRequester() }
        }
        LaunchedEffect(focusedEpisodeNumber, episodes, selectedSeasonNumber, episodeDetailOpen) {
            if (episodeDetailOpen) return@LaunchedEffect
            val targetNumber = focusedEpisodeNumber ?: return@LaunchedEffect
            val index = episodes.indexOfFirst { episode ->
                (episode.episodeNumber ?: episodes.indexOf(episode) + 1) == targetNumber
            }
            if (index < 0) return@LaunchedEffect
            episodeGridState.scrollToItem(index)
            episodeFocusRequesters.getOrNull(index)?.requestFocusSafelyAfterLayout()
        }
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .graphicsLayer { clip = false }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = episodeGridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(episodes, key = { _, episode -> episode.id }) { itemIndex, episode ->
                    val episodeIndex = episode.episodeNumber ?: (itemIndex + 1)
                    val seasonNum = selectedSeasonNumber ?: seasons.firstOrNull()?.number ?: 1
                    VodEpisodeCard(
                        episodeNumber = episodeIndex,
                        title = episode.title,
                        duration = episode.duration,
                        progressFraction = episodeProgressFraction(episode),
                        focusRequester = episodeFocusRequesters.getOrNull(itemIndex),
                        onClick = { onEpisodeClick(episode, seasonNum, episodeIndex) }
                    )
                }
            }
        }
    }
}

private fun focusUpModifier(onMoveFocusUp: (() -> Unit)?): Modifier =
    if (onMoveFocusUp != null) {
        Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                onMoveFocusUp()
                true
            } else {
                false
            }
        }
    } else {
        Modifier
    }

@Composable
private fun SeriesEpisodeDetailLayer(
    show: SeriesShow,
    selectedEpisodeDetail: SelectedEpisodeDetail?,
    useDarkTheme: Boolean,
    viewModel: SeriesViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onPlayUrl: (String, String, Boolean) -> Unit,
    watchFocusRequester: FocusRequester
) {
    val detail = selectedEpisodeDetail ?: return
    var watchStatus by remember(detail.episode.id) {
        mutableStateOf(EpisodeWatchStatus(0L, 0L, null, "Loading progress…"))
    }
    LaunchedEffect(detail.episode.id, detail.seasonNumber, detail.episodeNumber) {
        watchStatus = viewModel.loadEpisodeWatchStatus(
            seriesId = show.id,
            seasonNumber = detail.seasonNumber,
            episodeNumber = detail.episodeNumber,
            episode = detail.episode
        )
    }
    EpisodeDetailOverlay(
        seasonNumber = detail.seasonNumber,
        episodeNumber = detail.episodeNumber,
        episode = detail.episode,
        watchStatus = watchStatus,
        onWatchNow = {
            scope.launch {
                val resume = viewModel.shouldResumeEpisode(
                    seriesId = show.id,
                    seasonNumber = detail.seasonNumber,
                    episodeNumber = detail.episodeNumber,
                    streamId = detail.episode.id
                )
                VodPlaybackHelper.stageSeriesEpisode(
                    show = show,
                    seasonNumber = detail.seasonNumber,
                    episodeNumber = detail.episodeNumber,
                    streamId = detail.episode.id
                )
                viewModel.closeEpisodeDetail()
                onPlayUrl(detail.episode.title, detail.episode.streamUrl, resume)
            }
        },
        onBack = { viewModel.closeEpisodeDetail() },
        watchFocusRequester = watchFocusRequester,
        useDarkTheme = useDarkTheme,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun SeriesDetailOverlay(
    initialSeriesId: Long?,
    onPlayUrl: (String, String, Boolean) -> Unit,
    hubSearchQuery: String,
    viewModel: SeriesViewModel,
    hubViewModel: VodHubViewModel
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedShowId by viewModel.selectedShowId.collectAsStateWithLifecycle()
    val selectedShow by viewModel.selectedShow.collectAsStateWithLifecycle()
    val selectedShowOverview by viewModel.selectedShowOverview.collectAsStateWithLifecycle()
    val seasonsLoading by viewModel.seasonsLoading.collectAsStateWithLifecycle()
    val selectedSeasonNumber by viewModel.selectedSeasonNumber.collectAsStateWithLifecycle()
    val selectedSeasonEpisodes by viewModel.selectedSeasonEpisodes.collectAsStateWithLifecycle()
    val focusedEpisodeNumber by viewModel.focusedEpisodeNumber.collectAsStateWithLifecycle()
    val selectedEpisodeDetail by viewModel.selectedEpisodeDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val continueWatchingItems by hubViewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val playlists by settingsViewModel.playlists.collectAsStateWithLifecycle()
    val isM3uOnly = playlists.isNotEmpty() && playlists.all { it.type == PlaylistType.M3U }
    val scope = rememberCoroutineScope()
    val overlayFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    val episodeWatchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(hubSearchQuery) {
        viewModel.setSearchQuery(hubSearchQuery)
    }

    LaunchedEffect(initialSeriesId, continueWatchingItems) {
        if (initialSeriesId == null) return@LaunchedEffect
        val cw = continueWatchingItems.firstOrNull {
            it.contentType == ContinueWatchingContentType.SERIES && it.seriesId == initialSeriesId
        }
        viewModel.selectShow(initialSeriesId, cw?.seasonNumber)
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("Series recording") },
            text = { Text(message.orEmpty()) },
            confirmButton = {
                GlowFocusButton(onClick = { viewModel.clearMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    if (selectedShowId == null) return

    BackHandler {
        if (selectedEpisodeDetail != null) {
            viewModel.closeEpisodeDetail()
        } else {
            viewModel.clearShowSelection()
        }
    }

    LaunchedEffect(selectedShowId, seasonsLoading, focusedEpisodeNumber, selectedEpisodeDetail) {
        if (selectedEpisodeDetail == null && !seasonsLoading && focusedEpisodeNumber == null) {
            backFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    val resolvedShow = selectedShow ?: SeriesShow(
        id = selectedShowId ?: return,
        name = "Loading…",
        coverUrl = null
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(VodNetflixColors.Background.copy(alpha = 0.96f))
            .focusRequester(overlayFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape)
                ) {
                    if (selectedEpisodeDetail != null) {
                        viewModel.closeEpisodeDetail()
                    } else {
                        viewModel.clearShowSelection()
                    }
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SeriesDetailHeader(
                    showName = resolvedShow.name,
                    coverUrl = resolvedShow.coverUrl,
                    description = resolveSeriesSynopsis(resolvedShow, selectedShowOverview),
                    onBackToShows = { viewModel.clearShowSelection() },
                    onRecordSeries = { viewModel.recordSeries(resolvedShow) },
                    showRecord = !isM3uOnly,
                    backFocusRequester = backFocusRequester,
                    useDarkTheme = true
                )

                if (seasonsLoading) {
                    SeriesDetailLoadingPanel(useDarkTheme = true)
                } else if (seasons.isEmpty()) {
                    Text(
                        text = "No seasons available from your provider.",
                        color = VodNetflixColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    SeriesSeasonEpisodeSection(
                        seasons = seasons,
                        episodes = selectedSeasonEpisodes,
                        selectedSeasonNumber = selectedSeasonNumber,
                        focusedEpisodeNumber = focusedEpisodeNumber,
                        episodeDetailOpen = selectedEpisodeDetail != null,
                        onSeasonSelected = viewModel::selectSeason,
                        show = resolvedShow,
                        onEpisodeClick = viewModel::openEpisodeDetail,
                        episodeProgressFraction = { episode ->
                            viewModel.episodeProgressFraction(episode.id, episode.duration)
                        },
                        useDarkTheme = true,
                        firstSeasonFocusRequester = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            SeriesEpisodeDetailLayer(
                show = resolvedShow,
                selectedEpisodeDetail = selectedEpisodeDetail,
                useDarkTheme = true,
                viewModel = viewModel,
                scope = scope,
                onPlayUrl = onPlayUrl,
                watchFocusRequester = episodeWatchFocusRequester
            )
        }
    }
}
