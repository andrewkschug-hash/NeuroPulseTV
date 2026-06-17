package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.repository.IptvRepositoryImpl.Companion.CHANNEL_PAGE_SIZE
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.player.multiview.MultiViewLayout
import com.grid.tv.player.multiview.MultiViewManager
import com.grid.tv.player.multiview.MultiViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MultiViewViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val multiViewManager: MultiViewManager
) : ViewModel() {

    private val _state = MutableStateFlow(MultiViewState.initial(MultiViewLayout.FOUR, null))
    val state: StateFlow<MultiViewState> = _state.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private var channelOffset = 0
    private var hasMoreChannels = true
    private var loadingChannels = false

    init {
        loadMoreChannels()
    }

    fun loadMoreChannels() {
        if (loadingChannels || !hasMoreChannels) return
        viewModelScope.launch {
            loadingChannels = true
            try {
                val page = repository.channelsPage(
                    group = null,
                    search = "",
                    favoritesOnly = false,
                    favoriteGroupId = null,
                    limit = CHANNEL_PAGE_SIZE,
                    offset = channelOffset
                )
                channelOffset += page.size
                hasMoreChannels = page.size >= CHANNEL_PAGE_SIZE
                _channels.value = _channels.value + page
            } finally {
                loadingChannels = false
            }
        }
    }

    fun initialize(seedChannelId: Long) {
        viewModelScope.launch {
            val seed = if (seedChannelId > 0L) repository.channelById(seedChannelId) else null
            _state.value = MultiViewState.initial(MultiViewLayout.FOUR, seed)
        }
    }

    fun setLayout(layout: MultiViewLayout) {
        val seed = _state.value.panels.firstOrNull()?.channel
        _state.value = MultiViewState.initial(layout, seed).copy(
            activeAudioPanelIndex = _state.value.activeAudioPanelIndex.coerceIn(0, layout.panelCount - 1),
            focusedPanelIndex = _state.value.focusedPanelIndex.coerceIn(0, layout.panelCount - 1)
        )
    }

    fun moveFocus(delta: Int) {
        val layout = _state.value.layout
        val next = (_state.value.focusedPanelIndex + delta).coerceIn(0, layout.panelCount - 1)
        _state.value = _state.value.copy(focusedPanelIndex = next)
    }

    fun selectActivePanel() {
        _state.value = _state.value.copy(activeAudioPanelIndex = _state.value.focusedPanelIndex)
        multiViewManager.setActiveAudioPanel(_state.value.focusedPanelIndex)
    }

    fun enterFullscreen() {
        _state.value = _state.value.copy(fullscreenPanelIndex = _state.value.focusedPanelIndex)
    }

    fun exitFullscreen() {
        _state.value = _state.value.copy(fullscreenPanelIndex = null)
    }

    fun startReplacePanel() {
        _state.value = _state.value.copy(replacingPanelIndex = _state.value.focusedPanelIndex)
    }

    fun cancelReplacePanel() {
        _state.value = _state.value.copy(replacingPanelIndex = null)
    }

    fun replacePanel(context: android.content.Context, channel: Channel) {
        val panelIndex = _state.value.replacingPanelIndex ?: _state.value.focusedPanelIndex
        multiViewManager.replacePanel(context, panelIndex, channel)
        val panels = _state.value.panels.toMutableList()
        panels[panelIndex] = panels[panelIndex].copy(channel = channel)
        _state.value = _state.value.copy(panels = panels, replacingPanelIndex = null)
    }

    fun tuneAllPanels(context: android.content.Context) {
        _state.value.panels.forEachIndexed { index, panel ->
            panel.channel?.let { multiViewManager.tunePanel(context, index, it) }
        }
        multiViewManager.setActiveAudioPanel(_state.value.activeAudioPanelIndex)
    }

    fun playerForPanel(context: android.content.Context, panelIndex: Int) =
        multiViewManager.getOrCreatePlayer(context, panelIndex)

    override fun onCleared() {
        multiViewManager.releaseAll()
        super.onCleared()
    }
}
