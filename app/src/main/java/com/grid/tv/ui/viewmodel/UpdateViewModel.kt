package com.grid.tv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.feature.update.AppUpdateInfo
import com.grid.tv.feature.update.UpdateCheckResult
import com.grid.tv.feature.update.UpdateChecker
import com.grid.tv.feature.update.UpdatePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdatePromptState {
    data object Hidden : UpdatePromptState
    data class Available(val info: AppUpdateInfo) : UpdatePromptState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val preferences: UpdatePreferences
) : ViewModel() {

    private val _promptState = MutableStateFlow<UpdatePromptState>(UpdatePromptState.Hidden)
    val promptState: StateFlow<UpdatePromptState> = _promptState.asStateFlow()

    private var checkedThisSession = false

    fun checkOnLaunchIfNeeded() {
        if (checkedThisSession) {
            Log.d(UpdateChecker.TAG, "checkOnLaunch: already ran this session — skip")
            return
        }
        checkedThisSession = true
        viewModelScope.launch {
            Log.d(UpdateChecker.TAG, "checkOnLaunch: invoking UpdateChecker")
            when (val result = updateChecker.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    Log.d(UpdateChecker.TAG, "checkOnLaunch: showing update dialog for v${result.info.latestVersion}")
                    _promptState.value = UpdatePromptState.Available(result.info)
                }
                is UpdateCheckResult.UpToDate ->
                    Log.d(UpdateChecker.TAG, "checkOnLaunch: app is up to date")
                is UpdateCheckResult.NoReleasePublished ->
                    Log.d(UpdateChecker.TAG, "checkOnLaunch: no GitHub release published yet")
                is UpdateCheckResult.Skipped ->
                    Log.d(UpdateChecker.TAG, "checkOnLaunch: skipped — ${result.reason}")
                is UpdateCheckResult.Failed ->
                    Log.w(UpdateChecker.TAG, "checkOnLaunch: check failed — ${result.reason}")
            }
        }
    }

    fun dismissPrompt(version: String) {
        Log.d(UpdateChecker.TAG, "dismissPrompt: user chose Later for v$version")
        preferences.dismissVersion(version)
        _promptState.value = UpdatePromptState.Hidden
    }

    fun clearPrompt() {
        _promptState.value = UpdatePromptState.Hidden
    }
}
