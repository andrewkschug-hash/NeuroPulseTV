package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.update.AppUpdateInfo
import com.grid.tv.data.update.GitHubReleaseChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val releaseChecker: GitHubReleaseChecker
) : ViewModel() {

    private val _pendingUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val pendingUpdate: StateFlow<AppUpdateInfo?> = _pendingUpdate.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            runCatching {
                releaseChecker.checkForUpdate()
            }.getOrNull()?.let { update ->
                _pendingUpdate.value = update
            }
        }
    }

    fun dismissUpdate() {
        _pendingUpdate.value = null
    }
}
