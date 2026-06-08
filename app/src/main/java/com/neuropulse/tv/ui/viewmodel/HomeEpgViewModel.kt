package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.ContinueWatchingItem
import com.neuropulse.tv.domain.model.FavoriteGroup
import com.neuropulse.tv.domain.model.Playlist
import com.neuropulse.tv.domain.model.Program
import com.neuropulse.tv.domain.model.Recommendation
import com.neuropulse.tv.domain.model.SeriesShow
import com.neuropulse.tv.domain.model.UserProfile
import com.neuropulse.tv.domain.model.VodItem
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.parental.ProfileAccessGuard
import com.neuropulse.tv.player.LivePlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeEpgViewModel @Inject constructor(
    private val repository: IptvRepository,
    val livePlayerManager: LivePlayerManager
) : ViewModel() {

    private val _miniPlayerAudioEnabled = MutableStateFlow(false)
    val miniPlayerAudioEnabled = _miniPlayerAudioEnabled.asStateFlow()

    private val _favoriteGroupFilter = MutableStateFlow<Long?>(null)
    val favoriteGroupFilter: StateFlow<Long?> = _favoriteGroupFilter.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteGroups: StateFlow<List<FavoriteGroup>> = repository.favoriteGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channels: StateFlow<List<Channel>> = _favoriteGroupFilter
        .flatMapLatest { groupId ->
            repository.channels(group = null, search = "", favoritesOnly = groupId != null, favoriteGroupId = groupId)
        }
        .combine(_activeProfile) { channelList, profile ->
            profile?.let { p ->
                channelList.filter { !ProfileAccessGuard.shouldHideChannel(p, it) }
            } ?: channelList
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatchingItems: StateFlow<List<ContinueWatchingItem>> = repository.continueWatchingItems(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<Channel>> = continueWatchingItems
        .combine(_activeProfile) { items, profile ->
            items.map { it.channel }.filter { ch ->
                profile?.let { !ProfileAccessGuard.shouldHideChannel(it, ch) } ?: true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recommendations: StateFlow<List<Recommendation>> = repository.recommendedChannels(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sportsNow: StateFlow<List<Program>> = repository.liveSportsNow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val moviesSoon: StateFlow<List<Program>> = repository.moviesStartingSoon(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topChannels: StateFlow<List<Channel>> = repository.topChannels(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAdded: StateFlow<List<Channel>> = repository.recentlyAdded(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vod: StateFlow<List<VodItem>> = repository.vodStreams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: StateFlow<List<SeriesShow>> = repository.seriesShows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val now = MutableStateFlow(System.currentTimeMillis())

    private val _epgPrograms = MutableStateFlow<List<Program>>(emptyList())
    val epgPrograms = _epgPrograms.asStateFlow()

    private val _epgLoading = MutableStateFlow(false)
    val epgLoading = _epgLoading.asStateFlow()

    private val _windowStart = MutableStateFlow(System.currentTimeMillis() - 90 * 60 * 1000)
    val windowStart: StateFlow<Long> = _windowStart.asStateFlow()

    val windowDurationMs: Long = 4 * 60 * 60 * 1000

    val programs: StateFlow<List<Program>> = channels
        .flatMapLatest { ch ->
            repository.programs(ch.mapNotNull { it.epgId }, now.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _activeProfile.value = repository.activeProfile()
        }
        viewModelScope.launch {
            channels.collectLatest { loadWindow() }
        }
        viewModelScope.launch {
            _miniPlayerAudioEnabled.value = repository.loadSettings().miniPlayerAudioEnabled
            livePlayerManager.setMiniAudioEnabled(_miniPlayerAudioEnabled.value)
        }
        viewModelScope.launch {
            repository.refreshVodSeriesCatalog()
        }
    }

    fun setFavoriteGroupFilter(groupId: Long?) {
        _favoriteGroupFilter.value = groupId
    }

    fun createFavoriteGroup(name: String) {
        viewModelScope.launch { repository.createFavoriteGroup(name) }
    }

    fun addChannelToFavorites(channelId: Long, groupId: Long?) {
        viewModelScope.launch {
            if (groupId != null) {
                repository.addChannelToFavoriteGroup(channelId, groupId)
            } else {
                repository.toggleFavorite(channelId, true)
            }
        }
    }

    fun isProfileAccessAllowed(): Boolean {
        val profile = _activeProfile.value ?: return true
        return ProfileAccessGuard.isWithinAllowedHours(profile)
    }

    fun profileAccessMessage(): String? {
        val profile = _activeProfile.value ?: return null
        return if (!ProfileAccessGuard.isWithinAllowedHours(profile)) {
            ProfileAccessGuard.outsideHoursMessage(profile)
        } else {
            null
        }
    }

    fun reloadPlaybackSettings() {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            _miniPlayerAudioEnabled.value = settings.miniPlayerAudioEnabled
            livePlayerManager.setMiniAudioEnabled(settings.miniPlayerAudioEnabled)
        }
    }

    fun tuneLastWatched(context: android.content.Context) {
        viewModelScope.launch {
            val ch = continueWatching.value.firstOrNull()
                ?: channels.value.firstOrNull()
                ?: return@launch
            livePlayerManager.tuneChannel(context, ch.id, ch.streamUrl)
            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
            repository.saveWatchPosition(ch.id, 0L)
        }
    }

    fun resumeContinueWatching(context: android.content.Context, item: ContinueWatchingItem) {
        viewModelScope.launch {
            livePlayerManager.tuneChannel(context, item.channel.id, item.channel.streamUrl)
            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
            repository.saveWatchPosition(item.channel.id, item.lastPosition, item.programTitle)
        }
    }

    suspend fun buildCatchupUrl(program: Program, channel: Channel): String? =
        repository.buildCatchupUrl(program, channel)

    private suspend fun loadWindow() {
        _epgLoading.value = true
        val channelIds = channels.value.mapNotNull { it.epgId }
        val start = _windowStart.value
        val end = start + windowDurationMs
        _epgPrograms.value = withContext(Dispatchers.IO) {
            repository.programsWindow(channelIds, start, end)
        }
        _epgLoading.value = false
    }
}
