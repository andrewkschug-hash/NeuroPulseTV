package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.component.VodPosterCard
import com.neuropulse.tv.ui.component.showsHdBadge
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.MoviesViewModel

@Composable
fun MoviesBrowserScreen(
    onPlayMovie: (String, String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val progress by viewModel.vodProgress.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }

    val genreOptions = remember(categories) {
        listOf(null to "All") + categories.distinctBy { it.id }.map { it.id to it.name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
            .padding(20.dp)
    ) {
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
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            itemsIndexed(genreOptions, key = { _, option -> option.first ?: "all" }) { _, option ->
                val (categoryId, label) = option
                val selected = selectedCategoryId == categoryId
                Surface(onClick = { viewModel.setCategory(categoryId) }) {
                    Text(
                        text = label,
                        color = if (selected) EpgColors.Accent else EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
        if (movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No movies available", color = EpgColors.TextSecondary, fontFamily = DmSansFamily)
            }
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
                        onClick = { onPlayMovie(movie.title, movie.streamUrl) }
                    )
                }
            }
        }
    }
}
