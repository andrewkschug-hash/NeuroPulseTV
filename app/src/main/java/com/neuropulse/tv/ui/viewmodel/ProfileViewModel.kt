package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.UserProfile
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {
    val profiles: StateFlow<List<UserProfile>> = repository.profiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { repository.purgeDefaultProfiles() }
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch { repository.setActiveProfile(profileId) }
    }

    fun createProfile(name: String, color: String, pin: String?, parental: Boolean) {
        viewModelScope.launch { repository.createProfile(name, color, pin, parental) }
    }

    suspend fun verifyPin(profileId: Long, pin: String): Boolean =
        repository.verifyProfilePin(profileId, pin)
}
