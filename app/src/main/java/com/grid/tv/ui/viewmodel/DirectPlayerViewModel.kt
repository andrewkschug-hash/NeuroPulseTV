package com.grid.tv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.RecordedMediaDao
import com.grid.tv.data.db.entity.RecordedMediaEntity
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.feature.subtitles.ActiveSubtitle
import com.grid.tv.feature.subtitles.SubtitleManager
import com.grid.tv.feature.subtitles.SubtitleRequest
import com.grid.tv.player.PictureInPictureController
import com.grid.tv.player.PlaybackStartupPriority
import com.grid.tv.player.PlayerFactory
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
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

@HiltViewModel
class DirectPlayerViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val titleEnrichmentRepository: TitleEnrichmentRepository,
    private val profileDao: ProfileDao,
    private val recordedDao: RecordedMediaDao,
    private val subtitleManager: SubtitleManager,
    val pipController: PictureInPictureController,
    private val playerFactory: PlayerFactory
) : ViewModel() {

    companion object {
        private const val TAG = "DirectPlayer"
        /** Navigation / intent extra key for staged resume position (ms). */
        const val RESUME_POSITION_MS_KEY = "RESUME_POSITION"
    }

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _activeSubtitle = MutableStateFlow<ActiveSubtitle?>(null)
    val activeSubtitle: StateFlow<ActiveSubtitle?> = _activeSubtitle.asStateFlow()

    private val _recordedMedia = MutableStateFlow<RecordedMediaEntity?>(null)
    val recordedMedia: StateFlow<RecordedMediaEntity?> = _recordedMedia.asStateFlow()

    private var vodMeta: VodPlaybackMeta = VodPlaybackMeta()

    fun setVodMetadata(meta: VodPlaybackMeta) {
        vodMeta = meta
        viewModelScope.launch {
            titleEnrichmentRepository.enrichFromPlaybackMeta(meta)
            _settings.value = repository.loadSettings()
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
            decoderOwner = "vod_direct"
        )
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
        streamId: Long?,
        url: String,
        resume: Boolean,
        navigationResumeMs: Long = 0L,
        stagedResumeMs: Long = 0L
    ): Long {
        if (!resume) {
            Log.d(TAG, "$RESUME_POSITION_MS_KEY=0 (resume disabled)")
            return 0L
        }
        when {
            navigationResumeMs > 0L -> {
                Log.d(TAG, "$RESUME_POSITION_MS_KEY=$navigationResumeMs (navigation extra)")
                return navigationResumeMs
            }
            stagedResumeMs > 0L -> {
                Log.d(TAG, "$RESUME_POSITION_MS_KEY=$stagedResumeMs (staged playback context)")
                return stagedResumeMs
            }
        }
        val profileId = profileDao.activeProfile()?.profileId
        if (profileId == null) {
            Log.w(TAG, "$RESUME_POSITION_MS_KEY missing — no active profile for DB lookup")
            return 0L
        }
        val meta = vodMeta
        if (meta.isSeries && meta.seriesId != null && meta.seasonNumber != null && meta.episodeNumber != null) {
            continueWatchingRepository.resumePositionForSeriesEpisode(
                profileId = profileId,
                seriesId = meta.seriesId,
                seasonNumber = meta.seasonNumber,
                episodeNumber = meta.episodeNumber
            )?.let { ms ->
                Log.d(TAG, "$RESUME_POSITION_MS_KEY=$ms (database series episode)")
                return ms
            }
        }
        streamId?.let { id ->
            continueWatchingRepository.resumePositionForStream(profileId, id)?.let { ms ->
                Log.d(TAG, "$RESUME_POSITION_MS_KEY=$ms (database streamId=$id)")
                return ms
            }
        }
        Log.w(
            TAG,
            "$RESUME_POSITION_MS_KEY=0 fallback — no navigation, staged, or database value " +
                "(streamId=$streamId url=$url)"
        )
        return 0L
    }

    /** @deprecated Prefer [resolveResumePositionMs] with explicit navigation/staged values. */
    suspend fun resumePositionMs(streamId: Long?, url: String, resume: Boolean): Long =
        resolveResumePositionMs(streamId = streamId, url = url, resume = resume)

    fun persistProgress(streamId: Long?, positionMs: Long, title: String, durationMs: Long, streamUrl: String) {
        val id = streamId ?: vodMeta.streamId ?: return
        if (positionMs <= 0L) return
        viewModelScope.launch {
            val profileId = profileDao.activeProfile()?.profileId ?: return@launch
            val meta = vodMeta
            if (meta.isSeries && meta.seriesId != null && meta.seasonNumber != null && meta.episodeNumber != null) {
                continueWatchingRepository.saveSeriesEpisode(
                    profileId = profileId,
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
                    streamId = id,
                    title = title,
                    posterUrl = meta.posterUrl,
                    streamUrl = streamUrl,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            }
            repository.saveVodWatchPosition(id, positionMs, title, durationMs)
        }
    }

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
