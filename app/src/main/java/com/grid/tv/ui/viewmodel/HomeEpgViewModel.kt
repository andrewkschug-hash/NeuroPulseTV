package com.grid.tv.ui.viewmodel

import android.util.Log
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
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.data.repository.IptvRepositoryImpl.Companion.CHANNEL_PAGE_SIZE
import com.grid.tv.domain.model.ChannelScanSnapshot
import com.grid.tv.domain.model.ScannerRuntimeState
import com.grid.tv.feature.epg.EpgFlowLogger
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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

    val scannerRuntime: StateFlow<ScannerRuntimeState> = channelScanner.runtime

    companion object {
        /** Filter sentinel: show all favorited channels (any group). */
        const val FAVORITES_FILTER = -1L
        private const val WINDOW_CHUNK_MS = 2 * 60 * 60 * 1000L
        private const val MAX_WINDOW_MS = 24 * 60 * 60 * 1000L
        private const val PREVIEW_TUNE_DEBOUNCE_MS = 500L
        /** Visible guide rows to hydrate from cache before loading the rest of the page. */
        private const val PRIORITY_EPG_CHANNEL_COUNT = 50
        private const val VIEWPORT_EPG_DEBOUNCE_MS = 300L
        /** Cap viewport provider fetches so scrolling the guide cannot fan out dozens of HTTP calls. */
        private const val VIEWPORT_EPG_MAX_CHANNEL_IDS = 10
        private const val TAG = "EpgFlow"
    }

    private var guideBootstrapComplete = false
    private var epgLoadGeneration = 0
    private var channelFilterGeneration = 0

    private var previewTuneJob: Job? = null
    private var viewportEpgJob: Job? = null
    private var viewportObserveJob: Job? = null
    private var loadWindowJob: Job? = null

    private data class LoadWindowSnapshot(
        val generation: Int,
        val existingPrograms: List<Program>,
        val channels: List<Channel>,
        val start: Long,
        val end: Long
    )

    private data class LoadWindowOutcome(
        val generation: Int,
        val programs: List<Program>,
        val replayUrls: Map<Long, String>,
        val coverage: ProgrammeCoverageStats? = null
    )

    private data class ProgrammeCoverageStats(
        val programCount: Int,
        val channelCount: Int,
        val matchedChannelCount: Int
    )

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

    private val _guideSettingsLoaded = MutableStateFlow(false)
    val guideSettingsLoaded: StateFlow<Boolean> = _guideSettingsLoaded.asStateFlow()

    val channelGroups: StateFlow<List<String>> = repository.groups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupChannelCounts: StateFlow<Map<String, Int>> = repository.groupChannelCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** True when the playlist has imported at least one channel (ignores category/favorite filters). */
    val hasCatalogChannels: StateFlow<Boolean> = repository.hasChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    /** Only statuses for currently loaded guide rows — avoids recomposing on full 10k scan map updates. */
    val channelScanStatuses: StateFlow<Map<Long, ChannelScanSnapshot>> = combine(
        channelScanner.statuses,
        channels
    ) { allStatuses, visibleChannels ->
        if (visibleChannels.isEmpty()) {
            emptyMap()
        } else {
            buildMap {
                visibleChannels.forEach { channel ->
                    allStatuses[channel.id]?.let { put(channel.id, it) }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    val vodProgress: StateFlow<Map<Long, Long>> = repository.vodWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    private fun computeReplayUrls(programs: List<Program>, channels: List<Channel>): Map<Long, String> {
        if (programs.isEmpty() || channels.isEmpty()) return emptyMap()
        val channelsByEpg = HashMap<String, Channel>(channels.size * 2)
        for (channel in channels) {
            channel.epgId?.trim()?.takeIf { it.isNotEmpty() }?.let { channelsByEpg.putIfAbsent(it, channel) }
            channelsByEpg.putIfAbsent(channel.name, channel)
        }
        val out = HashMap<Long, String>(minOf(programs.size, 512))
        for (program in programs) {
            val channel = channelsByEpg[program.channelEpgId] ?: continue
            CatchupUrlFormatter.build(program, channel)?.let { out[program.id] = it }
        }
        return out
    }

    private val _epgLoading = MutableStateFlow(false)
    val epgLoading = _epgLoading.asStateFlow()

    private val _windowStart = MutableStateFlow(System.currentTimeMillis() - 90 * 60 * 1000)
    val windowStart: StateFlow<Long> = _windowStart.asStateFlow()

    private val _windowDurationMs = MutableStateFlow(4 * 60 * 60 * 1000L)
    val windowDurationMs: StateFlow<Long> = _windowDurationMs.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _hasConnection.value = repository.hasActiveConnection()
            _activeProfile.value = repository.activeProfile()
            _isInitializing.value = false
        }
        viewModelScope.launch(Dispatchers.IO) {
            bootstrapGuideFromSettings()
        }
        viewModelScope.launch(Dispatchers.IO) {
            combine(_windowStart, _windowDurationMs, _guideSettingsLoaded) { _, _, loaded -> loaded }
                .filter { it }
                .collectLatest { scheduleLoadWindow() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _channels.collectLatest { channels ->
                if (guideBootstrapComplete && channels.isNotEmpty()) {
                    scheduleLoadWindow()
                }
            }
        }
        viewModelScope.launch {
            combine(_favoriteGroupFilter, _guideFilter, _hideAdultContent) { _, _, _ -> }
                .collectLatest {
                    if (!guideBootstrapComplete) return@collectLatest
                    reloadChannels()
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.epgDataRevision().collect {
                if (it > 0L && guideBootstrapComplete) {
                    refreshCurrentWindowPrograms()
                }
            }
        }
        viewModelScope.launch {
            combine(channelGroups, _guideFiltersConfigured) { groups, configured ->
                groups to configured
            }.collectLatest { (groups, configured) ->
                if (!guideBootstrapComplete || !configured || groups.isEmpty()) return@collectLatest
                val current = _guideFilter.value
                if (!current.isActive) return@collectLatest
                val valid = current.selectedGroups.intersect(groups.toSet())
                if (valid == current.selectedGroups) return@collectLatest
                val updated = GuideChannelFilter(valid)
                _guideFilter.value = updated
                persistGuideFilter(updated, configured = true)
                reloadChannels()
            }
        }
        viewModelScope.launch {
            repository.playlists().collect { list ->
                if (!_isInitializing.value) {
                    _hasConnection.value = list.isNotEmpty()
                }
                if (list.isEmpty()) {
                    _demoFavoriteIds.value = emptySet()
                    _favoriteGroupFilter.value = null
                }
            }
        }
    }

    private suspend fun bootstrapGuideFromSettings() {
        val settings = repository.loadSettings()
        _miniPlayerAudioEnabled.value = settings.miniPlayerAudioEnabled
        _hideAdultContent.value = settings.hideAdultContent
        val sanitizedFilter = sanitizeGuideFilter(
            groups = settings.guideChannelGroups,
            configured = settings.guideFiltersConfigured
        )
        _guideFilter.value = sanitizedFilter
        _guideFiltersConfigured.value = settings.guideFiltersConfigured
        _guideSettingsLoaded.value = true
        livePlayerManager.setMiniAudioEnabled(settings.miniPlayerAudioEnabled)
        livePlayerManager.setAutoReconnectOnDrop(settings.autoReconnectOnDrop)
        guideBootstrapComplete = true
        reloadChannels()
    }

    /** Drop stale group names after playlist re-import so the SQL IN filter matches live groupName values. */
    private suspend fun sanitizeGuideFilter(
        groups: Set<String>,
        configured: Boolean
    ): GuideChannelFilter {
        if (!configured || groups.isEmpty()) return GuideChannelFilter(groups)
        val available = repository.groups().first()
        if (available.isEmpty()) return GuideChannelFilter(groups)
        val valid = groups.intersect(available.toSet())
        if (valid == groups) return GuideChannelFilter(groups)
        Log.w(
            TAG,
            "Pruned stale guide filter groups: kept ${valid.size} of ${groups.size} " +
                "(playlist groups may have changed since last save)"
        )
        val updated = GuideChannelFilter(valid)
        if (configured) {
            repository.saveGuideChannelFilter(valid, configured = true)
        }
        return updated
    }

    fun saveGuidePosition(position: EpgGuidePosition) {
        _guidePosition.value = position.copy(hasSavedPosition = true)
    }

    fun setFavoriteGroupFilter(groupId: Long?) {
        _favoriteGroupFilter.value = groupId
    }

    fun setGuideFilter(filter: GuideChannelFilter, markConfigured: Boolean = false) {
        val changed = _guideFilter.value != filter
        _guideFilter.value = filter
        if (markConfigured) {
            _guideFiltersConfigured.value = true
        }
        if (changed || markConfigured) {
            persistGuideFilter(filter, configured = _guideFiltersConfigured.value)
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
            val nextFilter = sanitizeGuideFilter(
                groups = settings.guideChannelGroups,
                configured = settings.guideFiltersConfigured
            )
            val filterChanged = _guideFilter.value != nextFilter
            _guideFilter.value = nextFilter
            _guideFiltersConfigured.value = settings.guideFiltersConfigured
            _guideSettingsLoaded.value = true
            if (filterChanged && guideBootstrapComplete) {
                reloadChannels()
            }
        }
    }

    fun clearGuideFilter() {
        setGuideFilter(GuideChannelFilter.All, markConfigured = true)
    }

    private fun persistGuideFilter(filter: GuideChannelFilter, configured: Boolean) {
        viewModelScope.launch {
            repository.saveGuideChannelFilter(filter.selectedGroups, configured)
        }
    }

    /** Extend the visible timeline forward when the user navigates past loaded data. */
    fun extendWindowForward() {
        val current = _windowDurationMs.value
        if (current >= MAX_WINDOW_MS) return
        _windowDurationMs.value = (current + WINDOW_CHUNK_MS).coerceAtMost(MAX_WINDOW_MS)
        viewModelScope.launch(Dispatchers.IO) { loadWindow() }
    }

    /** Extend the visible timeline backward (into the past). Returns scroll adjustment in px. */
    fun extendWindowBackward(): Int {
        val shift = WINDOW_CHUNK_MS
        val earliest = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        if (_windowStart.value - shift < earliest) return 0
        _windowStart.value -= shift
        _windowDurationMs.value = (_windowDurationMs.value + shift).coerceAtMost(MAX_WINDOW_MS)
        viewModelScope.launch(Dispatchers.IO) { loadWindow() }
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

    /**
     * Debounced viewport handler: prioritizes scanner probes, observes DB programmes for visible
     * rows, and fast-tracks a short-window provider fetch so tiles populate before bulk XMLTV sync.
     */
    fun onGuideViewportChanged(visibleChannelIds: List<Long>, focusNeighborIds: List<Long> = emptyList()) {
        channelScanner.setPriorityChannelIds((visibleChannelIds + focusNeighborIds).distinct())
        if (visibleChannelIds.isEmpty()) return

        viewportEpgJob?.cancel()
        viewportEpgJob = viewModelScope.launch(Dispatchers.IO) {
            delay(VIEWPORT_EPG_DEBOUNCE_MS)
            val visible = (visibleChannelIds + focusNeighborIds).distinct().take(VIEWPORT_EPG_MAX_CHANNEL_IDS)
            startViewportProgramObservation(visible)
            repository.fetchCurrentEpgForChannels(visible.map { it.toString() })
            val visibleChannels = _channels.value.filter { it.id in visible.toSet() }
            if (visibleChannels.isNotEmpty()) {
                appendProgramsForChannels(visibleChannels)
            }
        }
    }

    private fun startViewportProgramObservation(channelIds: List<Long>) {
        viewportObserveJob?.cancel()
        val channelIdSet = channelIds.toSet()
        viewportObserveJob = viewModelScope.launch(Dispatchers.IO) {
            combine(_channels, _windowStart, _windowDurationMs) { channels, start, duration ->
                Triple(
                    channels.filter { it.id in channelIdSet },
                    start,
                    start + duration
                )
            }
                .distinctUntilChanged { old, new ->
                    old.first.size == new.first.size &&
                        old.first.map { it.id } == new.first.map { it.id } &&
                        old.second == new.second &&
                        old.third == new.third
                }
                .flatMapLatest { (visibleChannels, windowStart, windowEnd) ->
                    if (visibleChannels.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        repository.observeProgramsWindowForChannels(
                            visibleChannels,
                            windowStart,
                            windowEnd
                        )
                    }
                }
                .collectLatest { programs ->
                    if (programs.isEmpty()) return@collectLatest
                    val existing = _epgPrograms.value
                    val channels = _channels.value
                    val merged = mergePrograms(existing, programs)
                    val replayUrls = computeReplayUrls(merged, channels)
                    withContext(Dispatchers.Main.immediate) {
                        _epgPrograms.value = merged
                        _replayUrlsByProgramId.value = replayUrls
                    }
                }
        }
    }

    private suspend fun refreshCurrentWindowPrograms() {
        val channels = _channels.value
        if (channels.isEmpty()) return
        val visible = channels.take(PRIORITY_EPG_CHANNEL_COUNT)
        val start = _windowStart.value
        val end = start + _windowDurationMs.value
        val existing = _epgPrograms.value
        val programs = repository.programsWindowForChannels(visible, start, end)
        val merged = mergePrograms(existing, programs)
        val replayUrls = computeReplayUrls(merged, channels)
        withContext(Dispatchers.Main.immediate) {
            _epgPrograms.value = merged
            _replayUrlsByProgramId.value = replayUrls
        }
    }

    private fun scheduleLoadWindow() {
        loadWindowJob?.cancel()
        loadWindowJob = viewModelScope.launch(Dispatchers.IO) {
            loadWindow()
        }
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
        val generation = channelFilterGeneration
        viewModelScope.launch { loadMoreChannelsInternal(expectedGeneration = generation) }
    }

    private fun reloadChannels() {
        channelLoadJob?.cancel()
        val generation = ++channelFilterGeneration
        channelLoadJob = viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main.immediate) {
                _isReloadingChannels.value = true
            }
            _channels.value = emptyList()
            _epgPrograms.value = emptyList()
            channelDbOffset = 0
            _hasMoreChannels.value = true
            loadMoreChannelsInternal(expectedGeneration = generation)
            if (generation == channelFilterGeneration) {
                withContext(Dispatchers.Main.immediate) {
                    _isReloadingChannels.value = false
                }
            }
        }
    }

    private suspend fun reloadChannelsInternal() {
        reloadChannels()
    }

    private suspend fun loadMoreChannelsInternal(expectedGeneration: Int = channelFilterGeneration) {
        if (expectedGeneration != channelFilterGeneration) return
        if (!_hasMoreChannels.value) return
        loadingChannels = true
        try {
            val page = fetchFilteredChannelPage(channelDbOffset)
            if (expectedGeneration != channelFilterGeneration) return
            channelDbOffset += page.size
            _hasMoreChannels.value = page.size >= CHANNEL_PAGE_SIZE
            if (page.isEmpty()) return
            _channels.value = _channels.value + page
            if (_channels.value.size <= CHANNEL_PAGE_SIZE) {
                loadWindow()
            } else {
                appendProgramsForChannels(page)
            }
        } finally {
            loadingChannels = false
        }
    }

    private suspend fun fetchFilteredChannelPage(offset: Int): List<Channel> {
        val favoriteFilter = _favoriteGroupFilter.value
        val groups = _guideFilter.value.selectedGroups
        val page = repository.channelsPage(
            groups = groups,
            search = "",
            favoritesOnly = favoriteFilter != null,
            favoriteGroupId = favoriteFilter,
            limit = CHANNEL_PAGE_SIZE,
            offset = offset
        )
        return if (_hideAdultContent.value) {
            page.filter { !ProfileAccessGuard.isAdultGroup(it.group) }
        } else {
            page
        }.filter { _guideFilter.value.appliesTo(it) }
    }

    private suspend fun appendProgramsForChannels(newChannels: List<Channel>) {
        if (newChannels.isEmpty()) return
        val generation = ++epgLoadGeneration
        val start = _windowStart.value
        val end = start + _windowDurationMs.value
        val existing = _epgPrograms.value
        val channels = _channels.value
        val newPrograms = repository.programsWindowForChannels(newChannels, start, end)
        if (generation != epgLoadGeneration) return
        val merged = mergePrograms(existing, newPrograms)
        val replayUrls = computeReplayUrls(merged, channels)
        withContext(Dispatchers.Main.immediate) {
            _epgPrograms.value = merged
            _replayUrlsByProgramId.value = replayUrls
        }
    }

    private fun mergePrograms(existing: List<Program>, additional: List<Program>): List<Program> {
        if (additional.isEmpty()) return existing
        if (existing.isEmpty()) return additional.sortedBy { it.startTime }
        val byId = LinkedHashMap<Long, Program>(existing.size + additional.size)
        for (program in existing) {
            byId[program.id] = program
        }
        for (program in additional) {
            byId[program.id] = program
        }
        return byId.values.sortedBy { it.startTime }
    }

    private suspend fun loadWindow() {
        if (!guideBootstrapComplete) return
        val snapshot = LoadWindowSnapshot(
            generation = ++epgLoadGeneration,
            existingPrograms = _epgPrograms.value,
            channels = _channels.value,
            start = _windowStart.value,
            end = _windowStart.value + _windowDurationMs.value
        )
        withContext(Dispatchers.Main.immediate) {
            _epgLoading.value = true
        }

        val outcome = computeLoadWindowOutcome(snapshot)
        if (outcome == null || outcome.generation != epgLoadGeneration) {
            withContext(Dispatchers.Main.immediate) {
                _epgLoading.value = false
            }
            return
        }

        withContext(Dispatchers.Main.immediate) {
            _epgPrograms.value = outcome.programs
            _replayUrlsByProgramId.value = outcome.replayUrls
            _epgLoading.value = false
            outcome.coverage?.let { stats ->
                EpgFlowLogger.guideLoaded(
                    programCount = stats.programCount,
                    channelCount = stats.channelCount,
                    matchedChannelCount = stats.matchedChannelCount
                )
                Log.i(
                    TAG,
                    "loadWindow: ${stats.programCount} programme rows, " +
                        "${stats.matchedChannelCount}/${stats.channelCount} " +
                        "filtered channels have matching epgId in window"
                )
            }
        }
    }

    private suspend fun computeLoadWindowOutcome(snapshot: LoadWindowSnapshot): LoadWindowOutcome? {
        val channelsForLookup = snapshot.channels
        val start = snapshot.start
        val end = snapshot.end
        val generation = snapshot.generation

        if (channelsForLookup.isEmpty()) {
            val priorityChannels = fetchFilteredChannelPage(offset = 0)
                .take(PRIORITY_EPG_CHANNEL_COUNT)
            Log.d(
                TAG,
                "loadWindow: no channels loaded yet; priority filtered batch=${priorityChannels.size}, " +
                    "filter=${_guideFilter.value.label}"
            )
            if (priorityChannels.isEmpty()) {
                return LoadWindowOutcome(
                    generation = generation,
                    programs = emptyList(),
                    replayUrls = emptyMap()
                )
            }
            val programs = repository.programsWindowForChannels(priorityChannels, start, end)
            if (generation != epgLoadGeneration) return null
            val merged = mergePrograms(snapshot.existingPrograms, programs)
            Log.i(
                TAG,
                "loadWindow priority (filtered cache): ${programs.size} programmes for " +
                    "${priorityChannels.size} channels, window [$start, $end]"
            )
            return LoadWindowOutcome(
                generation = generation,
                programs = merged,
                replayUrls = computeReplayUrls(merged, priorityChannels),
                coverage = programmeCoverageStats(priorityChannels, merged)
            )
        }

        val priorityCount = minOf(PRIORITY_EPG_CHANNEL_COUNT, channelsForLookup.size)
        val priorityChannels = channelsForLookup.take(priorityCount)
        val remainingChannels = channelsForLookup.drop(priorityCount)
        Log.d(
            TAG,
            "loadWindow: ${channelsForLookup.size} filtered channels, priority=$priorityCount, " +
                "window [$start, $end], withEpgId=${channelsForLookup.count { !it.epgId.isNullOrBlank() }}"
        )

        val priorityPrograms = repository.programsWindowForChannels(priorityChannels, start, end)
        if (generation != epgLoadGeneration) return null
        var merged = mergePrograms(snapshot.existingPrograms, priorityPrograms)

        // Defer programme load for channels beyond the first screen — avoids 200× resolver work at open.
        if (remainingChannels.isNotEmpty()) {
            Log.d(
                TAG,
                "loadWindow: deferring programme fetch for ${remainingChannels.size} off-screen channel(s)"
            )
        }

        return LoadWindowOutcome(
            generation = generation,
            programs = merged,
            replayUrls = computeReplayUrls(merged, channelsForLookup),
            coverage = programmeCoverageStats(channelsForLookup, merged)
        )
    }

    private fun programmeCoverageStats(
        channelsForLookup: List<Channel>,
        programs: List<Program>
    ): ProgrammeCoverageStats {
        val channelsWithPrograms = HashSet<String>(programs.size)
        for (program in programs) {
            channelsWithPrograms.add(program.channelEpgId.lowercase())
        }
        var matchedChannels = 0
        for (channel in channelsForLookup) {
            val epgId = channel.epgId ?: continue
            if (channelsWithPrograms.contains(epgId.lowercase())) {
                matchedChannels++
            }
        }
        return ProgrammeCoverageStats(
            programCount = programs.size,
            channelCount = channelsForLookup.size,
            matchedChannelCount = matchedChannels
        )
    }
}
