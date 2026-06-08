package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.EpgRowHeight
import com.neuropulse.tv.domain.model.PlaylistType
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsCategory(val label: String) {
    Playlists("Playlists"),
    Epg("EPG / Guide"),
    Playback("Playback"),
    Appearance("Appearance"),
    Recordings("Recordings"),
    About("About GRID")
}

@Composable
fun SettingsScreen(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onOpenEpgResolver: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageOptions by viewModel.storageOptions.collectAsStateWithLifecycle()
    val currentStorageLabel by viewModel.currentStorageLabel.collectAsStateWithLifecycle()
    val progress by viewModel.m3uProgress.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val xtreamAccounts by viewModel.xtreamAccounts.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var refreshHours by remember { mutableStateOf("24") }
    var playlistType by remember { mutableStateOf(PlaylistType.M3U) }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    val categories = SettingsCategory.entries

    Row(modifier = Modifier.fillMaxSize().background(EpgColors.Background)) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF111118))
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "Settings",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            categories.forEachIndexed { index, category ->
                val selected = index == selectedCategory
                Surface(
                    onClick = { selectedCategory = index },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) Color(0xFF1C1C2E) else Color.Transparent,
                        focusedContainerColor = Color(0xFF1C1C2E)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(EpgColors.Accent)
                            )
                        }
                        Text(
                            text = category.label,
                            color = if (selected) EpgColors.TextPrimary else EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = if (selected) 10.dp else 0.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onBack) { Text("← Back") }

            when (categories[selectedCategory]) {
                SettingsCategory.Playlists -> {
                    Text("Playlists", color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 20.sp)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { playlistType = PlaylistType.M3U }) {
                            Text(if (playlistType == PlaylistType.M3U) "M3U ✓" else "M3U")
                        }
                        Button(onClick = { playlistType = PlaylistType.XTREAM }) {
                            Text(if (playlistType == PlaylistType.XTREAM) "Xtream ✓" else "Xtream")
                        }
                    }
                    if (playlistType == PlaylistType.M3U) {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("M3U URL") }, modifier = Modifier.fillMaxWidth())
                    } else {
                        OutlinedTextField(value = xtreamServer, onValueChange = { xtreamServer = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = xtreamUser, onValueChange = { xtreamUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = xtreamPass, onValueChange = { xtreamPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(value = epgUrl, onValueChange = { epgUrl = it }, label = { Text("XMLTV URL (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = refreshHours, onValueChange = { refreshHours = it }, label = { Text("Refresh interval (hours)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (playlistType == PlaylistType.XTREAM) {
                                viewModel.addXtreamPlaylist(name, xtreamServer, xtreamUser, xtreamPass, epgUrl.ifBlank { null }, refreshHours.toIntOrNull() ?: 24)
                            } else {
                                viewModel.addPlaylistFromUrl(name, url, epgUrl.ifBlank { null }, refreshHours.toIntOrNull() ?: 24)
                            }
                        }) { Text("Add Playlist") }
                        Button(onClick = onPickLocalFile) { Text("Add Local File") }
                        Button(onClick = onPickTiviMateZip) { Text("Import TiviMate") }
                    }
                    Text("Status: $progress", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 13.sp)
                    Text("Installed playlists", color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 15.sp)
                    playlists.forEach { p ->
                        Text("${p.name} [${p.type}] — refresh every ${p.refreshIntervalHours}h", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 13.sp)
                    }
                    xtreamAccounts.forEach { account ->
                        val exp = account.expiryDateEpochSec?.let {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it * 1000L))
                        } ?: "N/A"
                        Text("${account.playlistName}: ${account.status} — expires $exp", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 12.sp)
                    }
                }
                SettingsCategory.Epg -> {
                    Text("EPG / Guide", color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 20.sp)
                    Button(onClick = { viewModel.refreshEpg() }) { Text("Refresh EPG Now") }
                    Button(onClick = onOpenEpgResolver) { Text("EPG Auto-Resolve Channels") }
                    Text("Row height", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.updateRowHeight(EpgRowHeight.COMPACT) }) { Text("Compact") }
                        Button(onClick = { viewModel.updateRowHeight(EpgRowHeight.NORMAL) }) { Text("Normal") }
                        Button(onClick = { viewModel.updateRowHeight(EpgRowHeight.LARGE) }) { Text("Large") }
                    }
                }
                SettingsCategory.Playback -> {
                    Text("Playback", color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 20.sp)
                    Button(onClick = { viewModel.updateMiniPlayerAudio(!settings.miniPlayerAudioEnabled) }) {
                        Text(if (settings.miniPlayerAudioEnabled) "Mini player audio: ON" else "Mini player audio: OFF")
                    }
                    Text("Stream retries: ${settings.streamRetries}", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.updateRetries(2) }) { Text("2") }
                        Button(onClick = { viewModel.updateRetries(3) }) { Text("3") }
                        Button(onClick = { viewModel.updateRetries(5) }) { Text("5") }
                    }
                    OutlinedTextField(
                        value = settings.preferredAudioLanguage,
                        onValueChange = { viewModel.updateAudioLanguage(it) },
                        label = { Text("Preferred audio language") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Default sleep timer: ${settings.sleepTimerMinutes} min",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90).forEach { min ->
                            Button(onClick = { viewModel.updateSleepTimerMinutes(min) }) {
                                Text(if (settings.sleepTimerMinutes == min) "[$min]" else "$min")
                            }
                        }
                    }
                }
                SettingsCategory.Appearance -> {
                    Text("Appearance", color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 20.sp)
                    Text("GRID uses a fixed dark theme optimized for TV viewing.", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
                }
                SettingsCategory.Recordings -> {
                    Text("Recordings", color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 20.sp)
                    Text(
                        "Storage Location",
                        color = EpgColors.TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        currentStorageLabel ?: "Not configured",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(storageOptions) { option ->
                            Button(onClick = { viewModel.setRecordingStorage(option.id) }) {
                                Text(option.displayLine(), fontFamily = DmSansFamily, fontSize = 13.sp)
                            }
                        }
                    }
                }
                SettingsCategory.About -> {
                    Text("About GRID", color = EpgColors.TextPrimary, fontFamily = DmSansFamily, fontSize = 20.sp)
                    Text("GRID v2.1.0", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
                    Text("Live TV Guide for Android TV", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(onClick = { viewModel.exportBackup(context.cacheDir) }) {
                        Text("Export .grid backup")
                    }
                    importSummary?.let {
                        Text(it, color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
