package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.ContentResolver
import android.net.Uri
import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.AspectRatioSetting
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.domain.model.ClockDisplay
import com.grid.tv.domain.model.DpadSensitivity
import com.grid.tv.domain.model.EpgRowHeight
import com.grid.tv.domain.model.MaxContentRating
import com.grid.tv.domain.model.StreamQuality
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.domain.model.ConnectionFormFields
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.XtreamAccountInfo
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.PictureInPictureController
import com.grid.tv.ui.theme.ThemeManager
import com.grid.tv.feature.dashboard.DashboardController
import com.grid.tv.feature.recording.RecordingStorageManager
import com.grid.tv.feature.recording.StorageOption
import com.grid.tv.domain.model.ScannerSettings
import com.grid.tv.feature.scanner.ChannelScanner
import com.grid.tv.worker.EpgScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.grid.tv.util.CONNECTION_FAILED_ERROR
import com.grid.tv.util.CONNECTION_TIMEOUT_ERROR
import com.grid.tv.util.connectionTimeoutMs
import java.io.File
import javax.inject.Inject

sealed interface ConnectionDialogState {
    data object Success : ConnectionDialogState
    data class Failure(val errorMessage: String) : ConnectionDialogState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val dashboardController: DashboardController,
    private val recordingStorageManager: RecordingStorageManager,
    private val channelScanner: ChannelScanner,
    private val epgScheduler: EpgScheduler,
    private val themeManager: ThemeManager,
    private val pipController: PictureInPictureController,
    private val livePlayerManager: LivePlayerManager
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

    val channelGroups: StateFlow<List<String>> = repository.groups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupChannelCounts: StateFlow<Map<String, Int>> = repository.groupChannelCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    private val _settingsReady = MutableStateFlow(false)
    val settingsReady: StateFlow<Boolean> = _settingsReady.asStateFlow()

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

    private val _cacheMessage = MutableStateFlow<String?>(null)
    val cacheMessage = _cacheMessage.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _settings.value = repository.loadSettings()
            _settingsReady.value = true
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

    fun saveConnection(
        editingPlaylistId: Long?,
        name: String,
        url: String,
        playlistType: com.grid.tv.domain.model.PlaylistType,
        xtreamServer: String,
        xtreamUser: String,
        xtreamPass: String,
        epgUrl: String?,
        refreshHours: Int
    ) {
        viewModelScope.launch {
            val isUpdate = editingPlaylistId != null
            val label = if (isUpdate) "Updating..." else "Connecting..."
            val isXtream = playlistType == com.grid.tv.domain.model.PlaylistType.XTREAM
            val connected = runConnectionJob(progressLabel = label) {
                if (isUpdate) {
                    when (playlistType) {
                        com.grid.tv.domain.model.PlaylistType.XTREAM ->
                            repository.updateXtreamPlaylist(
                                editingPlaylistId!!,
                                name,
                                xtreamServer,
                                xtreamUser,
                                xtreamPass,
                                epgUrl,
                                refreshHours
                            )
                        else ->
                            repository.updateM3uPlaylist(
                                editingPlaylistId!!,
                                name,
                                url,
                                epgUrl,
                                refreshHours
                            )
                    }
                } else {
                    when (playlistType) {
                        com.grid.tv.domain.model.PlaylistType.XTREAM ->
                            repository.addXtreamPlaylist(
                                name,
                                xtreamServer,
                                xtreamUser,
                                xtreamPass,
                                epgUrl,
                                refreshHours
                            )
                        else ->
                            repository.addPlaylistFromUrl(name, url, epgUrl, refreshHours)
                    }
                }
            }
            if (connected && isXtream) {
                viewModelScope.launch {
                    runCatching {
                        repository.refreshVodSeriesCatalog(
                            trigger = com.grid.tv.domain.model.VodRefreshTrigger.PLAYLIST_CONNECT,
                            force = true
                        )
                    }
                }
            }
        }
    }

    fun dismissConnectionDialog() {
        _connectionDialog.value = null
    }

    suspend fun connectionFormFor(playlist: Playlist): ConnectionFormFields =
        repository.connectionFormForPlaylist(playlist)

