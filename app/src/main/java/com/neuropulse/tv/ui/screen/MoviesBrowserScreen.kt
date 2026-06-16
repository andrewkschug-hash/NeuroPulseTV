package com.neuropulse.tv.ui.screen

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
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.VodPlaybackHelper
import com.neuropulse.tv.ui.component.VodCategoryChip
import com.neuropulse.tv.ui.component.VodEmptyState
import com.neuropulse.tv.ui.component.VodPosterCard
import com.neuropulse.tv.ui.component.showsHdBadge
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.MoviesViewModel
import kotlinx.coroutines.launch

@Composable
fun MoviesBrowserScreen(
    onPlayMovie: (String, String, Boolean) -> Unit,
    onBack: () -> Unit = {},
    embedded: Boolean = false,
    hubSearchQuery: String = "",
    contentFocusRequester: FocusRequester? = null,
    onMoveFocusUp: (() -> Unit)? = null,
    onMovieBrowse: (com.neuropulse.tv.domain.model.VodItem) -> Unit = {},
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .padding(if (embedded) 0.dp else 20.dp)
    ) {
        if (!embedded) {
            Button(onClick = onBack) {
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

        if (movies.isEmpty()) {
            VodEmptyState(
                title = "No movies available",
                message = "Add an Xtream playlist with VOD in Settings, or try another category."
            )
        } else {
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
            }
        }
    }
}
