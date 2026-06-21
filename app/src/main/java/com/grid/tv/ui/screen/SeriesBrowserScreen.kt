package com.grid.tv.ui.screen

import com.grid.tv.ui.component.GlowFocusButton
import com.grid.tv.ui.component.cleanVodDisplayTitle
import com.grid.tv.ui.component.parseVodLanguageBadge
import com.grid.tv.ui.component.parseVodReleaseYear
import com.grid.tv.ui.component.parseVodResolutionBadge
import com.grid.tv.ui.component.tvFocusBorder
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
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.runtime.mutableIntStateOf
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
                        name = cleanVodDisplayTitle(
                            cw.title.substringBefore(" · ").trim().ifBlank { cw.title }
                        ),
                        coverUrl = cw.posterUrl
                    )
                }
        }
    }

    val detailOpen = selectedShowId != null

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
                SeriesDetailPane(
                    show = show,
                    description = resolveSeriesSynopsis(show, selectedShowOverview),
                    seasons = seasons,
                    episodes = selectedSeasonEpisodes,
                    selectedSeasonNumber = selectedSeasonNumber,
                    seasonsLoading = seasonsLoading,
                    showRecord = !isM3uOnly,
                    useDarkTheme = false,
                    initialFocusedEpisodeNumber = focusedEpisodeNumber,
                    episodeDetailOpen = selectedEpisodeDetail != null,
                    onBackToShows = { viewModel.clearShowSelection() },
                    onRecordSeries = { viewModel.recordSeries(show) },
                    onSeasonSelected = viewModel::selectSeason,
                    onEpisodePlay = { episode, seasonNumber, episodeNumber ->
                        scope.launch {
                            val resume = viewModel.shouldResumeEpisode(
                                seriesId = show.id,
                                seasonNumber = seasonNumber,
                                episodeNumber = episodeNumber,
                                streamId = episode.id
                            )
                            VodPlaybackHelper.stageSeriesEpisode(
                                show = show,
                                seasonNumber = seasonNumber,
                                episodeNumber = episodeNumber,
                                streamId = episode.id
                            )
                            onPlayUrl(cleanVodDisplayTitle(episode.title), episode.streamUrl, resume)
                        }
                    },
                    episodeProgressFraction = { episode ->
                        viewModel.episodeProgressFraction(episode.id, episode.duration)
                    },
                    onMoveFocusUp = onMoveFocusUp,
                    modifier = Modifier.fillMaxSize()
                )
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

private enum class SeriesDetailFocusZone {
    HEADER,
    SEASONS,
    EPISODES
}

private enum class SeriesHeaderFocusTarget {
    DESCRIPTION,
    BACK,
    RECORD
}

