package com.grid.tv.feature.vod

import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodItem

fun vodContentLanguageCode(title: String, categoryName: String? = null): String? =
    parseVodContentLanguageCode(title) ?: categoryName?.let { parseVodContentLanguageCode(it) }

fun matchesVodLanguageFilter(
    title: String,
    preferredLanguages: Set<String>,
    categoryName: String? = null
): Boolean {
    if (preferredLanguages.isEmpty()) return true
    val code = vodContentLanguageCode(title, categoryName) ?: return false
    return code.uppercase() in preferredLanguages.map { it.uppercase() }.toSet()
}

fun VodItem.matchesLanguageFilter(
    preferredLanguages: Set<String>,
    categoryNames: Map<String, String> = emptyMap()
): Boolean = matchesVodLanguageFilter(
    title = title,
    preferredLanguages = preferredLanguages,
    categoryName = categoryId?.let { categoryNames[it] }
)

fun SeriesShow.matchesLanguageFilter(
    preferredLanguages: Set<String>,
    categoryNames: Map<String, String> = emptyMap()
): Boolean = matchesVodLanguageFilter(
    title = name,
    preferredLanguages = preferredLanguages,
    categoryName = categoryId?.let { categoryNames[it] }
)

fun ContinueWatchingItem.matchesLanguageFilter(preferredLanguages: Set<String>): Boolean =
    matchesVodLanguageFilter(title, preferredLanguages)

fun filterBrowseRows(
    rows: List<VodBrowseRow>,
    preferredLanguages: Set<String>,
    movieCategoryNames: Map<String, String> = emptyMap(),
    seriesCategoryNames: Map<String, String> = emptyMap()
): List<VodBrowseRow> {
    if (preferredLanguages.isEmpty()) return rows
    return rows.mapNotNull { row ->
        val movies = row.movies.filter { it.matchesLanguageFilter(preferredLanguages, movieCategoryNames) }
        val series = row.series.filter { it.matchesLanguageFilter(preferredLanguages, seriesCategoryNames) }
        row.copy(movies = movies, series = series).takeUnless { it.isEmpty }
    }
}
