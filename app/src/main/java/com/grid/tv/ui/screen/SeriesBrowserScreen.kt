package com.grid.tv.ui.screen

import com.grid.tv.ui.component.GlowFocusButton
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.grid.tv.ui.component.VodEpisodeCard
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
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
    viewModel: SeriesViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    hubViewModel: VodHubViewModel = hiltViewModel()
) {
    val pagedCards by viewModel.pagedCards.collectAsStateWithLifecycle()
    val catalogTotalCount by viewModel.catalogTotalCount.collectAsStateWithLifecycle()
    val filteredTotalCount by viewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val catalogProgress by viewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogStatus by viewModel.catalogStatus.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedShowId by viewModel.selectedShowId.collectAsStateWithLifecycle()
    val selectedSeasonNumber by viewModel.selectedSeasonNumber.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val continueWatchingItems by hubViewModel.continueWatchingItems.collectAsStateWithLifecycle()
    val playlists by settingsViewModel.playlists.collectAsStateWithLifecycle()
    val isM3uOnly = playlists.isNotEmpty() && playlists.all { it.type == PlaylistType.M3U }

    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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

    val activeShow = remember(selectedShowId, continueWatchingItems) {
        val showId = selectedShowId ?: return@remember null
        viewModel.findShow(showId)
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

    val seriesLoading = catalogProgress.isLoading &&
        catalogProgress.isMoviesPhaseComplete &&
        !catalogProgress.isSeriesPhaseComplete
    val activeSearch = if (embedded) hubSearchQuery else searchQuery
    val seriesEmptyReason = catalogStatus.seriesEmptyReason(
        filteredCount = filteredTotalCount,
        catalogTotal = catalogTotalCount,
        category = selectedCategory,
        searchQuery = activeSearch
    )

    LaunchedEffect(seriesEmptyReason, pagedCards.size, filteredTotalCount, catalogTotalCount, selectedCategory, seriesLoading, catalogProgress.seriesPhaseFinished) {
        Log.i(
            "VodCatalogPipeline",
            "Series empty-state: reason=$seriesEmptyReason filter=Series paged=${pagedCards.size} " +
                "filtered=$filteredTotalCount catalog=$catalogTotalCount category=$selectedCategory " +
                "loading=$seriesLoading phaseFinished=${catalogProgress.seriesPhaseFinished}"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .padding(if (embedded) 0.dp else 20.dp)
    ) {
        if (seasons.isEmpty()) {
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

        if (seasons.isEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .then(focusUpModifier(onMoveFocusUp))
            ) {
                itemsIndexed(categories) { index, cat ->
                    VodCategoryChip(
                        label = cat,
                        selected = cat == selectedCategory,
                        focused = false,
                        onClick = { viewModel.setCategory(cat) },
                        modifier = if (index == 0 && contentFocusRequester != null) {
                            Modifier.focusRequester(contentFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }

        if (seasons.isEmpty()) {
            VodCatalogLoadingBanner(
                baseMessage = "Fetching your provider's series catalog. Large libraries can take a minute.",
                progress = catalogProgress,
                isMovies = false
            )
        }

        if (seasons.isNotEmpty() && activeShow != null) {
            SeriesDetailHeader(
                showName = activeShow.name,
                coverUrl = activeShow.coverUrl,
                onBackToShows = { viewModel.clearShowSelection() },
                onRecordSeries = { viewModel.recordSeries(activeShow) },
                showRecord = !isM3uOnly,
                modifier = focusUpModifier(onMoveFocusUp)
            )

            Text(
                text = "Seasons",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(seasons, key = { it.number }) { season ->
                    VodCategoryChip(
                        label = "Season ${season.number}",
                        selected = selectedSeasonNumber == season.number,
                        focused = false,
                        onClick = { viewModel.selectSeason(season.number) }
                    )
                }
            }

            val episodes = seasons.firstOrNull { it.number == selectedSeasonNumber }?.episodes
                ?: seasons.firstOrNull()?.episodes
                ?: emptyList()

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodes, key = { it.id }) { episode ->
                    val episodeIndex = episode.episodeNumber ?: (episodes.indexOf(episode) + 1)
                    val seasonNum = selectedSeasonNumber ?: seasons.firstOrNull()?.number ?: 1
                    VodEpisodeCard(
                        episodeNumber = episodeIndex,
                        title = episode.title,
                        duration = episode.duration,
                        progressFraction = viewModel.episodeProgressFraction(episode.id, episode.duration),
                        onClick = {
                            scope.launch {
                                val resume = viewModel.shouldResumeEpisode(
                                    seriesId = activeShow.id,
                                    seasonNumber = seasonNum,
                                    episodeNumber = episodeIndex,
                                    streamId = episode.id
                                )
                                VodPlaybackHelper.stageSeriesEpisode(
                                    show = activeShow,
                                    seasonNumber = seasonNum,
                                    episodeNumber = episodeIndex,
                                    streamId = episode.id
                                )
                                onPlayUrl(episode.title, episode.streamUrl, resume)
                            }
                        }
                    )
                }
            }
        } else if (pagedCards.isEmpty() && catalogProgress.seriesPhaseFinished && !seriesLoading) {
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
        } else if (seasons.isEmpty()) {
            VodPagedVerticalGrid(
                items = pagedCards,
                progressByStreamId = emptyMap(),
                progressFraction = { _, _ -> null },
                onLoadMore = viewModel::loadNextPage,
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
    onBackToShows: () -> Unit,
    onRecordSeries: () -> Unit,
    showRecord: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
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
                color = EpgColors.TextPrimary,
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
                GlowFocusButton(onClick = onBackToShows) {
                    Text("← All Shows", fontFamily = DmSansFamily)
                }
                if (showRecord) {
                    GlowFocusButton(onClick = onRecordSeries) {
                        Text("Record Series", fontFamily = DmSansFamily)
                    }
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
