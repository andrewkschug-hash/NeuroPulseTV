package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class VodHubViewModel @Inject constructor(
    private val repository: IptvRepository
) : ViewModel() {

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            runCatching { repository.refreshVodSeriesCatalog() }
        }
    }
}
