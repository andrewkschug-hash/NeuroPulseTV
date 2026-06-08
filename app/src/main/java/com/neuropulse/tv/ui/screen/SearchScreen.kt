package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.component.FocusCard
import com.neuropulse.tv.ui.viewmodel.SearchScheduleFilter
import com.neuropulse.tv.ui.viewmodel.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(
    onPlayChannel: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.updateQuery(it)
            },
            label = { Text("Search channels and programs") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.tv.material3.Button(onClick = { viewModel.setScheduleFilter(SearchScheduleFilter.LIVE_NOW) }) { Text("Live Now") }
            androidx.tv.material3.Button(onClick = { viewModel.setScheduleFilter(SearchScheduleFilter.UPCOMING_TODAY) }) { Text("Upcoming Today") }
        }

        Text("Channels")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(channels) { channel ->
                FocusCard(onClick = { onPlayChannel(channel.id) }) {
                    Column {
                        Text(channel.name)
                        Text("CH ${channel.number}")
                        Text("Playlist: ${channel.playlistName ?: "Unknown"}")
                    }
                }
            }
        }

        Text("Programs")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(programs) { program ->
                FocusCard(onClick = {}) {
                    Column {
                        Text(program.title)
                        Text(SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(program.startTime)))
                        Text("Channel: ${program.channelEpgId}")
                        val now = System.currentTimeMillis()
                        if (now in program.startTime..program.endTime) Text("On Now")
                    }
                }
            }
        }
    }
}
