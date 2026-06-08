package com.neuropulse.tv.feature.sports

import java.util.Locale

class SportsDetector {
    private val keywords = listOf(
        "match", "live", "vs", "game", "sport", "football", "soccer", "basketball", "hockey", "baseball", "ufc", "nfl", "nba", "nhl", "mlb", "epl"
    )

    fun isSports(title: String, description: String?): Boolean {
        val haystack = (title + " " + (description ?: "")).lowercase(Locale.US)
        return keywords.any { it in haystack }
    }
}
