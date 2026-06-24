package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.data.repository.ContinueWatchingRepository
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodPlaybackMeta
import javax.inject.Inject
import javax.inject.Singleton

data class StoredResumeProgress(
    val positionMs: Long,
    val durationMs: Long
)

/**
 * Single source of truth for VOD resume position before playback starts.
 * Explicit navigation/staged values win; otherwise reads continue-watching storage.
 */
@Singleton
class VodResumeResolver @Inject constructor(
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val profileDao: ProfileDao
) {
    companion object {
        private const val TAG = "VodResumeResolver"
        const val RESUME_POSITION_MS_KEY = "RESUME_POSITION"
        const val MIN_RESUME_MS = 5_000L

        fun applyThreshold(positionMs: Long, durationMs: Long): Long {
            if (positionMs <= MIN_RESUME_MS) return 0L
            if (durationMs <= 0L) return positionMs
            val fraction = positionMs.toDouble() / durationMs.toDouble()
            return if (fraction >= ContinueWatchingRepository.COMPLETION_THRESHOLD) 0L else positionMs
        }
    }

    fun fromContinueWatchingItem(item: ContinueWatchingItem): Long =
        applyThreshold(item.positionMs, item.durationMs)

    suspend fun resolveForPlayback(
        meta: VodPlaybackMeta,
        streamId: Long?,
        navigationResumeMs: Long = 0L,
        stagedResumeMs: Long = 0L
    ): Long {
        if (navigationResumeMs > 0L) {
            Log.d(TAG, "$RESUME_POSITION_MS_KEY=$navigationResumeMs (navigation)")
            return navigationResumeMs
        }
        if (stagedResumeMs > 0L) {
            Log.d(TAG, "$RESUME_POSITION_MS_KEY=$stagedResumeMs (staged)")
            return stagedResumeMs
        }
        val profileId = profileDao.activeProfile()?.profileId
        if (profileId == null) {
            Log.w(TAG, "$RESUME_POSITION_MS_KEY=0 — no active profile")
            return 0L
        }
        val playlistId = meta.playlistId ?: 0L
        if (meta.isSeries &&
            meta.seriesId != null &&
            meta.seasonNumber != null &&
            meta.episodeNumber != null
        ) {
            continueWatchingRepository.storedResumeForSeriesEpisode(
                profileId = profileId,
                playlistId = playlistId,
                seriesId = meta.seriesId,
                seasonNumber = meta.seasonNumber,
                episodeNumber = meta.episodeNumber
            )?.let { stored ->
                val ms = applyThreshold(stored.positionMs, stored.durationMs)
                Log.d(
                    TAG,
                    "$RESUME_POSITION_MS_KEY=$ms " +
                        "(series episode playlist=$playlistId series=${meta.seriesId})"
                )
                return ms
            }
        }
        val resolvedStreamId = streamId ?: meta.streamId
        if (resolvedStreamId != null) {
            continueWatchingRepository.storedResumeForStream(
                profileId = profileId,
                playlistId = playlistId,
                streamId = resolvedStreamId
            )?.let { stored ->
                val ms = applyThreshold(stored.positionMs, stored.durationMs)
                Log.d(
                    TAG,
                    "$RESUME_POSITION_MS_KEY=$ms " +
                        "(movie streamId=$resolvedStreamId playlist=$playlistId)"
                )
                return ms
            }
        }
        Log.d(
            TAG,
            "$RESUME_POSITION_MS_KEY=0 — no stored resume " +
                "(streamId=$resolvedStreamId playlist=$playlistId)"
        )
        return 0L
    }
}
