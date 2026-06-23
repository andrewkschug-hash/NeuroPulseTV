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

/**
 * EPG channel list: scroll one row at a time on single-step D-pad moves so focus never
 * leapfrogs unbound/recycled rows. Large jumps (Page Up/Down) still align to the target.
 */
suspend fun LazyListState.animateScrollEpgChannelIntoView(
    index: Int,
    direction: TvLazyFocusScrollDirection,
    rowHeightPx: Int
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
    val itemSize = rowHeightPx.coerceAtLeast(1)
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(itemSize)

    when {
        targetIndex < firstVisible -> {
            if (direction == TvLazyFocusScrollDirection.UP && targetIndex == firstVisible - 1) {
                animateScrollToItem(targetIndex, scrollOffset = 0)
            } else {
                animateScrollToItem(targetIndex, scrollOffset = 0)
            }
        }
        targetIndex > lastVisible -> {
            if (direction == TvLazyFocusScrollDirection.DOWN && targetIndex == lastVisible + 1) {
                animateScrollToItem(firstVisible + 1, scrollOffset = 0)
            } else {
                val scrollOffset = (viewportHeight - itemSize).coerceAtLeast(0)
                animateScrollToItem(targetIndex, scrollOffset)
            }
        }
        direction == TvLazyFocusScrollDirection.DOWN &&
            targetIndex == lastVisible &&
            targetIndex < layoutInfo.totalItemsCount - 1 -> {
            animateScrollToItem(firstVisible + 1, scrollOffset = 0)
        }
        direction == TvLazyFocusScrollDirection.UP &&
            targetIndex == firstVisible &&
            targetIndex > 0 -> {
            animateScrollToItem(targetIndex - 1, scrollOffset = 0)
        }
    }
}

/**
 * VOD wall rows use mixed heights (hero + category rows). Always pin the focused row when
 * moving up so upper rows are not left scrolled off-screen.
 */
suspend fun LazyListState.animateScrollVodWallRowIntoView(
    index: Int,
    direction: TvLazyFocusScrollDirection
) {
    if (index < 0) return
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return

    val targetIndex = index.coerceIn(0, layoutInfo.totalItemsCount - 1)
    when (direction) {
        TvLazyFocusScrollDirection.UP -> {
            animateScrollToItem(targetIndex, scrollOffset = 0)
        }
        TvLazyFocusScrollDirection.DOWN -> {
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                animateScrollToItem(targetIndex)
                return
            }
            val firstVisible = visible.first().index
            val lastVisible = visible.last().index
            if (targetIndex > lastVisible) {
                val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                    .coerceAtLeast(1)
                val estimatedItemSize = visible.last().size.coerceAtLeast(1)
                val scrollOffset = (viewportHeight - estimatedItemSize).coerceAtLeast(0)
                animateScrollToItem(targetIndex, scrollOffset)
            } else if (targetIndex < firstVisible) {
                animateScrollToItem(targetIndex, scrollOffset = 0)
            } else if (targetIndex == lastVisible && targetIndex < layoutInfo.totalItemsCount - 1) {
                animateScrollToItem(firstVisible + 1, scrollOffset = 0)
            }
        }
        TvLazyFocusScrollDirection.NEUTRAL -> {
            animateScrollToItemIfNeeded(targetIndex, direction)
        }
    }
}

/** Non-animated snap for focus leaving the content zone. */
suspend fun LazyListState.scrollVodWallToTop() {
    if (firstVisibleItemIndex <= 0 && firstVisibleItemScrollOffset <= 0) return
    scrollToItem(0, scrollOffset = 0)
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
    val estimatedItemSize = visible.firstOrNull { it.size > 0 }?.size
        ?: visible.last().size.coerceAtLeast(1)
    if (targetIndex > lastVisible) {
        val scrollOffset = (viewportHeight - estimatedItemSize).coerceAtLeast(0)
        scrollToItem(targetIndex, scrollOffset)
    } else {
        scrollToItem(targetIndex, scrollOffset = 0)
    }
}
