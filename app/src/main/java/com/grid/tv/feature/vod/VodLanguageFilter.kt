package com.grid.tv.feature.vod

import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodItem
import com.grid.tv.ui.component.parseVodLanguageBadge
import java.util.Locale

fun vodContentLanguageCode(title: String): String? = parseVodLanguageBadge(title)

fun matchesVodLanguageFilter(title: String, preferredLanguages: Set<String>): Boolean {
    if (preferredLanguages.isEmpty()) return true
    val code = vodContentLanguageCode(title) ?: return false
    return code.uppercase() in preferredLanguages.map { it.uppercase() }.toSet()
}

fun VodItem.matchesLanguageFilter(preferredLanguages: Set<String>): Boolean =
    matchesVodLanguageFilter(title, preferredLanguages)

fun SeriesShow.matchesLanguageFilter(preferredLanguages: Set<String>): Boolean =
    matchesVodLanguageFilter(name, preferredLanguages)

fun ContinueWatchingItem.matchesLanguageFilter(preferredLanguages: Set<String>): Boolean =
    matchesVodLanguageFilter(title, preferredLanguages)

fun filterBrowseRows(rows: List<VodBrowseRow>, preferredLanguages: Set<String>): List<VodBrowseRow> {
    if (preferredLanguages.isEmpty()) return rows
    return rows.mapNotNull { row ->
        val movies = row.movies.filter { it.matchesLanguageFilter(preferredLanguages) }
        val series = row.series.filter { it.matchesLanguageFilter(preferredLanguages) }
        row.copy(movies = movies, series = series).takeUnless { it.isEmpty }
    }
}

fun discoverLanguagesFromTitles(titles: Sequence<String>): List<String> =
    titles.mapNotNull { vodContentLanguageCode(it) }
        .distinctBy { it.uppercase() }
        .sortedBy { displayLanguageName(it).lowercase() }
        .toList()

fun displayLanguageName(code: String): String {
    val locale = Locale.forLanguageTag(code.lowercase())
    val name = locale.getDisplayLanguage(Locale.ENGLISH)
    return name.takeIf { it.isNotBlank() && !name.equals(code, ignoreCase = true) } ?: code.uppercase()
}
