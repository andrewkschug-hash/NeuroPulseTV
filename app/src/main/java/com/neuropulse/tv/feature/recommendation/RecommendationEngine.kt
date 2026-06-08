package com.neuropulse.tv.feature.recommendation

import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.Recommendation

class RecommendationEngine {
    fun score(
        channels: List<Channel>,
        watchStats: Map<Long, WatchStat>,
        currentHour: Int,
        favoriteGenre: String?
    ): List<Recommendation> {
        return channels.map { channel ->
            val stat = watchStats[channel.id]
            val frequency = (stat?.sessions ?: 0) * 1.5
            val hourMatch = if (stat != null && kotlin.math.abs(stat.avgHour - currentHour) <= 2) 25.0 else 0.0
            val genreBonus = if (favoriteGenre != null && stat?.genreHint == favoriteGenre) 15.0 else 0.0
            val total = frequency + hourMatch + genreBonus
            val reason = if (hourMatch > 0) "You usually watch ${channel.name} around this time" else "Popular in your watch history"
            Recommendation(channel, total, reason)
        }.sortedByDescending { it.score }
    }
}

data class WatchStat(
    val sessions: Int,
    val avgHour: Int,
    val genreHint: String?
)
