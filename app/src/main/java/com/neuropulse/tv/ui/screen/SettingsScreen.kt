package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.EpgRowHeight
import com.neuropulse.tv.domain.model.PlaylistType
import com.neuropulse.tv.feature.dashboard.QrCodeGenerator
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onOpenEpgResolver: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val progress by viewModel.m3uProgress.collectAsStateWithLifecycle()
    val best by viewModel.healthBest.collectAsStateWithLifecycle()
    val worst by viewModel.healthWorst.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val dashboardUrl by viewModel.dashboardUrl.collectAsStateWithLifecycle()
    val xtreamAccounts by viewModel.xtreamAccounts.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var refreshHours by remember { mutableStateOf("24") }
    var playlistType by remember { mutableStateOf(PlaylistType.M3U) }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("GRID — Settings") }
        item {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth())
        }
        if (playlistType == PlaylistType.M3U) {
            item {
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("M3U URL") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { playlistType = PlaylistType.M3U }) { Text(if (playlistType == PlaylistType.M3U) "[M3U]" else "M3U") }
                Button(onClick = { playlistType = PlaylistType.XTREAM }) { Text(if (playlistType == PlaylistType.XTREAM) "[Xtream]" else "Xtream") }
            }
        }
        if (playlistType == PlaylistType.XTREAM) {
            item {
                OutlinedTextField(value = xtreamServer, onValueChange = { xtreamServer = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = xtreamUser, onValueChange = { xtreamUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = xtreamPass, onValueChange = { xtreamPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            OutlinedTextField(value = epgUrl, onValueChange = { epgUrl = it }, label = { Text("XMLTV URL (optional)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = refreshHours, onValueChange = { refreshHours = it }, label = { Text("Refresh interval (hours)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (playlistType == PlaylistType.XTREAM) {
                        viewModel.addXtreamPlaylist(name, xtreamServer, xtreamUser, xtreamPass, epgUrl.ifBlank { null }, refreshHours.toIntOrNull() ?: 24)
                    } else {
                        viewModel.addPlaylistFromUrl(name, url, epgUrl.ifBlank { null }, refreshHours.toIntOrNull() ?: 24)
                    }
                }) { Text("Add URL") }
                Button(onClick = onPickLocalFile) { Text("Add Local File") }
                Button(onClick = { viewModel.refreshEpg() }) { Text("Refresh EPG") }
                Button(onClick = onPickTiviMateZip) { Text("Import from TiviMate Backup") }
                Button(onClick = onOpenEpgResolver) { Text("EPG Auto-Resolve Channels") }
            }
        }

        item { Text("Parsing progress: $progress") }
        item { if (importSummary != null) Text(importSummary ?: "") }
        item { Button(onClick = { viewModel.startDashboard() }) { Text("Start Web Dashboard") } }
        item {
            if (dashboardUrl != null) {
                Text("Dashboard URL: ${dashboardUrl}")
                val qr = QrCodeGenerator.generate(dashboardUrl!!)
                Image(bitmap = qr.asImageBitmap(), contentDescription = "Dashboard QR")
            }
        }

        item { Text("Installed Playlists") }
        items(playlists) { p -> Text("${p.name} [${p.type}] (${p.refreshIntervalHours}h)") }

        item { Text("Xtream Account Info") }
        items(xtreamAccounts) { account ->
            val exp = account.expiryDateEpochSec?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it * 1000L))
            } ?: "N/A"
            Text("${account.playlistName}: ${account.status}")
            Text("Expiry: $exp | Max connections: ${account.maxConnections ?: 0}")
            Text("Server: ${account.serverUrl}")
        }

        item { Text("Playback") }
        item {
            Button(onClick = { viewModel.updateMiniPlayerAudio(!settings.miniPlayerAudioEnabled) }) {
                Text(
                    if (settings.miniPlayerAudioEnabled) {
                        "Mini player audio while browsing: ON"
                    } else {
                        "Mini player audio while browsing: OFF"
                    }
                )
            }
        }

        item { Text("EPG Row Height") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.updateRowHeight(EpgRowHeight.COMPACT) }) { Text("Compact") }
                Button(onClick = { viewModel.updateRowHeight(EpgRowHeight.NORMAL) }) { Text("Normal") }
                Button(onClick = { viewModel.updateRowHeight(EpgRowHeight.LARGE) }) { Text("Large") }
            }
        }

        item { Text("Stream retries: ${settings.streamRetries}") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.updateRetries(2) }) { Text("2") }
                Button(onClick = { viewModel.updateRetries(3) }) { Text("3") }
                Button(onClick = { viewModel.updateRetries(5) }) { Text("5") }
            }
        }

        item {
            OutlinedTextField(
                value = settings.preferredAudioLanguage,
                onValueChange = { viewModel.updateAudioLanguage(it) },
                label = { Text("Preferred audio language") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { Text("Parental controls: PIN-protected groups UI placeholder") }

        item { Text("Scheduled recordings") }
        items(recordings) { rec -> Text(rec) }

        item { Text("Stream Health Report - Most Reliable") }
        items(best) { item -> Text("CH ${item.channelId} score ${item.reliabilityScore}") }

        item { Text("Stream Health Report - Least Reliable") }
        items(worst) { item -> Text("CH ${item.channelId} score ${item.reliabilityScore}") }

        item {
            Text("TODO Recording Engine")
            Text("Requires local storage planning, foreground service scheduling, and MediaMuxer pipeline for transport stream remuxing.")
        }

        item { Text("About: GRID v2.0.0") }
        item { Text("GRID — Live TV Guide for Android TV") }
    }
}
