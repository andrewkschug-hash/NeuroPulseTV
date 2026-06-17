package com.grid.tv.feature.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("unified_search", Context.MODE_PRIVATE)

    fun recentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT, "") ?: return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val updated = (listOf(trimmed) + recentSearches().filter { !it.equals(trimmed, ignoreCase = true) })
            .take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT, updated.joinToString(DELIMITER)).apply()
    }

    fun clearRecent() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    companion object {
        private const val KEY_RECENT = "recent_queries"
        private const val DELIMITER = "\u001F"
        private const val MAX_RECENT = 12

        val DEFAULT_TRENDING = listOf(
            "ESPN",
            "Sports",
            "Movies",
            "NFL",
            "News",
            "Comedy"
        )
    }
}
