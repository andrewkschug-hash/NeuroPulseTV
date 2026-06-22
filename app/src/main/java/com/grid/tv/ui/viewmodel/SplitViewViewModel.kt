package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.domain.model.Channel
import com.grid.tv.data.repository.IptvRepositoryImpl.Companion.CHANNEL_PAGE_SIZE
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.player.MultiPanePlaybackPool
import com.grid.tv.player.PlaybackOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SplitViewViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val multiPanePlaybackPool: MultiPanePlaybackPool,
    private val playbackOrchestrator: PlaybackOrchestrator
) : ViewModel() {

    companion object {
        const val MAX_PANES = 4
        private const val PANE_OWNER = "split_view"
    }

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private var channelOffset = 0
    private var hasMoreChannels = true
    private var loadingChannels = false

    val favoriteChannels: StateFlow<List<Channel>> = repository.channels(null, "", favoritesOnly = true, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = repository.recentChannels(limit = 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadMoreChannels()
    }

    fun loadMoreChannels() {
        if (loadingChannels || !hasMoreChannels) return
        viewModelScope.launch {
            loadingChannels = true
            try {
                val page = repository.channelsPage(
                    groups = emptySet(),
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

    private val _paneChannelIds = MutableStateFlow<List<Long>>(emptyList())
    val paneChannelIds: StateFlow<List<Long>> = _paneChannelIds.asStateFlow()

    private val _resolvedChannels = MutableStateFlow<Map<Long, Channel>>(emptyMap())
    val paneChannels: StateFlow<List<Channel?>> = combine(_paneChannelIds, _resolvedChannels) { ids, resolved ->
        ids.map { resolved[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _audioPaneIndex = MutableStateFlow(0)
    val audioPaneIndex: StateFlow<Int> = _audioPaneIndex.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    fun loadPrimary(channelId: Long) {
        viewModelScope.launch {
            _loadFailed.value = false
            val channel = repository.channelById(channelId)
            if (channel == null) {
                _loadFailed.value = true
                return@launch
            }
            _resolvedChannels.value = _resolvedChannels.value + (channelId to channel)
            _paneChannelIds.value = listOf(channelId)
            _audioPaneIndex.value = 0
        }
    }

    fun addPane(channelId: Long, maxPanes: Int = MAX_PANES) {
        if (_paneChannelIds.value.size >= maxPanes) return
        if (channelId in _paneChannelIds.value) return
        viewModelScope.launch {
            val channel = repository.channelById(channelId) ?: return@launch
            _resolvedChannels.value = _resolvedChannels.value + (channelId to channel)
            _paneChannelIds.value = _paneChannelIds.value + channelId
        }
    }

    fun removePane(index: Int) {
        val ids = _paneChannelIds.value
        if (ids.size <= 1 || index !in ids.indices) return
        val removedAudioIndex = _audioPaneIndex.value
        _paneChannelIds.value = ids.filterIndexed { i, _ -> i != index }
        _audioPaneIndex.value = when {
            removedAudioIndex == index -> 0
            removedAudioIndex > index -> removedAudioIndex - 1
            else -> removedAudioIndex
        }.coerceIn(0, _paneChannelIds.value.lastIndex)
    }

    fun makePrimary(index: Int) {
        val ids = _paneChannelIds.value.toMutableList()
        if (index !in ids.indices || index == 0) return
        val id = ids.removeAt(index)
        ids.add(0, id)
        val audioIndex = _audioPaneIndex.value
        _paneChannelIds.value = ids
        _audioPaneIndex.value = when (audioIndex) {
            index -> 0
            in 1 until index -> audioIndex - 1
            else -> audioIndex
        }.coerceIn(0, ids.lastIndex)
    }

    fun setAudioPane(index: Int) {
        if (index in _paneChannelIds.value.indices) {
            _audioPaneIndex.value = index
        }
    }

    @UnstableApi
    fun playerForPane(context: Context, paneIndex: Int): ExoPlayer =
        multiPanePlaybackPool.getOrCreatePlayer(context, paneIndex, PANE_OWNER)

    fun syncPanePlayback(
        context: Context,
        channels: List<Channel?>,
        audioPaneIndex: Int,
        decodeOnlyAudio: Boolean
    ) {
        multiPanePlaybackPool.syncFromStreamUrls(
            context = context,
            streamUrlsByPane = channels.map { it?.streamUrl?.takeIf { url -> url.isNotBlank() } },
            audioPaneIndex = audioPaneIndex,
            decodeOnlyAudioPane = decodeOnlyAudio,
            owner = PANE_OWNER
        )
    }

    fun requestSplitSession(context: Context): PlaybackOrchestrator.SessionRequestResult =
        playbackOrchestrator.requestSession(
            PlaybackOrchestrator.PlaybackSession.SPLIT_VIEW,
            owner = "split_view_screen",
            context = context
        )

    fun releaseSplitSession(context: Context) {
        playbackOrchestrator.releaseSession(
            PlaybackOrchestrator.PlaybackSession.SPLIT_VIEW,
            context
        )
    }
}
