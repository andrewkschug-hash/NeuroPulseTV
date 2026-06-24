package com.grid.tv.feature.vod.personalization

import com.grid.tv.data.db.dao.SeriesFollowDao
import com.grid.tv.data.db.dao.VodWatchEventDao
import com.grid.tv.data.db.entity.SeriesFollowEntity
import com.grid.tv.data.db.entity.VodWatchEventEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodeDetectorTest {
    private val watchDao = mockk<VodWatchEventDao>()
    private val followDao = mockk<SeriesFollowDao>()
    private val watchTracker = VodWatchTracker(watchDao)
    private val followManager = SeriesFollowManager(followDao, watchDao)
    private val detector = NewEpisodeDetector(watchTracker, followManager)

    @Test
    fun loveIsland_episode38DetectedAfter37() = runTest {
        val profileId = 1L
        val seriesId = 42L
        coEvery { followDao.followedForProfile(profileId) } returns listOf(
            SeriesFollowEntity(
                profileId = profileId,
                seriesId = seriesId,
                seriesTitle = "Love Island",
                playlistId = 1,
                following = true,
                autoFollowed = false,
                followedAt = 0
            )
        )
        coEvery { watchDao.latestForSeries(profileId, 1L, seriesId) } returns VodWatchEventEntity(
            profileId = profileId,
            contentId = "series:1:42:1:37",
            contentType = "EPISODE",
            playlistId = 1L,
            seriesId = seriesId,
            seasonNumber = 1,
            episodeNumber = 37,
            progressPercent = 1f,
            positionMs = 3_600_000,
            durationMs = 3_600_000,
            lastWatched = 1
        )

        val detections = detector.detectForProfile(
            profileId,
            listOf(
                CatalogEpisode(
                    playlistId = 1,
                    seriesId = seriesId,
                    seriesTitle = "Love Island",
                    seasonNumber = 1,
                    episodeNumber = 38,
                    episodeId = 38001,
                    episodeTitle = "Episode 38",
                    addedAt = System.currentTimeMillis()
                )
            )
        )

        assertEquals(1, detections.size)
        assertEquals(38, detections.first().episodeNumber)
        assertEquals(37, detections.first().previousWatchedEpisode)
        assertTrue(detector.isSequentialNewEpisode(37, detections.first().let {
            CatalogEpisode(1, seriesId, "Love Island", 1, 38, 38001, "Episode 38", 0)
        }))
    }
}
