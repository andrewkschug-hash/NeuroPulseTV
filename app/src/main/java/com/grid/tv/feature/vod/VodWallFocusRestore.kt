package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow

/** Resolves row/column indices for a saved content key after wall rows rebuild. */
fun resolveVodWallFocus(
    wallRows: List<VodWallRow>,
    savedContentKey: String?,
    fallbackRow: Int,
    fallbackCol: Int
): Pair<Int, Int> {
    if (wallRows.isEmpty()) return 0 to 0
    if (!savedContentKey.isNullOrBlank()) {
        wallRows.forEachIndexed { rowIdx, row ->
            val colIdx = row.items.indexOfFirst { it.key == savedContentKey }
            if (colIdx >= 0) return rowIdx to colIdx
        }
    }
    val row = fallbackRow.coerceIn(0, wallRows.lastIndex)
    val maxCol = wallRows[row].items.lastIndex.coerceAtLeast(0)
    return row to fallbackCol.coerceIn(0, maxCol)
}

fun vodWallItemKey(item: VodWallItem?): String? = item?.key
