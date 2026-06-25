package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryGuards
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.parseCategoryBrowseRowId

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
    val raw = if (primary.isNotEmpty()) {
        primary
    } else {
        browseRows.mapNotNull(::vodCategoryFromBrowseRow)
    }
    return VodCategoryNameResolver.prepareMovieCategoriesForSidebar(raw)
}

internal fun prepareSeriesSidebarCategories(
    primary: List<VodCategory>,
    browseRows: List<VodBrowseRow>
): VodCategoryNameResolver.SeriesSidebarCategories {
    val fromBrowse = browseRows.mapNotNull(::vodCategoryFromBrowseRow)
    val raw = VodCategoryNameResolver.mergeSeriesCategorySources(primary, fromBrowse)
    return VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(raw)
}
