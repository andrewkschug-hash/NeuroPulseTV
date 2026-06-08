package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.ContentResolver
import android.net.Uri
import com.neuropulse.tv.domain.model.AppSettings
import com.neuropulse.tv.domain.model.EpgRowHeight
import com.neuropulse.tv.domain.model.Playlist
import com.neuropulse.tv.domain.model.XtreamAccountInfo
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.dashboard.DashboardController
import com.neuropulse.tv.worker.EpgScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val scheduler: EpgScheduler,
    private val dashboardController: DashboardController
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordings = repository.recordings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val healthBest = repository.healthBest(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val healthWorst = repository.healthWorst(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val xtreamAccounts: StateFlow<List<XtreamAccountInfo>> = repository.xtreamAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    private val _m3uProgress = MutableStateFlow("Idle")
    val m3uProgress = _m3uProgress.asStateFlow()

    private val _importSummary = MutableStateFlow<String?>(null)
    val importSummary = _importSummary.asStateFlow()

    private val _dashboardUrl = MutableStateFlow<String?>(null)
    val dashboardUrl = _dashboardUrl.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = repository.loadSettings()
            scheduler.scheduleAtLaunch()
        }
    }

    fun addPlaylistFromUrl(name: String, url: String, epg: String?, refreshHours: Int) {
        viewModelScope.launch {
            _m3uProgress.value = "Parsing..."
            repository.addPlaylistFromUrl(name, url, epg, refreshHours)
            _m3uProgress.value = "Done"
        }
    }

    fun addPlaylistFromLocal(name: String, content: String, epg: String?, refreshHours: Int) {
        viewModelScope.launch {
            _m3uProgress.value = "Parsing..."
            repository.addPlaylistFromLocal(name, content, epg, refreshHours)
            _m3uProgress.value = "Done"
        }
    }

    fun addXtreamPlaylist(name: String, serverUrl: String, username: String, password: String, epg: String?, refreshHours: Int) {
        viewModelScope.launch {
            _m3uProgress.value = "Connecting Xtream..."
            repository.addXtreamPlaylist(name, serverUrl, username, password, epg, refreshHours)
            _m3uProgress.value = "Done"
        }
    }

    fun refreshEpg() {
        viewModelScope.launch { repository.refreshEpgNow() }
    }

    fun updateRowHeight(value: EpgRowHeight) {
        viewModelScope.launch {
            val updated = _settings.value.copy(epgRowHeight = value)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }

    fun updateRetries(value: Int) {
        viewModelScope.launch {
            val updated = _settings.value.copy(streamRetries = value)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }

    fun updateAudioLanguage(value: String) {
        viewModelScope.launch {
            val updated = _settings.value.copy(preferredAudioLanguage = value)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }

    fun updateMiniPlayerAudio(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _settings.value.copy(miniPlayerAudioEnabled = enabled)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }

    fun importTiviMate(contentResolver: ContentResolver, uri: Uri, cacheDir: File) {
        viewModelScope.launch {
            _importSummary.value = repository.importTiviMate(contentResolver, uri, cacheDir)
        }
    }

    fun startDashboard() {
        _dashboardUrl.value = dashboardController.startOrGetUrl()
    }
}
