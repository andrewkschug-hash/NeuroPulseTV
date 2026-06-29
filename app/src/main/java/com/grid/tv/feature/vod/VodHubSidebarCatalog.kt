package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryGuards
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.parseCategoryBrowseRowId

internal fun categoryItemCountsFromBrowseRows(rows: List<VodBrowseRow>): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    rows.forEach { row ->
        val (playlistId, categoryId) = parseCategoryBrowseRowId(row.id) ?: return@forEach
        val key = VodCategoryNameResolver.categoryKey(playlistId, categoryId)
        val itemCount = row.movies.size + row.series.size
        counts[key] = (counts[key] ?: 0) + itemCount
    }
    return counts
}

internal fun vodCategoryFromBrowseRow(row: VodBrowseRow): VodCategory? {
    parseCategoryBrowseRowId(row.id)?.let { (playlistId, categoryId) ->
        if (!VodCategoryGuards.isStreamBackedCategoryId(categoryId) || row.title.isBlank()) return null
        return VodCategory(id = categoryId, name = row.title, playlistId = playlistId)
    }
    if (row.id.startsWith("genre_")) {
        Log.w(
            "VodCategoryGuard",
            "ignored genre browse row id=${row.id} — genre text is not a stream-backed categoryId"
        )
    }
    return null
}

internal fun prepareMovieSidebarCategories(
    primary: List<VodCategory>,
    browseRows: List<VodBrowseRow>
): VodCategoryNameResolver.SeriesSidebarCategories {
    val itemCounts = categoryItemCountsFromBrowseRows(browseRows)
    val raw = if (primary.isNotEmpty()) {
        primary
    } else {
        browseRows.mapNotNull(::vodCategoryFromBrowseRow)
    }
    return VodCategoryNameResolver.prepareMovieCategoriesForSidebar(raw, itemCounts)
}

internal fun prepareSeriesSidebarCategories(
    primary: List<VodCategory>,
    browseRows: List<VodBrowseRow>
): VodCategoryNameResolver.SeriesSidebarCategories {
    val itemCounts = categoryItemCountsFromBrowseRows(browseRows)
    val fromBrowse = browseRows.mapNotNull(::vodCategoryFromBrowseRow)
    val raw = VodCategoryNameResolver.mergeSeriesCategorySources(primary, fromBrowse)
    return VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(raw, itemCounts)
}
