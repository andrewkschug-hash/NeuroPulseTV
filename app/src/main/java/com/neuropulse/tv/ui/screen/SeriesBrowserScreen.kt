package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.neuropulse.tv.ui.viewmodel.SeriesViewModel

@Composable
fun SeriesBrowserScreen(
    onPlayUrl: (String, String) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val shows by viewModel.shows.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    var selectedSeason by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Series")
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shows) { show ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(model = show.coverUrl, contentDescription = show.name, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = {
                        viewModel.selectShow(show.id)
                        selectedSeason = null
                    }) { Text(show.name) }
                }
            }
        }

        if (seasons.isNotEmpty()) {
            Text("Seasons")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                seasons.forEach { season ->
                    Button(onClick = { selectedSeason = season.number }) {
                        Text(if (selectedSeason == season.number) "[S${season.number}]" else "S${season.number}")
                    }
                }
            }
            Text("Episodes")
            val episodes = seasons.firstOrNull { it.number == selectedSeason }?.episodes ?: seasons.first().episodes
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(episodes) { episode ->
                    Button(onClick = { onPlayUrl(episode.streamUrl, episode.title) }) {
                        Text("${episode.title} (${episode.duration ?: "N/A"})")
                    }
                }
            }
        }
    }
}
