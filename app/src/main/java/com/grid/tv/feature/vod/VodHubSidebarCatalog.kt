package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.BuildConfig
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
    val collapseByCanonical = false
    Log.i(
        "VodSidebarGenre",
        "SIDEBAR_CALLSITE movies collapseByCanonical=$collapseByCanonical raw=${raw.size} " +
            "indexed=${browseIndex.itemCountByCategoryKey.size} build=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
    )
    return VodCategoryNameResolver.prepareMovieCategoriesForSidebar(
        categories = raw,
        itemCountByCategoryKey = browseIndex.itemCountByCategoryKey,
        collapseByCanonical = collapseByCanonical,
    )
}

internal fun prepareSeriesSidebarCategories(
    primary: List<VodCategory>,
    browseIndex: VodBrowseRowCategoryIndex,
): VodCategoryNameResolver.SeriesSidebarCategories {
    if (browseIndex.itemCountByCategoryKey.isEmpty()) {
        return VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap())
    }
    val raw = VodCategoryNameResolver.mergeSeriesCategorySources(primary, browseIndex.categoriesFromRows)
    val collapseByCanonical = false
    Log.i(
        "VodSidebarGenre",
        "SIDEBAR_CALLSITE series collapseByCanonical=$collapseByCanonical raw=${raw.size} " +
            "indexed=${browseIndex.itemCountByCategoryKey.size} build=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
    )
    return VodCategoryNameResolver.prepareSeriesCategoriesForSidebar(
        categories = raw,
        itemCountByCategoryKey = browseIndex.itemCountByCategoryKey,
        collapseByCanonical = collapseByCanonical,
    )
}
