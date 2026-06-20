package com.grid.tv.ui.component

import androidx.compose.foundation.lazy.LazyListState

/** Direction of the latest D-pad move for lazy-list focus scrolling. */
enum class TvLazyFocusScrollDirection {
    UP,
    DOWN,
    NEUTRAL
}

/**
 * Leanback-style focus scroll: keep the list stationary while focus moves within the
 * visible viewport, and only scroll when the focused index crosses the top or bottom edge.
 */
suspend fun LazyListState.animateScrollToItemIfNeeded(
    index: Int,
    direction: TvLazyFocusScrollDirection = TvLazyFocusScrollDirection.NEUTRAL
) {
    if (index < 0) return
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return

    val targetIndex = index.coerceIn(0, layoutInfo.totalItemsCount - 1)
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) {
        animateScrollToItem(targetIndex)
        return
    }

    val firstVisible = visible.first().index
    val lastVisible = visible.last().index
    if (targetIndex in firstVisible..lastVisible) return

    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(1)
    val estimatedItemSize = visible.firstOrNull { it.index == targetIndex }?.size
        ?: visible.last().size.coerceAtLeast(1)

    when {
        targetIndex > lastVisible -> {
            val scrollOffset = (viewportHeight - estimatedItemSize).coerceAtLeast(0)
            animateScrollToItem(targetIndex, scrollOffset)
        }
        targetIndex < firstVisible -> {
            animateScrollToItem(targetIndex, scrollOffset = 0)
        }
        direction == TvLazyFocusScrollDirection.DOWN -> {
            val scrollOffset = (viewportHeight - estimatedItemSize).coerceAtLeast(0)
            animateScrollToItem(targetIndex, scrollOffset)
        }
        direction == TvLazyFocusScrollDirection.UP -> {
            animateScrollToItem(targetIndex, scrollOffset = 0)
        }
    }
}

/** Non-animated variant for one-shot jumps (e.g. restore saved guide position). */
suspend fun LazyListState.scrollToItemIfNeeded(index: Int) {
    if (index < 0) return
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return

    val targetIndex = index.coerceIn(0, layoutInfo.totalItemsCount - 1)
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) {
        scrollToItem(targetIndex)
        return
    }

    val firstVisible = visible.first().index
    val lastVisible = visible.last().index
    if (targetIndex in firstVisible..lastVisible) return

    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(1)
    val estimatedItemSize = visible.last().size.coerceAtLeast(1)
    if (targetIndex > lastVisible) {
        val scrollOffset = (viewportHeight - estimatedItemSize).coerceAtLeast(0)
        scrollToItem(targetIndex, scrollOffset)
    } else {
        scrollToItem(targetIndex, scrollOffset = 0)
    }
}
