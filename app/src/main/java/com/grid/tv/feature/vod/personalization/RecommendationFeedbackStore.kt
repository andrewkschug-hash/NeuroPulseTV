package com.grid.tv.feature.vod.personalization

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class RecommendationVote {
    UP,
    DOWN
}

/**
 * Lightweight per-profile thumbs up/down for recommendation tuning.
 * Stored in SharedPreferences to avoid a schema migration.
 */
@Singleton
class RecommendationFeedbackStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun vote(profileId: Long, contentKey: String, vote: RecommendationVote) {
        prefs.edit()
            .putString(key(profileId, contentKey), vote.name)
            .apply()
    }

    fun voteFor(profileId: Long, contentKey: String): RecommendationVote? =
        prefs.getString(key(profileId, contentKey), null)?.let {
            runCatching { RecommendationVote.valueOf(it) }.getOrNull()
        }

    fun scoreBoost(profileId: Long, contentKey: String): Float = when (voteFor(profileId, contentKey)) {
        RecommendationVote.UP -> 1.25f
        RecommendationVote.DOWN -> 0.4f
        null -> 1f
    }

    fun clearProfile(profileId: Long) {
        val prefix = "p${profileId}_"
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }.apply()
    }

    private fun key(profileId: Long, contentKey: String) = "p${profileId}_$contentKey"

    companion object {
        private const val PREFS_NAME = "recommendation_feedback"
    }
}
