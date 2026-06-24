package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.update.ApkUpdateDownloader
import com.grid.tv.data.update.AppUpdateInfo
import com.grid.tv.data.update.GitHubReleaseChecker
import com.grid.tv.data.update.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ManualUpdateUiState {
    data object Idle : ManualUpdateUiState()
    data object Checking : ManualUpdateUiState()
    data object UpToDate : ManualUpdateUiState()
    data class UpdateAvailable(val info: AppUpdateInfo) : ManualUpdateUiState()
    data class Error(val message: String) : ManualUpdateUiState()
    data class Downloading(val percent: Int) : ManualUpdateUiState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val releaseChecker: GitHubReleaseChecker,
    private val apkDownloader: ApkUpdateDownloader
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManualUpdateUiState>(ManualUpdateUiState.Idle)
    val uiState: StateFlow<ManualUpdateUiState> = _uiState.asStateFlow()

    private var pendingUpdate: AppUpdateInfo? = null

    fun checkForUpdate() {
        if (_uiState.value is ManualUpdateUiState.Checking ||
            _uiState.value is ManualUpdateUiState.Downloading
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.value = ManualUpdateUiState.Checking
            when (val result = releaseChecker.checkForUpdate()) {
                UpdateCheckResult.UpToDate -> {
                    pendingUpdate = null
                    _uiState.value = ManualUpdateUiState.UpToDate
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    pendingUpdate = result.info
                    _uiState.value = ManualUpdateUiState.UpdateAvailable(result.info)
                }
                is UpdateCheckResult.NoReleasePublished ->
                    _uiState.value = ManualUpdateUiState.Error("No published release found on GitHub")
                is UpdateCheckResult.Failed ->
                    _uiState.value = ManualUpdateUiState.Error(result.reason)
            }
        }
    }

    fun downloadAndInstall() {
        val update = pendingUpdate ?: return
        if (_uiState.value is ManualUpdateUiState.Downloading) return
        viewModelScope.launch {
            _uiState.value = ManualUpdateUiState.Downloading(0)
            apkDownloader.downloadApk(update.downloadUrl) { percent ->
                _uiState.value = ManualUpdateUiState.Downloading(percent)
            }.onSuccess { apkFile ->
                apkDownloader.launchInstaller(apkFile)
                _uiState.value = ManualUpdateUiState.UpdateAvailable(update)
            }.onFailure { error ->
                _uiState.value = ManualUpdateUiState.Error(
                    error.message ?: "Download failed"
                )
            }
        }
    }

    fun resetStatus() {
        if (_uiState.value !is ManualUpdateUiState.Downloading) {
            _uiState.value = ManualUpdateUiState.Idle
        }
    }
}
