package com.neuropulse.tv.feature.recommendation

import com.neuropulse.tv.domain.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationEngineTest {
    @Test
    fun topRecommendation_prefersFrequencyAndHourMatch() {
        val channels = listOf(
            Channel(id = 1, number = 1, name = "News 1", group = "News", logoUrl = null, epgId = "n1", streamUrl = "u1", backupStreamUrl = null, playlistId = 1, playlistName = null, isFavorite = false),
            Channel(id = 2, number = 2, name = "Sports 2", group = "Sports", logoUrl = null, epgId = "s2", streamUrl = "u2", backupStreamUrl = null, playlistId = 1, playlistName = null, isFavorite = false)
        )

        val stats = mapOf(
            1L to WatchStat(sessions = 2, avgHour = 9, genreHint = "NEWS"),
            2L to WatchStat(sessions = 8, avgHour = 10, genreHint = "SPORTS")
        )

        val ranked = RecommendationEngine().score(channels, stats, currentHour = 10, favoriteGenre = "SPORTS")
        assertEquals(2L, ranked.first().channel.id)
    }
}
