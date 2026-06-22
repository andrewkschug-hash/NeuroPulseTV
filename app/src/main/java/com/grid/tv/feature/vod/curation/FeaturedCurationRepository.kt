package com.grid.tv.feature.vod.curation

import com.grid.tv.data.db.dao.FeaturedCurationDao
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.entity.FeaturedBannerStatsEntity
import com.grid.tv.data.db.entity.ProfileGenreAffinityEntity
import com.grid.tv.domain.model.VodItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class FeaturedCurationRepository @Inject constructor(
    private val dao: FeaturedCurationDao,
    private val profileDao: ProfileDao
) {
    suspend fun activeProfileId(): Long? = profileDao.activeProfile()?.profileId

    fun observeActiveProfileId(): Flow<Long?> = profileDao.observeActiveProfileId()

    suspend fun genreAffinities(profileId: Long): Map<String, Int> = withContext(Dispatchers.IO) {
        dao.genreAffinitiesForProfile(profileId).associate { it.genre to it.score }
    }

    suspend fun bannerStats(profileId: Long): Map<String, FeaturedBannerStatsEntity> = withContext(Dispatchers.IO) {
        dao.bannerStatsForProfile(profileId).associateBy { it.contentKey }
    }

    suspend fun recordGenreSelection(profileId: Long, genres: Collection<String>) {
        if (genres.isEmpty()) return
        withContext(Dispatchers.IO) {
            genres.map { it.trim().lowercase() }
                .filter { it.length >= 2 }
                .distinct()
                .forEach { genre ->
                    val existing = dao.genreAffinity(profileId, genre)
                    dao.upsertGenreAffinity(
                        ProfileGenreAffinityEntity(
                            profileId = profileId,
                            genre = genre,
                            score = (existing?.score ?: 0) + 1
                        )
                    )
                }
        }
    }

    suspend fun recordBannerImpression(profileId: Long, item: VodItem) {
        withContext(Dispatchers.IO) {
            val key = contentKey(item)
            val existing = dao.bannerStats(profileId, key)
            dao.upsertBannerStats(
                FeaturedBannerStatsEntity(
                    profileId = profileId,
                    contentKey = key,
                    impressionCount = (existing?.impressionCount ?: 0) + 1,
                    clickCount = existing?.clickCount ?: 0,
                    lastShownAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun recordBannerClick(profileId: Long, item: VodItem) {
        withContext(Dispatchers.IO) {
            val key = contentKey(item)
            val existing = dao.bannerStats(profileId, key)
            dao.upsertBannerStats(
                FeaturedBannerStatsEntity(
                    profileId = profileId,
                    contentKey = key,
                    impressionCount = existing?.impressionCount ?: 0,
                    clickCount = (existing?.clickCount ?: 0) + 1,
                    lastShownAt = existing?.lastShownAt ?: System.currentTimeMillis()
                )
            )
        }
    }

    fun contentKey(item: VodItem): String = "${item.playlistId}_${item.streamId}"
}