@Composable
private fun SeriesDetailPane(
    show: SeriesShow,
    description: String,
    seasons: List<SeriesSeason>,
    episodes: List<SeriesEpisode>,
    selectedSeasonNumber: Int?,
    seasonsLoading: Boolean,
    showRecord: Boolean,
    useDarkTheme: Boolean,
    initialFocusedEpisodeNumber: Int?,
    episodeDetailOpen: Boolean,
    onBackToShows: () -> Unit,
    onRecordSeries: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodePlay: (SeriesEpisode, Int, Int) -> Unit,
    episodeProgressFraction: (SeriesEpisode) -> Float?,
    onMoveFocusUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val episodeColumns = 2
    val rootFocusRequester = remember { FocusRequester() }
    val episodeGridState = rememberLazyGridState()
    var focusZone by remember(show.id) { mutableStateOf(SeriesDetailFocusZone.HEADER) }
    var headerTarget by remember(show.id) { mutableStateOf(SeriesHeaderFocusTarget.BACK) }
    var seasonFocusIndex by remember(show.id) { mutableIntStateOf(0) }
    var episodeRow by remember(show.id) { mutableIntStateOf(0) }
    var episodeCol by remember(show.id) { mutableIntStateOf(0) }

    LaunchedEffect(seasons, selectedSeasonNumber) {
        if (seasons.isEmpty()) return@LaunchedEffect
        seasonFocusIndex = seasons.indexOfFirst { it.number == selectedSeasonNumber }
            .coerceAtLeast(0)
    }

    LaunchedEffect(initialFocusedEpisodeNumber, episodes, seasonsLoading) {
        if (seasonsLoading) return@LaunchedEffect
        val target = initialFocusedEpisodeNumber ?: return@LaunchedEffect
        val index = episodes.indexOfFirst { episode ->
            (episode.episodeNumber ?: episodes.indexOf(episode) + 1) == target
        }
        if (index >= 0) {
            episodeRow = index / episodeColumns
            episodeCol = index % episodeColumns
            focusZone = SeriesDetailFocusZone.EPISODES
        }
    }

    LaunchedEffect(seasonFocusIndex, focusZone) {
        if (focusZone != SeriesDetailFocusZone.SEASONS && focusZone != SeriesDetailFocusZone.EPISODES) {
            return@LaunchedEffect
        }
        seasons.getOrNull(seasonFocusIndex)?.let { onSeasonSelected(it.number) }
    }

    LaunchedEffect(focusZone, episodeRow, episodeCol, episodes.size, episodeDetailOpen) {
        if (episodeDetailOpen) return@LaunchedEffect
        if (focusZone == SeriesDetailFocusZone.EPISODES) {
            val index = episodeRow * episodeColumns + episodeCol
            if (index in episodes.indices) {
                episodeGridState.scrollToItem(index)
            }
        }
        rootFocusRequester.requestFocusSafelyAfterLayout()
    }

    fun episodeIndex(): Int = episodeRow * episodeColumns + episodeCol

    fun playFocusedEpisode() {
        val index = episodeIndex()
        val episode = episodes.getOrNull(index) ?: return
        val seasonNum = selectedSeasonNumber ?: seasons.firstOrNull()?.number ?: 1
        val episodeNum = episode.episodeNumber ?: (index + 1)
        onEpisodePlay(episode, seasonNum, episodeNum)
    }

    fun handleDetailKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (episodeDetailOpen) return false
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft -> when (focusZone) {
                SeriesDetailFocusZone.HEADER -> {
                    when (headerTarget) {
                        SeriesHeaderFocusTarget.BACK -> {
                            if (showRecord) headerTarget = SeriesHeaderFocusTarget.RECORD
                        }
                        SeriesHeaderFocusTarget.RECORD -> Unit
                        SeriesHeaderFocusTarget.DESCRIPTION -> Unit
                    }
                    true
                }
                SeriesDetailFocusZone.SEASONS -> {
                    if (seasonFocusIndex > 0) {
                        seasonFocusIndex -= 1
                    } else {
                        focusZone = SeriesDetailFocusZone.HEADER
                        headerTarget = SeriesHeaderFocusTarget.BACK
                    }
                    true
                }
                SeriesDetailFocusZone.EPISODES -> {
                    if (episodeCol > 0) episodeCol -= 1
                    true
                }
            }
            Key.DirectionRight -> when (focusZone) {
                SeriesDetailFocusZone.HEADER -> {
                    when (headerTarget) {
                        SeriesHeaderFocusTarget.BACK -> {
                            if (showRecord) headerTarget = SeriesHeaderFocusTarget.RECORD
                        }
                        SeriesHeaderFocusTarget.RECORD -> Unit
                        SeriesHeaderFocusTarget.DESCRIPTION -> Unit
                    }
                    true
                }
                SeriesDetailFocusZone.SEASONS -> {
                    if (seasonFocusIndex < seasons.lastIndex) seasonFocusIndex += 1
                    true
                }
                SeriesDetailFocusZone.EPISODES -> {
                    val index = episodeIndex()
                    if (episodeCol < episodeColumns - 1 && index + 1 < episodes.size) {
                        episodeCol += 1
                    }
                    true
                }
            }
            Key.DirectionUp -> when (focusZone) {
                SeriesDetailFocusZone.HEADER -> {
                    if (headerTarget != SeriesHeaderFocusTarget.DESCRIPTION) {
                        headerTarget = SeriesHeaderFocusTarget.DESCRIPTION
                    } else {
                        onMoveFocusUp?.invoke()
                    }
                    true
                }
                SeriesDetailFocusZone.SEASONS -> {
                    focusZone = SeriesDetailFocusZone.HEADER
                    headerTarget = SeriesHeaderFocusTarget.DESCRIPTION
                    true
                }
                SeriesDetailFocusZone.EPISODES -> {
                    if (episodeRow > 0) {
                        episodeRow -= 1
                        val maxCol = ((episodes.size - 1) % episodeColumns)
                        if (episodeRow == (episodes.size - 1) / episodeColumns) {
                            episodeCol = episodeCol.coerceAtMost(maxCol)
                        }
                    } else {
                        focusZone = SeriesDetailFocusZone.SEASONS
                    }
                    true
                }
            }
            Key.DirectionDown -> when (focusZone) {
                SeriesDetailFocusZone.HEADER -> {
                    focusZone = SeriesDetailFocusZone.SEASONS
                    true
                }
                SeriesDetailFocusZone.SEASONS -> {
                    if (episodes.isNotEmpty()) {
                        focusZone = SeriesDetailFocusZone.EPISODES
                        episodeRow = 0
                        episodeCol = 0
                    }
                    true
                }
                SeriesDetailFocusZone.EPISODES -> {
                    val nextRow = episodeRow + 1
                    val maxRow = (episodes.size - 1) / episodeColumns
                    if (nextRow <= maxRow) {
                        episodeRow = nextRow
                        val maxCol = ((episodes.size - 1) % episodeColumns)
                        if (episodeRow == maxRow) {
                            episodeCol = episodeCol.coerceAtMost(maxCol)
                        }
                    }
                    true
                }
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> when (focusZone) {
                SeriesDetailFocusZone.HEADER -> {
                    when (headerTarget) {
                        SeriesHeaderFocusTarget.BACK -> onBackToShows()
                        SeriesHeaderFocusTarget.RECORD -> onRecordSeries()
                        SeriesHeaderFocusTarget.DESCRIPTION -> Unit
                    }
                    true
                }
                SeriesDetailFocusZone.SEASONS -> {
                    seasons.getOrNull(seasonFocusIndex)?.let { onSeasonSelected(it.number) }
                    true
                }
                SeriesDetailFocusZone.EPISODES -> {
                    playFocusedEpisode()
                    true
                }
            }
            else -> false
        }
    }

    Box(
        modifier = modifier
            .focusRequester(rootFocusRequester)
            .focusable(enabled = !episodeDetailOpen)
            .focusProperties { canFocus = !episodeDetailOpen }
            .onPreviewKeyEvent { handleDetailKey(it) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SeriesDetailHeader(
                rawShowName = show.name,
                coverUrl = show.coverUrl,
                description = description,
                onBackToShows = onBackToShows,
                onRecordSeries = onRecordSeries,
                showRecord = showRecord,
                useDarkTheme = useDarkTheme,
                headerTarget = headerTarget,
                headerFocused = focusZone == SeriesDetailFocusZone.HEADER
            )
            if (seasonsLoading) {
                SeriesDetailLoadingPanel(useDarkTheme = useDarkTheme)
            } else if (seasons.isEmpty()) {
                Text(
                    text = "No seasons available from your provider.",
                    color = if (useDarkTheme) VodNetflixColors.TextSecondary else EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                SeriesSeasonEpisodeSection(
                    seasons = seasons,
                    episodes = episodes,
                    selectedSeasonNumber = selectedSeasonNumber,
                    seasonFocusIndex = seasonFocusIndex,
                    episodeRow = episodeRow,
                    episodeCol = episodeCol,
                    episodeColumns = episodeColumns,
                    focusZone = focusZone,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodePlay = onEpisodePlay,
                    episodeProgressFraction = episodeProgressFraction,
                    useDarkTheme = useDarkTheme,
                    episodeGridState = episodeGridState,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SeriesDetailHeader(
    rawShowName: String,
    coverUrl: String?,
    description: String,
    onBackToShows: () -> Unit,
    onRecordSeries: () -> Unit,
    showRecord: Boolean,
    useDarkTheme: Boolean,
    headerTarget: SeriesHeaderFocusTarget,
    headerFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val displayTitle = remember(rawShowName) { cleanVodDisplayTitle(rawShowName) }
    val releaseYear = remember(rawShowName) { parseVodReleaseYear(rawShowName) }
    val languageCode = remember(rawShowName) { parseVodLanguageBadge(rawShowName) }
    val resolutionBadge = remember(rawShowName) { parseVodResolutionBadge(rawShowName) }
    val titleColor = if (useDarkTheme) VodNetflixColors.TextPrimary else EpgColors.TextPrimary
    val descriptionFocused = headerFocused && headerTarget == SeriesHeaderFocusTarget.DESCRIPTION
    val backFocused = headerFocused && headerTarget == SeriesHeaderFocusTarget.BACK
    val recordFocused = headerFocused && headerTarget == SeriesHeaderFocusTarget.RECORD
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .focusProperties { canFocus = false },
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
                    contentDescription = displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                color = titleColor,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (releaseYear != null || languageCode != null || resolutionBadge != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    releaseYear?.let { year ->
                        SeriesDetailMetadataPill(text = year, accent = false, useDarkTheme = useDarkTheme)
                    }
                    languageCode?.let { code ->
                        SeriesDetailMetadataPill(text = code, accent = false, useDarkTheme = useDarkTheme)
                    }
                    resolutionBadge?.let { badge ->
                        SeriesDetailMetadataPill(text = badge, accent = true, useDarkTheme = useDarkTheme)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                GlowFocusButton(
                    onClick = onBackToShows,
                    externallyFocused = backFocused,
                    modifier = Modifier.focusProperties { canFocus = false }
                ) {
                    Text("← All Shows", fontFamily = DmSansFamily)
                }
                if (showRecord) {
                    GlowFocusButton(
                        onClick = onRecordSeries,
                        externallyFocused = recordFocused,
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Text("Record Series", fontFamily = DmSansFamily)
                    }
                }
            }
            SeriesDetailSynopsis(
                text = description,
                useDarkTheme = useDarkTheme,
                focused = descriptionFocused,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun SeriesDetailMetadataPill(
    text: String,
    accent: Boolean,
    useDarkTheme: Boolean
) {
    val shape = RoundedCornerShape(999.dp)
    val background = if (accent) {
        Color(0xFFFFD54F).copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    val textColor = when {
        accent -> Color(0xFFFFD54F)
        useDarkTheme -> VodNetflixColors.TextSecondary
        else -> EpgColors.TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(background, shape)
            .border(1.dp, textColor.copy(alpha = 0.35f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SeriesDetailSynopsis(
    text: String,
    useDarkTheme: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val synopsisColor = if (useDarkTheme) {
        Color(0xB3FFFFFF)
    } else {
        EpgColors.TextSecondary
    }
    val scrollState = rememberScrollState()
    val shape = RoundedCornerShape(8.dp)
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
            .tvFocusBorder(
                focused = focused,
                shape = shape,
                width = 2.dp,
                unfocusedWidth = 1.dp,
                unfocusedColor = Color.Transparent,
                focusedColor = if (useDarkTheme) VodNetflixColors.Accent else EpgColors.FocusBorder
            )
            .padding(4.dp)
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
    seasonFocusIndex: Int,
    episodeRow: Int,
    episodeCol: Int,
    episodeColumns: Int,
    focusZone: SeriesDetailFocusZone,
    onSeasonSelected: (Int) -> Unit,
    onEpisodePlay: (SeriesEpisode, Int, Int) -> Unit,
    episodeProgressFraction: (SeriesEpisode) -> Float?,
    useDarkTheme: Boolean,
    episodeGridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val titleColor = if (useDarkTheme) VodNetflixColors.TextPrimary else EpgColors.TextPrimary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
    ) {
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
                    focused = focusZone == SeriesDetailFocusZone.SEASONS && index == seasonFocusIndex,
                    onClick = { onSeasonSelected(season.number) },
                    modifier = Modifier.focusProperties { canFocus = false }
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .graphicsLayer { clip = false }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(episodeColumns),
                state = episodeGridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(episodes, key = { _, episode -> episode.id }) { itemIndex, episode ->
                    val episodeIndex = episode.episodeNumber ?: (itemIndex + 1)
                    val seasonNum = selectedSeasonNumber ?: seasons.firstOrNull()?.number ?: 1
                    val row = itemIndex / episodeColumns
                    val col = itemIndex % episodeColumns
                    VodEpisodeCard(
                        episodeNumber = episodeIndex,
                        title = episode.title,
                        duration = episode.duration,
                        progressFraction = episodeProgressFraction(episode),
                        externallyFocused = focusZone == SeriesDetailFocusZone.EPISODES &&
                            row == episodeRow &&
                            col == episodeCol,
                        onClick = { onEpisodePlay(episode, seasonNum, episodeIndex) },
                        modifier = Modifier.focusProperties { canFocus = false }
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
                onPlayUrl(cleanVodDisplayTitle(detail.episode.title), detail.episode.streamUrl, resume)
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
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        SeriesDetailPane(
            show = resolvedShow,
            description = resolveSeriesSynopsis(resolvedShow, selectedShowOverview),
            seasons = seasons,
            episodes = selectedSeasonEpisodes,
            selectedSeasonNumber = selectedSeasonNumber,
            seasonsLoading = seasonsLoading,
            showRecord = !isM3uOnly,
            useDarkTheme = true,
            initialFocusedEpisodeNumber = focusedEpisodeNumber,
            episodeDetailOpen = selectedEpisodeDetail != null,
            onBackToShows = { viewModel.clearShowSelection() },
            onRecordSeries = { viewModel.recordSeries(resolvedShow) },
            onSeasonSelected = viewModel::selectSeason,
            onEpisodePlay = { episode, seasonNumber, episodeNumber ->
                scope.launch {
                    val resume = viewModel.shouldResumeEpisode(
                        seriesId = resolvedShow.id,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        streamId = episode.id
                    )
                    VodPlaybackHelper.stageSeriesEpisode(
                        show = resolvedShow,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        streamId = episode.id
                    )
                    onPlayUrl(cleanVodDisplayTitle(episode.title), episode.streamUrl, resume)
                }
            },
            episodeProgressFraction = { episode ->
                viewModel.episodeProgressFraction(episode.id, episode.duration)
            },
            modifier = Modifier.fillMaxSize()
        )
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
