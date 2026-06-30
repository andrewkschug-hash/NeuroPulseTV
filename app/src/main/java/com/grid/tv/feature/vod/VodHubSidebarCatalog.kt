package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryGuards
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.parseCategoryBrowseRowId

internal fun categoryItemCountsFromBrowseRows(rows: List<VodBrowseRow>): Map<String, Int> =
    VodBrowseRowCategoryIndex.fromBrowseRows(rows).itemCountByCategoryKey

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
    browseIndex: VodBrowseRowCategoryIndex,
): VodCategoryNameResolver.SeriesSidebarCategories {
    if (browseIndex.itemCountByCategoryKey.isEmpty()) {
        return VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap())
    }
    val raw = if (primary.isNotEmpty()) {
        primary
    } else {
        browseIndex.categoriesFromRows
    }
    return VodCategoryNameResolver.prepareMovieCategoriesForSidebar(raw, browseIndex.itemCountByCategoryKey)
}

internal fun prepareSeriesSidebarCategories(
    primary: List<VodCategory>,
    browseIndex: VodBrowseRowCategoryIndex,
): VodCategoryNameResolver.SeriesSidebarCategories {
    if (browseIndex.itemCountByCategoryKey.isEmpty()) {
        return VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap())
    }
    val raw = VodCategoryNameResolver.mergeSeriesCategorySources(primary, browseIndex.categoriesFromRows)
    return VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(raw, browseIndex.itemCountByCategoryKey)
}
