package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryGuards
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.parseCategoryBrowseRowId

/**
 * O(n) index built when browse rows are ingested or language-filtered.
 * Partition and sidebar code read counts / row-derived categories from here
 * instead of re-scanning every row on each filter change.
 */
data class VodBrowseRowCategoryIndex(
    val itemCountByCategoryKey: Map<String, Int> = emptyMap(),
    val categoriesFromRows: List<VodCategory> = emptyList(),
) {
    companion object {
        val EMPTY = VodBrowseRowCategoryIndex()

        fun fromBrowseRows(rows: List<VodBrowseRow>): VodBrowseRowCategoryIndex {
            if (rows.isEmpty()) return EMPTY
            val counts = linkedMapOf<String, Int>()
            val categories = ArrayList<VodCategory>(rows.size.coerceAtMost(64))
            rows.forEach { row ->
                val (playlistId, categoryId) = parseCategoryBrowseRowId(row.id) ?: return@forEach
                if (!VodCategoryGuards.isStreamBackedCategoryId(categoryId) || row.title.isBlank()) return@forEach
                val key = VodCategoryNameResolver.categoryKey(playlistId, categoryId)
                val itemCount = row.movies.size + row.series.size
                counts[key] = (counts[key] ?: 0) + itemCount
                categories += VodCategory(id = categoryId, name = row.title, playlistId = playlistId)
            }
            return VodBrowseRowCategoryIndex(counts, categories)
        }
    }
}

fun List<VodBrowseRow>.toCategoryIndex(): VodBrowseRowCategoryIndex =
    VodBrowseRowCategoryIndex.fromBrowseRows(this)
