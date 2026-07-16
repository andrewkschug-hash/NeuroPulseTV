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
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _profilesReady = MutableStateFlow(false)
    val profilesReady: StateFlow<Boolean> = _profilesReady.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.prepareProfilePicker()
                _activeProfile.value = repository.activeProfile()
            } catch (e: Exception) {
                android.util.Log.e("ProfileVM", "Profile init failed — showing picker", e)
            } finally {
                _profilesReady.value = true
            }
        }
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch { activateProfile(profileId) }
    }

    suspend fun activateProfile(profileId: Long) {
        repository.setActiveProfile(profileId)
        _activeProfile.value = repository.activeProfile()
    }

    fun refreshActiveProfile() {
        viewModelScope.launch {
            _activeProfile.value = repository.activeProfile()
        }
    }

    fun isGuestSession(): Boolean = repository.isGuestSession()

    fun enterGuestSession() {
        viewModelScope.launch { activateGuestSession() }
    }

    suspend fun activateGuestSession() {
        repository.enterGuestSession()
        _activeProfile.value = repository.activeProfile()
    }

    suspend fun createProfileAndGetId(
        name: String,
        color: String,
        pin: String?,
        parental: Boolean
    ): Long = repository.createProfile(name, color, pin, parental)

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
