package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.feature.recording.RecordingStatus
import com.neuropulse.tv.domain.model.EpgResolutionStatus
import coil.compose.AsyncImage
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.ProgramGenre
import com.neuropulse.tv.ui.component.FocusCard
import com.neuropulse.tv.ui.viewmodel.HomeEpgViewModel
import com.neuropulse.tv.ui.viewmodel.EpgResolverViewModel
import com.neuropulse.tv.ui.viewmodel.RecordingViewModel
import java.util.concurrent.TimeUnit

@Composable
fun HomeEpgScreen(
    onWatchChannel: (Long) -> Unit,
    onOpenEpg: () -> Unit = {},
    onPlayUrl: (String, String) -> Unit = { _, _ -> },
    onOpenSeries: () -> Unit = {},
    viewModel: HomeEpgViewModel = hiltViewModel(),
    recordingViewModel: RecordingViewModel = hiltViewModel(),
    epgResolverViewModel: EpgResolverViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val sportsNow by viewModel.sportsNow.collectAsStateWithLifecycle()
    val moviesSoon by viewModel.moviesSoon.collectAsStateWithLifecycle()
    val topChannels by viewModel.topChannels.collectAsStateWithLifecycle()
    val recent by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val vod by viewModel.vod.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val epgWindow by viewModel.epgPrograms.collectAsStateWithLifecycle()
    val epgLoading by viewModel.epgLoading.collectAsStateWithLifecycle()
    val scheduled by recordingViewModel.scheduled.collectAsStateWithLifecycle()
    val suggestionsByChannelId by epgResolverViewModel.suggestionsByChannelId.collectAsStateWithLifecycle()
    val hero = channels.firstOrNull()
    val now = System.currentTimeMillis()
    val current = hero?.epgId?.let { id -> programs.firstOrNull { it.channelEpgId == id && now in it.startTime..it.endTime } }

    var selectedProgram by remember { mutableStateOf<Program?>(null) }
    var selectedSuggestionChannelId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenEpg) { Text("Open EPG") }
            Button(onClick = onOpenSeries) { Text("Open Series") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
            item {
                Text("Continue Watching")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(continueWatching) { ch ->
                        FocusCard(onClick = { onWatchChannel(ch.id) }) { Text("CH ${ch.number} ${ch.name}") }
                    }
                }
            }
            item {
                Text("Recommended for You")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recommendations) { rec ->
                        FocusCard(onClick = { onWatchChannel(rec.channel.id) }) {
                            Column {
                                Text(rec.channel.name)
                                Text(rec.reason)
                            }
                        }
                    }
                }
            }
            item {
                Text("Live Sports Right Now")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sportsNow) { p -> FocusCard(onClick = {}) { Text(p.title) } }
                }
            }
            item {
                Text("Movies Starting Soon")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(moviesSoon) { p -> FocusCard(onClick = {}) { Text(p.title) } }
                }
            }
            item {
                Text("Your Top Channels")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(topChannels) { ch -> FocusCard(onClick = { onWatchChannel(ch.id) }) { Text(ch.name) } }
                }
            }
            item {
                Text("Recently Added")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { ch -> FocusCard(onClick = { onWatchChannel(ch.id) }) { Text(ch.name) } }
                }
            }
            item {
                Text("VOD")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vod.take(20)) { v ->
                        FocusCard(onClick = { onPlayUrl(v.streamUrl, v.title) }) {
                            Column {
                                AsyncImage(model = v.posterUrl, contentDescription = v.title, modifier = Modifier.width(120.dp).height(170.dp))
                                Text(v.title)
                                Text(v.genre ?: "")
                                Text("${v.rating ?: "NR"} | ${v.duration ?: "N/A"}")
                            }
                        }
                    }
                }
            }
            item {
                Text("Series")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(series.take(15)) { s ->
                        FocusCard(onClick = onOpenSeries) {
                            Column {
                                AsyncImage(model = s.coverUrl, contentDescription = s.name, modifier = Modifier.width(120.dp).height(170.dp))
                                Text(s.name)
                            }
                        }
                    }
                }
            }
        }

        // Dedicated EPG view retained under button-access mode.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.loadPrevBlock() }) { Text("<- 2h") }
            Button(onClick = { viewModel.loadNextBlock() }) { Text("2h ->") }
            if (epgLoading) Text("Loading...")
        }
        Box(modifier = Modifier.fillMaxWidth().weight(0.4f).background(Color.Black)) {
            if (hero != null) {
                AsyncImage(model = hero.logoUrl, contentDescription = null, modifier = Modifier.padding(16.dp).width(140.dp).height(80.dp))
                Box(modifier = Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                ).padding(16.dp)) {
                    Column {
                        Text("Now Playing - CH ${hero.number} ${hero.name}")
                        Text(current?.title ?: "No EPG data")
                        val remaining = if (current != null) TimeUnit.MILLISECONDS.toMinutes(current.endTime - now).coerceAtLeast(0) else 0
                        Text("$remaining min remaining")
                    }
                }
            }
        }

        val hScroll = rememberScrollState()
        val vScroll = rememberScrollState()
        Column(modifier = Modifier.height(280.dp).fillMaxWidth().verticalScroll(vScroll).horizontalScroll(hScroll)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                repeat(12) { i ->
                    val hour = (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) + i) % 24
                    Text(String.format("%02d:00", hour), modifier = Modifier.width(170.dp))
                }
                Box(modifier = Modifier.width(2.dp).height(20.dp).background(Color.Cyan))
            }

            channels.forEach { channel ->
                Row(modifier = Modifier.fillMaxWidth().height(72.dp).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${channel.number}", modifier = Modifier.width(56.dp))
                    AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.width(72.dp).height(40.dp))
                    Text(channel.name, modifier = Modifier.width(180.dp))
                    if (channel.epgResolutionStatus == EpgResolutionStatus.SUGGESTED) {
                        val suggestion = suggestionsByChannelId[channel.id.toString()]
                        Text(
                            text = "SUGGESTED",
                            color = Color.Yellow,
                            modifier = Modifier
                                .background(Color(0x664A4A00))
                                .clickable { if (suggestion != null) selectedSuggestionChannelId = suggestion.channelId }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    epgWindow.filter { it.channelEpgId == channel.epgId }.forEach { p ->
                        val color = when (p.genre) {
                            ProgramGenre.NEWS -> Color.Red
                            ProgramGenre.SPORTS -> Color(0xFF2E8B57)
                            ProgramGenre.MOVIES -> Color(0xFF7B1FA2)
                            ProgramGenre.KIDS -> Color(0xFFFFC107)
                            ProgramGenre.GENERAL -> Color(0xFF5A5A5A)
                        }
                        val isCurrent = now in p.startTime..p.endTime
                        val hasRec = scheduled.any {
                            it.channelId == channel.id &&
                                it.programTitle == p.title &&
                                it.startTime == p.startTime &&
                                it.status != RecordingStatus.FAILED.name
                        }
                        Box(
                            modifier = Modifier
                                .width(190.dp)
                                .height(64.dp)
                                .background(color)
                                .then(if (isCurrent) Modifier.background(Color.White.copy(alpha = 0.15f)) else Modifier)
                                .clickable { selectedProgram = p }
                                .padding(8.dp)
                        ) {
                            Text((if (hasRec) "REC " else "") + (if (p.catchupUrl != null && p.endTime < now) "⏪ ${p.title}" else p.title))
                        }
                    }
                }
            }
        }
    }

    val selected = selectedProgram
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedProgram = null },
            title = { Text(selected.title) },
            text = { Text("${selected.description}\n${java.util.Date(selected.startTime)} - ${java.util.Date(selected.endTime)}") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onWatchChannel(channels.firstOrNull { it.epgId == selected.channelEpgId }?.id ?: return@Button) }) { Text("Watch") }
                    Button(onClick = {
                        val channel = channels.firstOrNull { it.epgId == selected.channelEpgId } ?: return@Button
                        val nowMs = System.currentTimeMillis()
                        if (selected.startTime <= nowMs) {
                            val duration = (selected.endTime - nowMs).coerceAtLeast(10 * 60 * 1000)
                            recordingViewModel.startImmediateRecording(context, channel, selected.title, duration)
                        } else {
                            recordingViewModel.scheduleProgram(channel, selected)
                        }
                    }) { Text("Record") }
                }
            }
        )
    }

    val selectedSuggestion = selectedSuggestionChannelId?.let { suggestionsByChannelId[it] }
    if (selectedSuggestion != null) {
        AlertDialog(
            onDismissRequest = { selectedSuggestionChannelId = null },
            title = { Text("Possible EPG match") },
            text = { Text("We found a possible EPG match: ${selectedSuggestion.suggestedEpgName}. Accept?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        epgResolverViewModel.acceptSuggestion(selectedSuggestion)
                        selectedSuggestionChannelId = null
                    }) { Text("Accept") }
                    Button(onClick = {
                        epgResolverViewModel.dismissSuggestion(selectedSuggestion)
                        selectedSuggestionChannelId = null
                    }) { Text("Dismiss") }
                }
            }
        )
    }
}
