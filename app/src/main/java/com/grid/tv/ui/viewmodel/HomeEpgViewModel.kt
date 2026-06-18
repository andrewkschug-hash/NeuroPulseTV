package com.grid.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.data.repository.FavoritesRepository
import com.grid.tv.domain.epg.CatchupUrlFormatter
import com.grid.tv.domain.epg.EpgProgramAction
import com.grid.tv.domain.epg.EpgProgramReplayState
import com.grid.tv.domain.epg.canReplayProgram
import com.grid.tv.domain.epg.resolveProgramAction
import com.grid.tv.domain.model.CatchupPlaybackContext
import com.grid.tv.domain.model.CatchupPlaybackSession
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.FavoriteGroup
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.Recommendation
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.data.repository.IptvRepositoryImpl.Companion.CHANNEL_PAGE_SIZE
import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.domain.model.ScannerRuntimeState
import com.grid.tv.feature.epg.GuideChannelFilter
import com.grid.tv.feature.scanner.ChannelScanner
import com.grid.tv.ui.component.EpgLayout
import com.grid.tv.feature.parental.ProfileAccessGuard
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.StreamPlaybackStatus
import com.grid.tv.ui.component.ProgramTimeState
import com.grid.tv.ui.component.programTimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EpgGuidePosition(
    val focusChannelIndex: Int = 0,
    val focusProgramIndex: Int = 0,
    val focusOnChannelColumn: Boolean = true,
    val timelineScrollPx: Int = 0,
    val hasSavedPosition: Boolean = false
)

