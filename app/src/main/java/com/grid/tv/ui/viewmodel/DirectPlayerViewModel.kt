package com.grid.tv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.RecordedMediaDao
import com.grid.tv.data.db.entity.RecordedMediaEntity
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.feature.vod.VodResumeResolver
import com.grid.tv.feature.subtitles.ActiveSubtitle
import com.grid.tv.feature.subtitles.SubtitleManager
import com.grid.tv.feature.subtitles.SubtitleRequest
import com.grid.tv.player.ExternalPlayerId
import com.grid.tv.player.PictureInPictureController
import com.grid.tv.player.IptvOnDemandContentKind
import com.grid.tv.player.IptvOnDemandMediaItem
import com.grid.tv.player.IptvStreamFormat
import com.grid.tv.player.IptvStreamFormatRegistry
import com.grid.tv.player.PlaybackStartupPriority
import com.grid.tv.player.PlayerFactory
import com.grid.tv.player.ResolvedVodStream
import com.grid.tv.player.StreamTypeDetector
import com.grid.tv.player.StreamTypePreflightSniffer
import com.grid.tv.player.VodStreamResolver
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.data.preferences.PlaybackPreferences
import com.grid.tv.data.sync.CloudSyncProgressUploader
import com.grid.tv.data.sync.WatchProgressSyncPayload
import com.grid.tv.domain.model.VodProgressPolicy
import com.grid.tv.feature.network.introdb.IntroDbClient
import com.grid.tv.feature.network.introdb.IntroSkipWindow
import com.grid.tv.feature.vod.VodNextUpItem
import com.grid.tv.feature.vod.VodNextUpResolver
import com.grid.tv.domain.model.VodPlaybackMeta
import com.grid.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class DirectPlayerViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val vodResumeResolver: VodResumeResolver,
    private val titleEnrichmentRepository: TitleEnrichmentRepository,
    private val profileDao: ProfileDao,
    private val recordedDao: RecordedMediaDao,
    private val subtitleManager: SubtitleManager,
    val pipController: PictureInPictureController,
    private val playerFactory: PlayerFactory,
    private val streamFormatRegistry: IptvStreamFormatRegistry,
    private val streamTypePreflightSniffer: StreamTypePreflightSniffer,
    private val introDbClient: IntroDbClient,
    private val playbackPreferences: PlaybackPreferences,
    private val cloudSyncProgressUploader: CloudSyncProgressUploader
) : ViewModel() {

    companion object {
        private const val TAG = "DirectPlayer"
        /** Navigation / intent extra key for staged resume position (ms). */
        const val RESUME_POSITION_MS_KEY = VodResumeResolver.RESUME_POSITION_MS_KEY
    }

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _activeSubtitle = MutableStateFlow<ActiveSubtitle?>(null)
    val activeSubtitle: StateFlow<ActiveSubtitle?> = _activeSubtitle.asStateFlow()

    private val _recordedMedia = MutableStateFlow<RecordedMediaEntity?>(null)
    val recordedMedia: StateFlow<RecordedMediaEntity?> = _recordedMedia.asStateFlow()

    private var vodMeta: VodPlaybackMeta = VodPlaybackMeta()
    private val _introWindow = MutableStateFlow<IntroSkipWindow?>(null)
    val introWindow: StateFlow<IntroSkipWindow?> = _introWindow.asStateFlow()

    private val _nextUpItem = MutableStateFlow<VodNextUpItem?>(null)
    val nextUpItem: StateFlow<VodNextUpItem?> = _nextUpItem.asStateFlow()

    val nextUpAutoPlay: Boolean get() = playbackPreferences.nextUpAutoPlay

    private var enrichmentRequestedForKey: String? = null

    fun preferredExternalPlayer(): ExternalPlayerId = playbackPreferences.externalPlayer

    fun loadIntroWindow(meta: VodPlaybackMeta, enrichment: com.grid.tv.data.db.entity.TitleEnrichmentEntity?) {
        if (!meta.isSeries) return
        viewModelScope.launch {
            _introWindow.value = introDbClient.lookup(
                tmdbId = enrichment?.tmdbId,
                imdbId = enrichment?.imdbId,
                season = meta.seasonNumber,
                episode = meta.episodeNumber
            )
        }
    }

    fun loadNextUp(meta: VodPlaybackMeta) {
        if (!meta.isSeries || meta.seriesId == null || meta.playlistId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val detail = repository.loadSeriesDetail(meta.playlistId, meta.seriesId)
            _nextUpItem.value = VodNextUpResolver.resolve(meta, detail)
        }
    }

    fun clearNextUp() {
        _nextUpItem.value = null
    }

    fun setVodMetadata(meta: VodPlaybackMeta) {
        vodMeta = meta
        val key = meta.enrichmentSessionKey() ?: return
        if (enrichmentRequestedForKey == key) return
        enrichmentRequestedForKey = key
        viewModelScope.launch {
            titleEnrichmentRepository.enrichFromPlaybackMeta(meta)
            _settings.value = repository.loadSettings()
            loadNextUp(meta)
            val enrichment = titleEnrichmentRepository.getCached(key)
            loadIntroWindow(meta, enrichment)
        }
    }

    private fun VodPlaybackMeta.enrichmentSessionKey(): String? {
        val playlist = playlistId ?: return null
        return when {
            isSeries && seriesId != null -> TitleEnrichmentRepository.xtreamSeriesKey(playlist, seriesId)
            streamId != null -> TitleEnrichmentRepository.xtreamVodKey(playlist, streamId)
            else -> null
        }
    }

    @UnstableApi
    fun createPlayer(context: Context): ExoPlayer {
        val settings = _settings.value
        return playerFactory.create(
            context = context,
            bufferSize = settings.bufferSize,
            preferHardwareDecoding = settings.preferHardwareDecoding,
            startupPriority = PlaybackStartupPriority.FAST,
            networkSettings = settings,
            decoderOwner = "vod_direct",
            preferLiveStability = false,
            onDemandPlayback = true
        )
    }

    fun buildOnDemandMediaItem(
        url: String,
        contentKind: IptvOnDemandContentKind,
        resolvedStream: ResolvedVodStream
    ): MediaItem = IptvOnDemandMediaItem.build(
        url = url,
        contentKind = contentKind,
        registry = streamFormatRegistry,
        formatOverride = resolvedStream.toStreamFormat()
    )

    /**
     * Single VOD entry-point: sniff → resolve final media type → register before ExoPlayer.
     */
    suspend fun resolveVodStream(
        url: String,
        contentKind: IptvOnDemandContentKind,
        title: String? = null,
    ): ResolvedVodStream? {
        streamFormatRegistry.remove(url)

        val detection = when (contentKind) {
            IptvOnDemandContentKind.RECORDING,
            IptvOnDemandContentKind.LOCAL_FILE -> StreamTypeDetector.Detection(
                format = IptvStreamFormat.PROGRESSIVE,
                reason = "local_playback"
            )
            else -> streamTypePreflightSniffer.detectForVod(url)
        }

        val resolved = VodStreamResolver.resolve(url, detection, title) ?: run {
            Log.e(TAG, "VOD resolution blocked url=$url reason=${detection.reason}")
            return null
        }

        resolved.logFinal()
        streamFormatRegistry.put(
            resolved.url,
            resolved.toStreamFormat(),
            IptvStreamFormatRegistry.Source.MANIFEST_SNIFF
        )
        return resolved
    }

    fun releasePlayer(player: ExoPlayer) {
        playerFactory.releasePlayer(player)
    }

    fun attachAutoSubtitles(player: ExoPlayer, playerView: PlayerView?, url: String, title: String) {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            _settings.value = settings
            val meta = vodMeta
            val request = SubtitleRequest(
                mediaUrl = url,
                title = title,
                providerKey = meta.providerKey(),
                isTv = meta.isTv,
                releaseYear = parseYear(title)
            )
            _activeSubtitle.value = subtitleManager.attachAutoSubtitles(
                player = player,
                playerView = playerView,
                request = request,
                settings = settings
            )
        }
    }

    fun applySubtitleStyle(playerView: PlayerView?, settings: AppSettings) {
        subtitleManager.applyStyle(playerView, settings)
    }

    fun updateSubtitleSettings(
        enabled: Boolean? = null,
        language: String? = null,
        fontSize: SubtitleFontSize? = null,
        position: SubtitlePosition? = null,
        delayMs: Long? = null,
        player: ExoPlayer? = null,
        playerView: PlayerView? = null,
        url: String? = null,
        title: String? = null
    ) {
        viewModelScope.launch {
            val current = repository.loadSettings()
            val updated = current.copy(
                subtitlesEnabled = enabled ?: current.subtitlesEnabled,
                subtitleLanguage = language ?: current.subtitleLanguage,
                subtitleFontSize = fontSize ?: current.subtitleFontSize,
                subtitlePosition = position ?: current.subtitlePosition,
                subtitleDelayMs = delayMs ?: current.subtitleDelayMs
            )
            repository.saveSettings(updated)
            _settings.value = updated
            subtitleManager.applyStyle(playerView, updated)
            player?.let { exo ->
                if (!updated.subtitlesEnabled) {
                    subtitleManager.setEnabled(exo, false)
                } else if (url != null && title != null) {
                    attachAutoSubtitles(exo, playerView, url, title)
                }
            }
        }
    }

    private fun VodPlaybackMeta.providerKey(): String? {
        val playlist = playlistId ?: return null
        return when {
            isSeries && seriesId != null -> TitleEnrichmentRepository.xtreamSeriesKey(playlist, seriesId)
            streamId != null -> TitleEnrichmentRepository.xtreamVodKey(playlist, streamId)
            else -> null
        }
    }

    private fun parseYear(title: String): Int? {
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(title) ?: return null
        return match.value.toIntOrNull()
    }

    fun loadRecordedMedia(recordingId: Long) {
        if (recordingId <= 0L) {
            _recordedMedia.value = null
            return
        }
        viewModelScope.launch {
            _recordedMedia.value = recordedDao.getById(recordingId)
        }
    }

    suspend fun resolveResumePositionMs(
        meta: VodPlaybackMeta,
        streamId: Long?,
        navigationResumeMs: Long = 0L,
        stagedResumeMs: Long = 0L
    ): Long = vodResumeResolver.resolveForPlayback(
        meta = meta,
        streamId = streamId,
        navigationResumeMs = navigationResumeMs,
        stagedResumeMs = stagedResumeMs
    )

    /** @deprecated Prefer [resolveResumePositionMs] with [VodPlaybackMeta]. */
    suspend fun resumePositionMs(streamId: Long?, url: String, resume: Boolean): Long {
        if (!resume) return 0L
        return resolveResumePositionMs(
            meta = vodMeta,
            streamId = streamId
        )
    }

    fun persistProgress(streamId: Long?, positionMs: Long, title: String, durationMs: Long, streamUrl: String) {
        val id = streamId ?: vodMeta.streamId ?: return
        if (positionMs <= 0L) return
        viewModelScope.launch {
            val profileId = withContext(Dispatchers.IO) { repository.activeProfileId() }
            val meta = vodMeta
            val playlistId = meta.playlistId ?: 0L
            if (meta.isSeries && meta.seriesId != null && meta.seasonNumber != null && meta.episodeNumber != null) {
                continueWatchingRepository.saveSeriesEpisode(
                    profileId = profileId,
                    playlistId = playlistId,
                    seriesId = meta.seriesId,
                    seasonNumber = meta.seasonNumber,
                    episodeNumber = meta.episodeNumber,
                    streamId = id,
                    title = title,
                    posterUrl = meta.posterUrl,
                    streamUrl = streamUrl,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            } else {
                continueWatchingRepository.saveMovie(
                    profileId = profileId,
                    playlistId = playlistId,
                    streamId = id,
                    title = title,
                    posterUrl = meta.posterUrl,
                    streamUrl = streamUrl,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            }
            repository.saveVodWatchPosition(id, positionMs, title, durationMs, playlistId)
            val contentKey = if (meta.isSeries && meta.seriesId != null && meta.seasonNumber != null && meta.episodeNumber != null) {
                ContinueWatchingRepository.seriesContentKey(
                    playlistId, meta.seriesId, meta.seasonNumber, meta.episodeNumber
                )
            } else {
                ContinueWatchingRepository.movieContentKey(playlistId, id)
            }
            cloudSyncProgressUploader.uploadIfSignedIn(
                profileId,
                WatchProgressSyncPayload(
                    contentKey = contentKey,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    watchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun isWatched(positionMs: Long, durationMs: Long): Boolean =
        VodProgressPolicy.isWatched(positionMs, durationMs)

    fun shouldOfferNextUp(positionMs: Long, durationMs: Long): Boolean =
        VodProgressPolicy.shouldOfferNextUp(positionMs, durationMs)

    fun saveRecordingPosition(recordingId: Long, positionMs: Long) {
        if (recordingId <= 0L) return
        viewModelScope.launch {
            recordedDao.updatePlaybackPosition(recordingId, positionMs.coerceAtLeast(0L))
        }
    }

    fun deleteRecording(recordingId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            val media = recordedDao.getById(recordingId) ?: return@launch
            recordedDao.deleteById(recordingId)
            kotlin.runCatching { java.io.File(media.filePath).delete() }
            media.thumbnailPath?.let { kotlin.runCatching { java.io.File(it).delete() } }
            onComplete()
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            if (settings.recordedPlaybackSpeed != speed) {
                repository.saveSettings(settings.copy(recordedPlaybackSpeed = speed))
            }
        }
    }

    suspend fun loadPlaybackSpeed(): Float =
        repository.loadSettings().recordedPlaybackSpeed.coerceIn(0.5f, 2f).let { if (it <= 0f) 1f else it }
}
