package com.grid.tv.ui.screen

import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.delay

/** Pending grid restore: scroll viewport first, then focus target index. */
data class VodGridFocusRestoreRequest(
    val targetIndex: Int,
    val scrollIndex: Int,
    val scrollOffset: Int,
    val contentKey: String? = null,
    val token: Long = System.nanoTime(),
)

/** Parsed stable identity from browse grid keys (`playlistId_streamId`). */
data class VodContentKeyParts(
    val playlistId: String,
    val itemId: String,
) {
    companion object {
        fun parse(key: String?): VodContentKeyParts? {
            if (key.isNullOrBlank()) return null
            val sep = key.indexOf('_')
            if (sep <= 0 || sep >= key.lastIndex) return null
            return VodContentKeyParts(
                playlistId = key.substring(0, sep),
                itemId = key.substring(sep + 1),
            )
        }
    }
}

/**
 * Resolves focus index with priority:
 * exact contentKey → itemId match → playlist+item match → saved index → first visible.
 */
fun resolveBrowseGridFocusIndex(
    itemCount: Int,
    saved: VodGridFocusMemory,
    keyAtIndex: (Int) -> String?,
    firstVisibleIndex: Int = 0,
): Int {
    if (itemCount <= 0) return 0
    val savedParts = VodContentKeyParts.parse(saved.contentKey)

    if (!saved.contentKey.isNullOrBlank()) {
        for (i in 0 until itemCount) {
            if (keyAtIndex(i) == saved.contentKey) return i
        }
    }

    if (savedParts != null) {
        for (i in 0 until itemCount) {
            val parts = VodContentKeyParts.parse(keyAtIndex(i)) ?: continue
            if (parts.playlistId == savedParts.playlistId && parts.itemId == savedParts.itemId) {
                return i
            }
        }
        for (i in 0 until itemCount) {
            val parts = VodContentKeyParts.parse(keyAtIndex(i)) ?: continue
            if (parts.itemId == savedParts.itemId) return i
        }
    }

    val indexFallback = saved.itemIndex.coerceIn(0, itemCount - 1)
    if (keyAtIndex(indexFallback) != null) return indexFallback

    return firstVisibleIndex.coerceIn(0, itemCount - 1)
}

/** Wait until [index] appears in the grid viewport (or timeout). */
suspend fun awaitGridItemVisible(
    gridState: LazyGridState,
    index: Int,
    maxFrames: Int = 45,
) {
    repeat(maxFrames) {
        if (gridState.layoutInfo.visibleItemsInfo.any { it.index == index }) return
        delay(16)
    }
}

/**
 * Scroll-then-focus sequence for premium restoration (no off-screen focus flicker).
 * Returns the resolved target index after scroll settles.
 */
suspend fun restoreGridFocusAnimated(
    gridState: LazyGridState,
    request: VodGridFocusRestoreRequest,
    itemCount: Int,
): Int {
    if (itemCount <= 0) return 0
    val target = request.targetIndex.coerceIn(0, itemCount - 1)
    if (request.scrollIndex >= 0) {
        gridState.scrollToItem(
            request.scrollIndex.coerceIn(0, itemCount - 1),
            request.scrollOffset
        )
    }
    gridState.scrollToItem(target)
    awaitGridItemVisible(gridState, target)
    return target
}
