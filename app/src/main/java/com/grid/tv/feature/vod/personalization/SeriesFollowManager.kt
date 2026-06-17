package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.SeriesFollowDao
import com.grid.tv.data.db.dao.VodWatchEventDao
import com.grid.tv.data.db.entity.SeriesFollowEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesFollowManager @Inject constructor(
    private val followDao: SeriesFollowDao,
    private val watchEventDao: VodWatchEventDao
) {
    companion object {
        const val AUTO_FOLLOW_EPISODE_THRESHOLD = 3
    }

    suspend fun followSeries(
        profileId: Long,
        seriesId: Long,
        seriesTitle: String,
        playlistId: Long,
        autoFollowed: Boolean = false
    ) {
        followDao.upsert(
            SeriesFollowEntity(
                profileId = profileId,
                seriesId = seriesId,
                seriesTitle = seriesTitle,
                playlistId = playlistId,
                following = true,
                autoFollowed = autoFollowed,
                followedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun unfollowSeries(profileId: Long, seriesId: Long) {
        val existing = followDao.get(profileId, seriesId) ?: return
        followDao.upsert(existing.copy(following = false))
    }

    suspend fun isFollowing(profileId: Long, seriesId: Long): Boolean =
        followDao.get(profileId, seriesId)?.following == true

    suspend fun getFollowedSeries(profileId: Long): List<SeriesFollow> =
        followDao.followedForProfile(profileId).map { it.toDomain() }

    suspend fun evaluateAutoFollow(profileId: Long, seriesId: Long, seriesTitle: String, playlistId: Long) {
        if (isFollowing(profileId, seriesId)) return
        val counts = watchEventDao.seriesWithMinEpisodes(profileId, AUTO_FOLLOW_EPISODE_THRESHOLD)
        val qualifies = counts.any { it.seriesId == seriesId }
        if (qualifies) {
            followSeries(profileId, seriesId, seriesTitle, playlistId, autoFollowed = true)
        }
    }

    private fun SeriesFollowEntity.toDomain() = SeriesFollow(
        profileId = profileId,
        seriesId = seriesId,
        seriesTitle = seriesTitle,
        playlistId = playlistId,
        following = following,
        autoFollowed = autoFollowed,
        followedAt = followedAt
    )
}
