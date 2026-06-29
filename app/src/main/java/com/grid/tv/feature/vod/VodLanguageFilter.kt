package com.grid.tv.feature.vod

import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodItem

/** Single source of truth for VOD language filtering options. */
data class VodLanguageFilterOptions(
    val preferredLanguages: Set<String> = emptySet(),
    val includeUntagged: Boolean = true
) {
    val isActive: Boolean get() = preferredLanguages.isNotEmpty()
}

fun vodContentLanguageCode(title: String, categoryName: String? = null): String? =
    parseVodContentLanguageCode(title) ?: categoryName?.let { parseVodContentLanguageCode(it) }

/** Regional codes that IPTV providers use interchangeably with base language codes. */
internal val VOD_LANGUAGE_EQUIVALENTS: Map<String, Set<String>> = mapOf(
    "EN" to setOf("EN", "US", "UK", "GB", "AU", "CA", "NZ", "IE"),
)

internal fun expandPreferredLanguageCodes(preferred: Set<String>): Set<String> {
    val expanded = mutableSetOf<String>()
    preferred.forEach { code ->
        val upper = code.trim().uppercase()
        if (upper.isEmpty()) return@forEach
        expanded += upper
        VOD_LANGUAGE_EQUIVALENTS[upper]?.let { expanded += it }
    }
    return expanded
}

/**
 * Hybrid language filter:
 * - Explicit matching tag → include
 * - Explicit non-matching tag → exclude
 * - No tag → include only when [includeUntagged] is true
 */
fun matchesVodLanguageFilter(
    title: String,
    preferredLanguages: Set<String>,
    categoryName: String? = null,
    includeUntagged: Boolean = true
): Boolean {
    if (preferredLanguages.isEmpty()) return true
    val normalized = expandPreferredLanguageCodes(preferredLanguages)
    val code = vodContentLanguageCode(title, categoryName)
    return when {
        code == null -> includeUntagged
        code.uppercase() in normalized -> true
        else -> false
    }
}

fun matchesVodLanguageFilter(
    title: String,
    options: VodLanguageFilterOptions,
    categoryName: String? = null
): Boolean = matchesVodLanguageFilter(
    title = title,
    preferredLanguages = options.preferredLanguages,
    categoryName = categoryName,
    includeUntagged = options.includeUntagged
)

fun VodItem.matchesLanguageFilter(
    options: VodLanguageFilterOptions,
    categoryNames: Map<String, String> = emptyMap()
): Boolean = matchesVodLanguageFilter(
    title = title,
    options = options,
    categoryName = categoryId?.let { categoryNames[it] }
)

fun VodItem.matchesLanguageFilter(
    preferredLanguages: Set<String>,
    categoryNames: Map<String, String> = emptyMap(),
    includeUntagged: Boolean = true
): Boolean = matchesLanguageFilter(
    VodLanguageFilterOptions(preferredLanguages, includeUntagged),
    categoryNames
)

fun SeriesShow.matchesLanguageFilter(
    options: VodLanguageFilterOptions,
    categoryNames: Map<String, String> = emptyMap()
): Boolean = matchesVodLanguageFilter(
    title = name,
    options = options,
    categoryName = seriesLanguageCategoryName(categoryNames)
)

private fun SeriesShow.seriesLanguageCategoryName(categoryNames: Map<String, String>): String? =
    categoryId?.let { categoryNames[it] } ?: genre?.takeIf { it.isNotBlank() }

fun SeriesShow.matchesLanguageFilter(
    preferredLanguages: Set<String>,
    categoryNames: Map<String, String> = emptyMap(),
    includeUntagged: Boolean = true
): Boolean = matchesLanguageFilter(
    VodLanguageFilterOptions(preferredLanguages, includeUntagged),
    categoryNames
)

fun ContinueWatchingItem.matchesLanguageFilter(options: VodLanguageFilterOptions): Boolean =
    matchesVodLanguageFilter(title, options)

fun ContinueWatchingItem.matchesLanguageFilter(
    preferredLanguages: Set<String>,
    includeUntagged: Boolean = true
): Boolean = matchesLanguageFilter(
    VodLanguageFilterOptions(preferredLanguages, includeUntagged)
)

fun filterBrowseRows(
    rows: List<VodBrowseRow>,
    options: VodLanguageFilterOptions,
    movieCategoryNames: Map<String, String> = emptyMap(),
    seriesCategoryNames: Map<String, String> = emptyMap()
): List<VodBrowseRow> {
    if (!options.isActive) return rows
    return rows.mapNotNull { row ->
        val movies = row.movies.filter { it.matchesLanguageFilter(options, movieCategoryNames) }
        val series = row.series.filter { it.matchesLanguageFilter(options, seriesCategoryNames) }
        row.copy(movies = movies, series = series).takeUnless { it.isEmpty }
    }
}

fun filterBrowseRows(
    rows: List<VodBrowseRow>,
    preferredLanguages: Set<String>,
    movieCategoryNames: Map<String, String> = emptyMap(),
    seriesCategoryNames: Map<String, String> = emptyMap(),
    includeUntagged: Boolean = true
): List<VodBrowseRow> = filterBrowseRows(
    rows = rows,
    options = VodLanguageFilterOptions(preferredLanguages, includeUntagged),
    movieCategoryNames = movieCategoryNames,
    seriesCategoryNames = seriesCategoryNames
)
