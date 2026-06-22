package com.grid.tv.ui.screen

import android.util.Log
import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Text
import com.grid.tv.domain.model.VodCatalogEmptyReason
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.domain.model.vodEmptyMessage
import com.grid.tv.domain.model.vodEmptyTitle
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodCatalogRefreshWarningBanner
import com.grid.tv.ui.component.VodCatalogProgressBar
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.ui.component.netflixMovieBrowseRows
import com.grid.tv.ui.component.NetflixCategoryRow
import com.grid.tv.ui.component.movieMetaSubtitle
import com.grid.tv.ui.component.NetflixMovieRow
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.VodNetflixColors
import com.grid.tv.ui.viewmodel.MoviesViewModel
import kotlinx.coroutines.launch

@Composable
fun MoviesBrowserScreen(
    onPlayMovie: (String, String, Boolean) -> Unit,
    onBack: () -> Unit = {},
    embedded: Boolean = false,
    hubSearchQuery: String = "",
    contentFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onMoveFocusUp: (() -> Unit)? = null,
    onMovieBrowse: (com.grid.tv.domain.model.VodItem) -> Unit = {},
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val moviePagingItems = viewModel.pagedMovies.collectAsLazyPagingItems()
    val catalogTotalCount by viewModel.catalogTotalCount.collectAsStateWithLifecycle()
    val filteredTotalCount by viewModel.filteredTotalCount.collectAsStateWithLifecycle()
    val browseRows by viewModel.browseRows.collectAsStateWithLifecycle()
    val catalogProgress by viewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogStatus by viewModel.catalogStatus.collectAsStateWithLifecycle()
    val progress by viewModel.vodProgress.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var standaloneSearch by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(hubSearchQuery, embedded) {
        if (embedded) viewModel.setSearchQuery(hubSearchQuery)
    }

    val moviesLoading = catalogProgress.isLoading && !catalogProgress.isMoviesPhaseComplete
    val activeSearch = if (embedded) hubSearchQuery else standaloneSearch
    val emptyReason = catalogStatus.moviesEmptyReason(
        filteredCount = filteredTotalCount,
        catalogTotal = catalogTotalCount,
        categoryId = selectedCategoryId,
        searchQuery = activeSearch
    )

    LaunchedEffect(emptyReason, moviePagingItems.itemCount, filteredTotalCount, catalogTotalCount, selectedCategoryId, moviesLoading, catalogProgress.moviesPhaseFinished) {
        Log.i(
            "VodCatalogPipeline",
            "Movies empty-state: reason=$emptyReason filter=Movies paged=${moviePagingItems.itemCount} " +
                "filtered=$filteredTotalCount catalog=$catalogTotalCount category=$selectedCategoryId " +
                "loading=$moviesLoading phaseFinished=${catalogProgress.moviesPhaseFinished}"
        )
    }

    fun playFromMovie(movie: com.grid.tv.domain.model.VodItem) {
        scope.launch {
            onMovieBrowse(movie)
            val resume = viewModel.shouldResume(movie, progress)
            VodPlaybackHelper.stageMovie(movie)
            onPlayMovie(movie.title, movie.streamUrl, resume)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VodNetflixColors.Background)
            .padding(if (embedded) 0.dp else 20.dp)
    ) {
        VodCatalogProgressBar(
            progress = catalogProgress.moviesProgressFraction(),
            visible = moviesLoading
        )

        if (!embedded) {
            GlowFocusButton(onClick = onBack) {
                Text("← Back", fontFamily = DmSansFamily)
            }
            Text(
                text = "Movies",
                color = VodNetflixColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            OutlinedTextField(
                value = standaloneSearch,
                onValueChange = {
                    standaloneSearch = it
                    viewModel.setSearchQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search movies") }
            )
        }

        VodCatalogLoadingBanner(
            baseMessage = "Fetching your provider's movie catalog. Large libraries can take a minute.",
            progress = catalogProgress,
            isMovies = true
        )
        VodCatalogRefreshWarningBanner(
            message = catalogStatus.moviesRefreshWarning(catalogTotalCount)
        )

        when {
            activeSearch.isNotBlank() -> {
                val movies = moviePagingItems.itemSnapshotList.items
                if (movies.isEmpty() && catalogProgress.moviesPhaseFinished && !moviesLoading) {
                    VodEmptyState(
                        title = emptyReason.vodEmptyTitle(isMovies = true),
                        message = emptyReason.vodEmptyMessage(catalogStatus, isMovies = true)
                    )
                } else {
                    NetflixCategoryRow(title = "Search Results") {
                        NetflixMovieRow(
                            movies = movies,
                            progressByStreamId = progress,
                            posterUrlFor = { it.posterUrl },
                            metaSubtitleFor = { movieMetaSubtitle(it, it.rating) },
                            onPlayMovie = { movie -> playFromMovie(movie) }
                        )
                    }
                }
            }
            browseRows.isEmpty() && catalogProgress.moviesPhaseFinished && !moviesLoading -> {
                VodEmptyState(
                    title = emptyReason.vodEmptyTitle(isMovies = true),
                    message = emptyReason.vodEmptyMessage(catalogStatus, isMovies = true),
                    onRetry = if (emptyReason != VodCatalogEmptyReason.FILTERED_EMPTY) {
                        { viewModel.refreshCatalog() }
                    } else {
                        null
                    }
                )
            }
            else -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    netflixMovieBrowseRows(
                        rows = browseRows,
                        progressByStreamId = progress,
                        posterUrlFor = { it.posterUrl },
                        metaSubtitleFor = { movieMetaSubtitle(it, it.rating) },
                        onPlayMovie = { movie -> playFromMovie(movie) }
                    )
                }
            }
        }
    }
}
