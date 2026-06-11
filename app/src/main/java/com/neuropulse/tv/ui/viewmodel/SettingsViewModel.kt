package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.ContentResolver
import android.net.Uri
import com.neuropulse.tv.domain.model.AppSettings
import com.neuropulse.tv.domain.model.EpgRowHeight
import com.neuropulse.tv.domain.model.ConnectionFormFields
import com.neuropulse.tv.domain.model.Playlist
import com.neuropulse.tv.domain.model.XtreamAccountInfo
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.dashboard.DashboardController
import com.neuropulse.tv.feature.recording.RecordingStorageManager
import com.neuropulse.tv.feature.recording.StorageOption
import com.neuropulse.tv.domain.model.ScannerSettings
import com.neuropulse.tv.feature.scanner.ChannelScanner
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

sealed interface ConnectionDialogState {
    data object Success : ConnectionDialogState
    data class Failure(val errorMessage: String) : ConnectionDialogState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val scheduler: EpgScheduler,
    private val dashboardController: DashboardController,
    private val recordingStorageManager: RecordingStorageManager,
    private val channelScanner: ChannelScanner
) : ViewModel() {

    val scannerRuntime = channelScanner.runtime

    companion object {
        const val APP_VERSION = "2.1.0"
    }

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

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionDialog = MutableStateFlow<ConnectionDialogState?>(null)
    val connectionDialog: StateFlow<ConnectionDialogState?> = _connectionDialog.asStateFlow()

    private val _importSummary = MutableStateFlow<String?>(null)
    val importSummary = _importSummary.asStateFlow()

    private val _dashboardUrl = MutableStateFlow<String?>(null)
    val dashboardUrl = _dashboardUrl.asStateFlow()

    private val _storageOptions = MutableStateFlow<List<StorageOption>>(emptyList())
    val storageOptions = _storageOptions.asStateFlow()

    private val _currentStorageLabel = MutableStateFlow<String?>(null)
    val currentStorageLabel = _currentStorageLabel.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = repository.loadSettings()
            scheduler.scheduleAtLaunch()
            refreshStorageSettings()
            syncScannerSettings()
        }
    }

    suspend fun shouldShowWhatsNew(): Boolean = repository.shouldShowWhatsNew(APP_VERSION)

    fun markWhatsNewSeen() {
        viewModelScope.launch { repository.markVersionSeen(APP_VERSION) }
    }

    fun exportBackup(cacheDir: File) {
        viewModelScope.launch {
            val file = File(cacheDir, "grid-backup-${System.currentTimeMillis()}.grid")
            _importSummary.value = repository.exportBackup(file)
        }
    }

    fun updateSleepTimerMinutes(minutes: Int) {
        viewModelScope.launch {
            val updated = _settings.value.copy(sleepTimerMinutes = minutes)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }

    fun updateHideAdultContent(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _settings.value.copy(hideAdultContent = enabled)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }

    fun updateSleepTimerAutoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _settings.value.copy(sleepTimerAutoEnabled = enabled)
            _settings.value = updated
            repository.saveSettings(updated)
        }
    }

    fun refreshStorageSettings() {
        _storageOptions.value = recordingStorageManager.enumerateOptions()
        viewModelScope.launch {
            _currentStorageLabel.value = recordingStorageManager.currentStorageLabel()
        }
    }

    fun setRecordingStorage(optionId: String) {
        viewModelScope.launch {
            recordingStorageManager.saveLocationById(optionId)
            _currentStorageLabel.value = recordingStorageManager.currentStorageLabel()
        }
    }

    fun addPlaylistFromUrl(name: String, url: String, epg: String?, refreshHours: Int) {
        viewModelScope.launch {
            runConnectionJob(progressLabel = "Parsing...") {
                repository.addPlaylistFromUrl(name, url, epg, refreshHours)
            }
        }
    }

    fun addPlaylistFromLocal(name: String, content: String, epg: String?, refreshHours: Int) {
        viewModelScope.launch {
            runConnectionJob(progressLabel = "Parsing...") {
                repository.addPlaylistFromLocal(name, content, epg, refreshHours)
            }
        }
    }

    fun addXtreamPlaylist(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        epg: String?,
        refreshHours: Int
    ) {
        viewModelScope.launch {
            runConnectionJob(progressLabel = "Connecting Xtream...") {
                repository.addXtreamPlaylist(name, serverUrl, username, password, epg, refreshHours)
            }
        }
    }

    fun dismissConnectionDialog() {
        _connectionDialog.value = null
    }

    suspend fun connectionFormFor(playlist: Playlist): ConnectionFormFields =
        repository.connectionFormForPlaylist(playlist)

    private suspend fun runConnectionJob(progressLabel: String, block: suspend () -> Unit) {
        _isConnecting.value = true
        _m3uProgress.value = progressLabel
        try {
            block()
            _connectionDialog.value = ConnectionDialogState.Success
        } catch (e: Exception) {
            _connectionDialog.value = ConnectionDialogState.Failure(
                e.message ?: "Could not connect to the provided URL."
            )
        } finally {
            _isConnecting.value = false
            _m3uProgress.value = "Idle"
        }
    }

    fun refreshEpg() {
        viewModelScope.launch { repository.refreshEpgNow() }
    }

    private fun syncScannerSettings() {
        val current = _settings.value
        channelScanner.updateSettings(
            ScannerSettings(
                autoScanEnabled = current.autoScanEnabled,
                scanIntervalMinutes = current.scanIntervalMinutes,
                concurrentChecks = current.concurrentChecks,
                scanOnMetered = current.scanOnMetered
            )
        )
    }

    private fun persistScannerSettings(updated: com.neuropulse.tv.domain.model.AppSettings) {
        _settings.value = updated
        viewModelScope.launch {
            repository.saveSettings(updated)
            syncScannerSettings()
        }
    }

    fun updateAutoScanEnabled(enabled: Boolean) {
        persistScannerSettings(_settings.value.copy(autoScanEnabled = enabled))
    }

    fun updateScanIntervalMinutes(minutes: Int) {
        persistScannerSettings(_settings.value.copy(scanIntervalMinutes = minutes))
    }

    fun updateConcurrentChecks(count: Int) {
        persistScannerSettings(_settings.value.copy(concurrentChecks = count))
    }

    fun updateScanOnMetered(enabled: Boolean) {
        persistScannerSettings(_settings.value.copy(scanOnMetered = enabled))
    }

    fun scanChannelsNow() {
        channelScanner.scanNow()
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

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun importTiviMate(contentResolver: ContentResolver, uri: Uri, cacheDir: File) {
        viewModelScope.launch {
            _importSummary.value = repository.importTiviMate(contentResolver, uri, cacheDir)
        }
    }

    fun startDashboard() {
        _dashboardUrl.value = dashboardController.startOrGetUrl()
    }

    fun resetAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetApp()
            _settings.value = AppSettings()
            syncScannerSettings()
            onComplete()
        }
    }
}
