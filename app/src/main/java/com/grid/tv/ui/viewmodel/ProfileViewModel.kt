package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {
    val profiles: StateFlow<List<UserProfile>> = repository.profiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    init {
        viewModelScope.launch { repository.purgeDefaultProfiles() }
        refreshActiveProfile()
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch {
            repository.setActiveProfile(profileId)
            refreshActiveProfile()
        }
    }

    fun refreshActiveProfile() {
        viewModelScope.launch {
            _activeProfile.value = repository.activeProfile()
        }
    }

    fun createProfile(name: String, color: String, pin: String?, parental: Boolean) {
        viewModelScope.launch { repository.createProfile(name, color, pin, parental) }
    }

    fun updateProfileName(profileId: Long, name: String) {
        viewModelScope.launch {
            repository.updateProfileName(profileId, name)
            refreshActiveProfile()
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            refreshActiveProfile()
        }
    }

    fun updateAvatarColor(profileId: Long, colorHex: String) {
        viewModelScope.launch {
            repository.updateProfileAvatarColor(profileId, colorHex)
            refreshActiveProfile()
        }
    }

    suspend fun verifyPin(profileId: Long, pin: String): Boolean =
        repository.verifyProfilePin(profileId, pin)

    fun updateProfilePin(profileId: Long, pin: String?) {
        viewModelScope.launch {
            repository.updateProfilePin(profileId, pin)
            refreshActiveProfile()
        }
    }
}
