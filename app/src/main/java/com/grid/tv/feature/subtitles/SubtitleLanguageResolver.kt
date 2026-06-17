package com.grid.tv.feature.subtitles

import java.util.Locale

object SubtitleLanguageResolver {
    fun priorityLanguages(userPreferred: String, deviceLocale: Locale = Locale.getDefault()): List<String> {
        val ordered = linkedSetOf<String>()
        normalizeCode(userPreferred)?.let { ordered.add(it) }
        normalizeCode(deviceLocale.language)?.let { ordered.add(it) }
        ordered.add("en")
        return ordered.toList()
    }

    fun pickBestLanguage(available: Collection<String>, priorities: List<String>): String? {
        if (available.isEmpty()) return null
        val normalizedAvailable = available.map { normalizeCode(it) ?: it.lowercase() }
        for (priority in priorities) {
            val matchIndex = normalizedAvailable.indexOfFirst { candidate ->
                candidate == priority || candidate.startsWith(priority)
            }
            if (matchIndex >= 0) return available.elementAt(matchIndex)
        }
        return available.firstOrNull()
    }

    fun normalizeCode(raw: String?): String? {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return null
        return value.substringBefore('-').substringBefore('_').take(3).ifBlank { null }
    }
}