@HiltViewModel
class HomeEpgViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val favoritesRepository: FavoritesRepository,
    val livePlayerManager: LivePlayerManager,
    private val channelScanner: ChannelScanner
) : ViewModel() {

    val channelScanStatuses: StateFlow<Map<Long, ChannelScanSnapshot>> = channelScanner.statuses
    val scannerRuntime: StateFlow<ScannerRuntimeState> = channelScanner.runtime

    companion object {
        /** Filter sentinel: show all favorited channels (any group). */
        const val FAVORITES_FILTER = -1L
        private const val WINDOW_CHUNK_MS = 2 * 60 * 60 * 1000L
        private const val MAX_WINDOW_MS = 24 * 60 * 60 * 1000L
        private const val PREVIEW_TUNE_DEBOUNCE_MS = 500L
    }

    private var previewTuneJob: Job? = null

    private val _miniPlayerAudioEnabled = MutableStateFlow(false)
    val miniPlayerAudioEnabled = _miniPlayerAudioEnabled.asStateFlow()

    private val _favoriteGroupFilter = MutableStateFlow<Long?>(null)
    val favoriteGroupFilter: StateFlow<Long?> = _favoriteGroupFilter.asStateFlow()

    /** Demo-mode favorites for placeholder channels (negative IDs). */
    private val _demoFavoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val demoFavoriteIds: StateFlow<Set<Long>> = _demoFavoriteIds.asStateFlow()

    private val _favoriteSavedMessage = MutableStateFlow<String?>(null)
    val favoriteSavedMessage: StateFlow<String?> = _favoriteSavedMessage.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _lastPlayedChannel = MutableStateFlow<Channel?>(null)
    val lastPlayedChannel: StateFlow<Channel?> = _lastPlayedChannel.asStateFlow()

    /** True after the user has opened a stream (preview or fullscreen) this session. */
    private val _guidePreviewEnabled = MutableStateFlow(false)
    val guidePreviewEnabled: StateFlow<Boolean> = _guidePreviewEnabled.asStateFlow()

    private val _guidePreviewChannelId = MutableStateFlow<Long?>(null)
    val guidePreviewChannelId: StateFlow<Long?> = _guidePreviewChannelId.asStateFlow()

    fun setLastPlayedChannel(channel: Channel) {
        _lastPlayedChannel.value = channel
    }

    suspend fun lookupChannel(channelId: Long): Channel? = repository.channelById(channelId)

    fun enableGuidePreview(channelId: Long) {
        _guidePreviewEnabled.value = true
        _guidePreviewChannelId.value = channelId
    }

    fun clearGuidePreviewUi() {
        _guidePreviewEnabled.value = false
        _guidePreviewChannelId.value = null
        clearGuidePreview()
    }

    fun resumeGuidePreviewIfEnabled(context: android.content.Context) {
        if (!_guidePreviewEnabled.value) return
        livePlayerManager.resumeGuidePreview(context)
    }

    private val _hasConnection = MutableStateFlow(false)
    val hasConnection: StateFlow<Boolean> = _hasConnection.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteGroups: StateFlow<List<FavoriteGroup>> = repository.favoriteGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _guideFilter = MutableStateFlow(GuideChannelFilter.All)
    val guideFilter: StateFlow<GuideChannelFilter> = _guideFilter.asStateFlow()

    private val _isReloadingChannels = MutableStateFlow(false)
    val isReloadingChannels: StateFlow<Boolean> = _isReloadingChannels.asStateFlow()

    private val _guideFiltersConfigured = MutableStateFlow(false)
    val guideFiltersConfigured: StateFlow<Boolean> = _guideFiltersConfigured.asStateFlow()

    val channelGroups: StateFlow<List<String>> = repository.groups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True when the playlist has imported at least one channel (ignores category/favorite filters). */
    val hasCatalogChannels: StateFlow<Boolean> = repository.hasChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _hasMoreChannels = MutableStateFlow(true)
    val hasMoreChannels: StateFlow<Boolean> = _hasMoreChannels.asStateFlow()

    private var channelDbOffset = 0
    private var loadingChannels = false
    private var channelLoadJob: Job? = null

    private val _hideAdultContent = MutableStateFlow(true)
    val hideAdultContent: StateFlow<Boolean> = _hideAdultContent.asStateFlow()

    private val _guidePosition = MutableStateFlow(EpgGuidePosition())
    val guidePosition: StateFlow<EpgGuidePosition> = _guidePosition.asStateFlow()

    val continueWatchingItems: StateFlow<List<ContinueWatchingItem>> =
        continueWatchingRepository.observeItems(limit = 12)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Live channels are not tracked in Continue Watching (VOD + series only). */
    val continueWatching: StateFlow<List<Channel>> =
        kotlinx.coroutines.flow.flowOf(emptyList<Channel>())
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

    val vodProgress: StateFlow<Map<Long, Long>> = repository.vodWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val featuredMovies: StateFlow<List<VodItem>> = vod
        .map { items ->
            items.sortedByDescending { it.addedEpochSec ?: 0L }.take(20)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val featuredSeries: StateFlow<List<SeriesShow>> = series
        .map { items -> items.take(20) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val now = MutableStateFlow(System.currentTimeMillis())

    private val _replayUrlsByProgramId = MutableStateFlow<Map<Long, String>>(emptyMap())
    val replayUrlsByProgramId: StateFlow<Map<Long, String>> = _replayUrlsByProgramId.asStateFlow()

    private val _epgPrograms = MutableStateFlow<List<Program>>(emptyList())
    val epgPrograms = _epgPrograms.asStateFlow()

    fun replayState(program: Program, channel: Channel, nowMs: Long): EpgProgramReplayState {
        val replayUrl = _replayUrlsByProgramId.value[program.id]
        val timeState = programTimeState(program, nowMs)
        val canReplay = canReplayProgram(program, channel, nowMs, replayUrl)
        val action = resolveProgramAction(program, channel, nowMs, replayUrl)
        return EpgProgramReplayState(
            programId = program.id,
            timeState = timeState,
            action = if (action == EpgProgramAction.WATCH_REPLAY && !canReplay) {
                EpgProgramAction.WATCH_LIVE
            } else {
                action
            },
            canReplay = canReplay,
            replayUrl = replayUrl?.takeIf { canReplay }
        )
    }

    suspend fun stageCatchupPlayback(program: Program, channel: Channel): String? {
        val url = _replayUrlsByProgramId.value[program.id] ?: buildCatchupUrl(program, channel)
        if (url.isNullOrBlank()) return null
        CatchupPlaybackContext.stage(
            CatchupPlaybackSession(
                programTitle = program.title,
                channelName = channel.name,
                channelId = channel.id,
                liveStreamUrl = channel.streamUrl,
                programStartMs = program.startTime,
                programEndMs = program.endTime,
                replayUrl = url
            )
        )
        return url
    }

    private fun recomputeReplayUrls(programs: List<Program>) {
        val channelsByEpg = _channels.value.associateBy { it.epgId }
        _replayUrlsByProgramId.value = programs.mapNotNull { program ->
            val channel = channelsByEpg[program.channelEpgId] ?: return@mapNotNull null
            CatchupUrlFormatter.build(program, channel)?.let { program.id to it }
        }.toMap()
    }

    private val _epgLoading = MutableStateFlow(false)
    val epgLoading = _epgLoading.asStateFlow()

    private val _windowStart = MutableStateFlow(System.currentTimeMillis() - 90 * 60 * 1000)
    val windowStart: StateFlow<Long> = _windowStart.asStateFlow()

    private val _windowDurationMs = MutableStateFlow(4 * 60 * 60 * 1000L)
    val windowDurationMs: StateFlow<Long> = _windowDurationMs.asStateFlow()

    val programs: StateFlow<List<Program>> = channels
        .flatMapLatest { ch ->
            repository.programs(ch.mapNotNull { it.epgId }, now.value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _hasConnection.value = repository.hasActiveConnection()
            _isInitializing.value = false
        }
        viewModelScope.launch {
            _activeProfile.value = repository.activeProfile()
        }
        viewModelScope.launch {
            loadWindow()
        }
        viewModelScope.launch {
            combine(_windowStart, _windowDurationMs) { _, _ -> }
                .collectLatest { loadWindow() }
        }
        viewModelScope.launch {
            combine(_favoriteGroupFilter, _guideFilter, _hideAdultContent) { _, _, _ -> }
                .collectLatest { reloadChannels() }
        }
        viewModelScope.launch {
            repository.epgDataRevision().collect {
                if (it > 0L) loadWindow()
            }
        }
        viewModelScope.launch {
            val settings = repository.loadSettings()
            _miniPlayerAudioEnabled.value = settings.miniPlayerAudioEnabled
            _hideAdultContent.value = settings.hideAdultContent
            _guideFilter.value = GuideChannelFilter(settings.guideChannelGroups)
            _guideFiltersConfigured.value = settings.guideFiltersConfigured
            livePlayerManager.setMiniAudioEnabled(settings.miniPlayerAudioEnabled)
            livePlayerManager.setAutoReconnectOnDrop(settings.autoReconnectOnDrop)
        }
        viewModelScope.launch {
            repository.playlists().collect { list ->
                if (!_isInitializing.value) {
                    _hasConnection.value = list.isNotEmpty()
                }
                if (list.isEmpty()) {
                    _demoFavoriteIds.value = emptySet()
                    _favoriteGroupFilter.value = null
                    _guideFilter.value = GuideChannelFilter.All
                }
            }
        }
    }

    fun saveGuidePosition(position: EpgGuidePosition) {
        _guidePosition.value = position.copy(hasSavedPosition = true)
    }

    fun setFavoriteGroupFilter(groupId: Long?) {
        _favoriteGroupFilter.value = groupId
    }

    fun setGuideFilter(filter: GuideChannelFilter, markConfigured: Boolean = false) {
        _guideFilter.value = filter
        if (markConfigured) {
            _guideFiltersConfigured.value = true
            persistGuideFilter(filter, configured = true)
        }
    }

    fun saveGuideChannelGroups(groups: Set<String>, markConfigured: Boolean = true) {
        val filter = GuideChannelFilter(groups)
        _guideFilter.value = filter
        _guideFiltersConfigured.value = markConfigured
        persistGuideFilter(filter, configured = markConfigured)
    }

    fun reloadGuideSettings() {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            _guideFilter.value = GuideChannelFilter(settings.guideChannelGroups)
            _guideFiltersConfigured.value = settings.guideFiltersConfigured
        }
    }

    fun clearGuideFilter() {
        setGuideFilter(GuideChannelFilter.All)
    }

    private fun persistGuideFilter(filter: GuideChannelFilter, configured: Boolean) {
        viewModelScope.launch {
            val current = repository.loadSettings()
            repository.saveSettings(
                current.copy(
                    guideChannelGroups = filter.selectedGroups,
                    guideFiltersConfigured = configured
                )
            )
        }
    }

    /** Extend the visible timeline forward when the user navigates past loaded data. */
    fun extendWindowForward() {
        val current = _windowDurationMs.value
        if (current >= MAX_WINDOW_MS) return
        _windowDurationMs.value = (current + WINDOW_CHUNK_MS).coerceAtMost(MAX_WINDOW_MS)
        viewModelScope.launch { loadWindow() }
    }

    /** Extend the visible timeline backward (into the past). Returns scroll adjustment in px. */
    fun extendWindowBackward(): Int {
        val shift = WINDOW_CHUNK_MS
        val earliest = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        if (_windowStart.value - shift < earliest) return 0
        _windowStart.value -= shift
        _windowDurationMs.value = (_windowDurationMs.value + shift).coerceAtMost(MAX_WINDOW_MS)
        viewModelScope.launch { loadWindow() }
        return (shift * EpgLayout.dpPerMs()).toInt()
    }

    fun toggleFavorite(channelId: Long, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            if (channelId < 0) {
                _demoFavoriteIds.value = if (currentlyFavorite) {
                    _demoFavoriteIds.value - channelId
                } else {
                    _demoFavoriteIds.value + channelId
                }
            } else {
                repository.toggleFavorite(channelId, !currentlyFavorite)
            }
        }
    }

    fun createFavoriteGroup(name: String) {
        viewModelScope.launch {
            favoritesRepository.createGroup(repository.activeProfileId(), name)
        }
    }

    fun addChannelToFavorites(channelId: Long, groupId: Long?) {
        viewModelScope.launch {
            if (channelId < 0) {
                _demoFavoriteIds.value = _demoFavoriteIds.value + channelId
            } else if (groupId != null) {
                repository.addChannelToFavoriteGroup(channelId, groupId)
            } else {
                repository.toggleFavorite(channelId, true)
            }
        }
    }

    fun saveChannelToFavorites(channelId: Long, channelName: String) {
        viewModelScope.launch {
            if (channelId < 0) {
                _demoFavoriteIds.value = _demoFavoriteIds.value + channelId
            } else {
                repository.toggleFavorite(channelId, true)
            }
            _favoriteGroupFilter.value = FAVORITES_FILTER
            _favoriteSavedMessage.value = "$channelName saved to ★ Favorites"
        }
    }

    fun clearFavoriteSavedMessage() {
        _favoriteSavedMessage.value = null
    }

    fun addChannelToFavoriteGroup(channelId: Long, groupId: Long) {
        viewModelScope.launch {
            if (channelId < 0) {
                _demoFavoriteIds.value = _demoFavoriteIds.value + channelId
            } else {
                repository.addChannelToFavoriteGroup(channelId, groupId)
            }
            _favoriteGroupFilter.value = groupId
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

    fun reloadPlaybackSettings(context: android.content.Context) {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            _miniPlayerAudioEnabled.value = settings.miniPlayerAudioEnabled
            _hideAdultContent.value = settings.hideAdultContent
            livePlayerManager.setMiniAudioEnabled(settings.miniPlayerAudioEnabled)
            livePlayerManager.setAutoReconnectOnDrop(settings.autoReconnectOnDrop)
            livePlayerManager.syncPlaybackSettings(
                context.applicationContext,
                settings.bufferSize,
                settings.preferHardwareDecoding
            )
        }
    }

    fun tuneLastWatched(context: android.content.Context) {
        // Mini player removed — no background tune on the guide.
    }

    private var lastHealthReport: Pair<Long, StreamPlaybackStatus>? = null

    fun reportPlaybackHealth(channelId: Long, status: StreamPlaybackStatus) {
        if (status == StreamPlaybackStatus.IDLE || status == StreamPlaybackStatus.LOADING) return
        val key = channelId to status
        if (lastHealthReport == key) return
        lastHealthReport = key
        val success = status == StreamPlaybackStatus.PLAYING ||
            status == StreamPlaybackStatus.AUDIO_ONLY
        channelScanner.reportPlaybackResult(channelId, success)
        viewModelScope.launch {
            repository.reportStreamSession(
                channelId = channelId,
                loadMs = if (success) 1200 else 5000,
                bufferEvents = when (status) {
                    StreamPlaybackStatus.STALLED, StreamPlaybackStatus.NO_SIGNAL -> 3
                    StreamPlaybackStatus.ERROR, StreamPlaybackStatus.UNAVAILABLE -> 5
                    else -> 0
                },
                success = success
            )
        }
    }

    fun updateScannerViewport(channelIds: List<Long>) {
        channelScanner.setPriorityChannelIds(channelIds)
    }

    fun setScannerForeground(visible: Boolean) {
        channelScanner.setAppInForeground(visible)
    }

    fun previewChannel(context: android.content.Context, channel: Channel, programTitle: String? = null) {
        lastHealthReport = null
        previewTuneJob?.cancel()
        previewTuneJob = viewModelScope.launch {
            delay(PREVIEW_TUNE_DEBOUNCE_MS)
            if (channel.streamUrl.isBlank()) return@launch
            livePlayerManager.tuneChannel(context, channel)
            livePlayerManager.setMode(LivePlayerManager.Mode.MINI)
        }
    }

    fun cancelPreviewTune() {
        previewTuneJob?.cancel()
        previewTuneJob = null
    }

    fun clearGuidePreview() {
        cancelPreviewTune()
        livePlayerManager.stopGuidePreview()
    }

    suspend fun buildCatchupUrl(program: Program, channel: Channel): String? =
        repository.buildCatchupUrl(program, channel)

    fun loadMoreChannels() {
        if (loadingChannels || !_hasMoreChannels.value) return
        viewModelScope.launch { loadMoreChannelsInternal() }
    }

    private fun reloadChannels() {
        channelLoadJob?.cancel()
        channelLoadJob = viewModelScope.launch {
            _isReloadingChannels.value = true
            _channels.value = emptyList()
            channelDbOffset = 0
            _hasMoreChannels.value = true
            loadMoreChannelsInternal()
            _isReloadingChannels.value = false
            loadWindow()
        }
    }

    private suspend fun loadMoreChannelsInternal() {
        if (!_hasMoreChannels.value) return
        loadingChannels = true
        try {
            val favoriteFilter = _favoriteGroupFilter.value
            val groups = _guideFilter.value.selectedGroups
            val page = repository.channelsPage(
                groups = groups,
                search = "",
                favoritesOnly = favoriteFilter != null,
                favoriteGroupId = favoriteFilter,
                limit = CHANNEL_PAGE_SIZE,
                offset = channelDbOffset
            )
            channelDbOffset += page.size
            _hasMoreChannels.value = page.size >= CHANNEL_PAGE_SIZE
            val filtered = page.let { list ->
                if (_hideAdultContent.value) {
                    list.filter { !ProfileAccessGuard.isAdultGroup(it.group) }
                } else {
                    list
                }
            }
            _channels.value = _channels.value + filtered
            if (filtered.isNotEmpty()) {
                loadWindow()
            }
        } finally {
            loadingChannels = false
        }
    }

    private suspend fun loadWindow() {
        _epgLoading.value = true
        val channelsForLookup = _channels.value
        if (channelsForLookup.isEmpty()) {
            val fallbackEpgIds = repository.allDistinctEpgIds().take(500)
            if (fallbackEpgIds.isEmpty()) {
                _epgPrograms.value = emptyList()
                _epgLoading.value = false
                return
            }
            val start = _windowStart.value
            val end = start + _windowDurationMs.value
            _epgPrograms.value = withContext(Dispatchers.IO) {
                repository.programsWindow(fallbackEpgIds, start, end)
            }
            recomputeReplayUrls(_epgPrograms.value)
            _epgLoading.value = false
            return
        }
        val start = _windowStart.value
        val end = start + _windowDurationMs.value
        _epgPrograms.value = withContext(Dispatchers.IO) {
            repository.programsWindowForChannels(channelsForLookup, start, end)
        }
        recomputeReplayUrls(_epgPrograms.value)
        _epgLoading.value = false
    }
}