    private suspend fun runConnectionJob(progressLabel: String, block: suspend () -> Unit): Boolean {
        _isConnecting.value = true
        _m3uProgress.value = progressLabel
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(connectionTimeoutMs(_settings.value.connectionTimeoutSeconds)) { block() }
            }
            _connectionDialog.value = ConnectionDialogState.Success
            true
        } catch (_: TimeoutCancellationException) {
            _connectionDialog.value = ConnectionDialogState.Failure(CONNECTION_TIMEOUT_ERROR)
            false
        } catch (e: Exception) {
            _connectionDialog.value = ConnectionDialogState.Failure(
                e.message?.takeIf { it.isNotBlank() } ?: CONNECTION_FAILED_ERROR
            )
            false
        } finally {
            _isConnecting.value = false
            _m3uProgress.value = "Idle"
        }
    }

    fun refreshEpg() {
        epgScheduler.scheduleManualEpg()
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

    private fun persistScannerSettings(updated: com.grid.tv.domain.model.AppSettings) {
        _settings.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSettings(updated)
            withContext(Dispatchers.Main) { syncScannerSettings() }
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

    fun updateGuideChannelGroups(groups: Set<String>) {
        viewModelScope.launch {
            repository.saveGuideChannelFilter(groups, configured = true)
            val latest = repository.loadSettings()
            _settings.value = latest
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
        persistSettings(_settings.value.copy(miniPlayerAudioEnabled = enabled))
    }

    private fun persistSettings(updated: AppSettings) {
        _settings.value = updated
        viewModelScope.launch(Dispatchers.IO) { repository.saveSettings(updated) }
    }

    fun updateParentalPinLock(enabled: Boolean) {
        persistSettings(_settings.value.copy(parentalPinLockEnabled = enabled))
    }

    fun updateMaxContentRating(rating: MaxContentRating) {
        persistSettings(_settings.value.copy(maxContentRating = rating))
    }

    fun updateConnectionTimeout(seconds: Int) {
        persistSettings(_settings.value.copy(connectionTimeoutSeconds = seconds))
    }

    fun updateUseProxy(enabled: Boolean) {
        persistSettings(_settings.value.copy(useProxy = enabled))
    }

    fun updateProxyUrl(url: String) {
        persistSettings(_settings.value.copy(proxyUrl = url))
    }

    fun updateShowEpgProgramInfoOnSidebar(enabled: Boolean) {
        persistSettings(_settings.value.copy(showEpgProgramInfoOnSidebar = enabled))
    }

    fun updateStartChannelFromBeginningOnCatchup(enabled: Boolean) {
        persistSettings(_settings.value.copy(startChannelFromBeginningOnCatchup = enabled))
    }

    fun updateDefaultStreamQuality(quality: StreamQuality) {
        persistSettings(_settings.value.copy(defaultStreamQuality = quality))
    }

    fun updateBufferSize(size: BufferSize) {
        persistSettings(_settings.value.copy(bufferSize = size))
    }

    fun updateAutoReconnectOnDrop(enabled: Boolean) {
        persistSettings(_settings.value.copy(autoReconnectOnDrop = enabled))
        livePlayerManager.setAutoReconnectOnDrop(enabled)
    }

    fun updatePreferHardwareDecoding(enabled: Boolean) {
        persistSettings(_settings.value.copy(preferHardwareDecoding = enabled))
    }

    fun updateAspectRatio(ratio: AspectRatioSetting) {
        persistSettings(_settings.value.copy(aspectRatio = ratio))
    }

    fun updateSubtitlesEnabled(enabled: Boolean) {
        persistSettings(_settings.value.copy(subtitlesEnabled = enabled))
    }

    fun updateSubtitleLanguage(language: String) {
        persistSettings(_settings.value.copy(subtitleLanguage = language))
    }

    fun updateSubtitleFontSize(size: SubtitleFontSize) {
        persistSettings(_settings.value.copy(subtitleFontSize = size))
    }

    fun updateSubtitlePosition(position: SubtitlePosition) {
        persistSettings(_settings.value.copy(subtitlePosition = position))
    }

    fun updateSubtitleDelayMs(delayMs: Long) {
        persistSettings(_settings.value.copy(subtitleDelayMs = delayMs.coerceIn(-5_000L, 5_000L)))
    }

    fun updateDeinterlacingEnabled(enabled: Boolean) {
        persistSettings(_settings.value.copy(deinterlacingEnabled = enabled))
    }

    fun updateMiniPlayerEnabled(enabled: Boolean) {
        persistSettings(_settings.value.copy(miniPlayerEnabled = enabled))
    }

    fun updateSidebarAutoHideSeconds(seconds: Int) {
        persistSettings(_settings.value.copy(sidebarAutoHideSeconds = seconds))
    }

    fun updateShowChannelNumbers(enabled: Boolean) {
        persistSettings(_settings.value.copy(showChannelNumbers = enabled))
    }

    fun updateDpadSidebarSensitivity(sensitivity: DpadSensitivity) {
        persistSettings(_settings.value.copy(dpadSidebarSensitivity = sensitivity))
    }

    fun updateClockDisplay(display: ClockDisplay) {
        persistSettings(_settings.value.copy(clockDisplay = display))
    }

    fun updateTheme(themeId: AppThemeId) {
        viewModelScope.launch {
            themeManager.setTheme(themeId)
            persistSettings(_settings.value.copy(themeId = themeId))
        }
    }

    fun updatePictureInPictureEnabled(enabled: Boolean) {
        pipController.pictureInPictureEnabled = enabled
        persistSettings(_settings.value.copy(pictureInPictureEnabled = enabled))
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearAppCache()
            _cacheMessage.value = "Cache cleared"
        }
    }

    fun dismissCacheMessage() {
        _cacheMessage.value = null
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            repository.resetSettingsToDefaults()
            _settings.value = repository.loadSettings()
            syncScannerSettings()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun importTiviMate(contentResolver: ContentResolver, uri: Uri, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
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
