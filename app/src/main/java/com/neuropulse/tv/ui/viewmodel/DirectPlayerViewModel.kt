package com.neuropulse.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuropulse.tv.data.db.dao.ProfileDao
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import com.neuropulse.tv.data.repository.ContinueWatchingRepository
import com.neuropulse.tv.player.PictureInPictureController
import com.neuropulse.tv.domain.model.VodPlaybackMeta
import com.neuropulse.tv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DirectPlayerViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val profileDao: ProfileDao,
    private val recordedDao: RecordedMediaDao,
    val pipController: PictureInPictureController
) : ViewModel() {

    private val _recordedMedia = MutableStateFlow<RecordedMediaEntity?>(null)
    val recordedMedia: StateFlow<RecordedMediaEntity?> = _recordedMedia.asStateFlow()

    private var vodMeta: VodPlaybackMeta = VodPlaybackMeta()

    fun setVodMetadata(meta: VodPlaybackMeta) {
        vodMeta = meta
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

    suspend fun resumePositionMs(streamId: Long?, url: String, resume: Boolean): Long {
        if (!resume) return 0L
        val profileId = profileDao.activeProfile()?.profileId ?: return 0L
        streamId?.let { id ->
            continueWatchingRepository.resumePositionForStream(profileId, id)?.let { return it }
        }
        return 0L
    }

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
