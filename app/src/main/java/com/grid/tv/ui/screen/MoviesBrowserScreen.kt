package com.grid.tv.ui.screen

import android.util.Log
import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import com.grid.tv.domain.model.VodCatalogEmptyReason
import com.grid.tv.domain.model.VodCatalogStatus
import com.grid.tv.domain.model.VodPlaybackHelper
import com.grid.tv.domain.model.vodEmptyMessage
import com.grid.tv.domain.model.vodEmptyTitle
import com.grid.tv.ui.component.VodCatalogLoadingBanner
import com.grid.tv.ui.component.VodCatalogProgressBar
import com.grid.tv.ui.component.VodCategoryChip
import com.grid.tv.ui.component.VodEmptyState
import com.grid.tv.ui.component.VodPosterCard
import com.grid.tv.ui.component.VodPosterSkeleton
import com.grid.tv.ui.component.showsHdBadge
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.MoviesViewModel
import kotlinx.coroutines.launch

@Composable
fun MoviesBrowserScreen(
    onPlayMovie: (String, String, Boolean) -> Unit,
    onBack: () -> Unit = {},
    embedded: Boolean = false,
    hubSearchQuery: String = "",
    contentFocusRequester: FocusRequester? = null,
    onMoveFocusUp: (() -> Unit)? = null,
    onMovieBrowse: (com.grid.tv.domain.model.VodItem) -> Unit = {},
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val allMovies by viewModel.allMovies.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val catalogProgress by viewModel.catalogProgress.collectAsStateWithLifecycle()
    val catalogStatus by viewModel.catalogStatus.collectAsStateWithLifecycle()
    val progress by viewModel.vodProgress.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(hubSearchQuery, embedded) {
        if (embedded) viewModel.setSearchQuery(hubSearchQuery)
    }

    val genreOptions = remember(categories) {
        listOf(null to "All") + categories.distinctBy { it.id }.map { it.id to it.name }
    }

    val moviesLoading = catalogProgress.isLoading && !catalogProgress.isMoviesPhaseComplete
    val activeSearch = if (embedded) hubSearchQuery else search
    val emptyReason = catalogStatus.moviesEmptyReason(
        filteredCount = movies.size,
        catalogTotal = allMovies.size,
        categoryId = selectedCategoryId,
        searchQuery = activeSearch
    )

    LaunchedEffect(emptyReason, movies.size, allMovies.size, selectedCategoryId, moviesLoading, catalogProgress.moviesPhaseFinished) {
        Log.i(
            "VodCatalogPipeline",
            "Movies empty-state: reason=$emptyReason filter=Movies filtered=${movies.size} " +
                "catalog=${allMovies.size} category=$selectedCategoryId loading=$moviesLoading " +
                "phaseFinished=${catalogProgress.moviesPhaseFinished}"
        )
    }

    val skeletonCount = remember(movies.size, catalogProgress.moviesTotal, moviesLoading) {
        if (!moviesLoading || catalogProgress.moviesTotal <= movies.size) {
            0
        } else {
            (catalogProgress.moviesTotal - movies.size).coerceIn(1, 32)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
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
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    viewModel.setSearchQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search movies") }
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .then(
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
                )
        ) {
            itemsIndexed(genreOptions, key = { _, option -> option.first ?: "all" }) { index, option ->
                val (categoryId, label) = option
                val selected = selectedCategoryId == categoryId
                VodCategoryChip(
                    label = label,
                    selected = selected,
                    focused = false,
                    onClick = { viewModel.setCategory(categoryId) },
                    modifier = if (index == 0 && contentFocusRequester != null) {
                        Modifier.focusRequester(contentFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }

        VodCatalogLoadingBanner(
            baseMessage = "Fetching your provider's movie catalog. Large libraries can take a minute.",
            progress = catalogProgress,
            isMovies = true
        )

        when {
            movies.isEmpty() && skeletonCount == 0 && catalogProgress.moviesPhaseFinished && !moviesLoading -> {
                val title = emptyReason.vodEmptyTitle(isMovies = true)
                val message = emptyReason.vodEmptyMessage(catalogStatus, isMovies = true)
                VodEmptyState(
                    title = title,
                    message = message,
                    onRetry = if (emptyReason != VodCatalogEmptyReason.FILTERED_EMPTY) {
                        { viewModel.refreshCatalog() }
                    } else {
                        null
                    }
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(movies, key = { "${it.playlistId}_${it.streamId}" }) { movie ->
                        VodPosterCard(
                            title = movie.title,
                            posterUrl = movie.posterUrl,
                            progressFraction = viewModel.progressFraction(movie, progress),
                            showHdBadge = movie.showsHdBadge(),
                            onClick = {
                                scope.launch {
                                    onMovieBrowse(movie)
                                    val resume = viewModel.shouldResume(movie, progress)
                                    VodPlaybackHelper.stageMovie(movie)
                                    onPlayMovie(movie.title, movie.streamUrl, resume)
                                }
                            }
                        )
                    }
                    items(skeletonCount, key = { index -> "movie_skeleton_$index" }) {
                        VodPosterSkeleton()
                    }
                }
            }
        }
    }
}
