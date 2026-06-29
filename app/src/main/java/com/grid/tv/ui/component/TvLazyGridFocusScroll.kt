package com.grid.tv.ui.component

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

fun isTvDirectionalKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && (
        event.key == Key.DirectionUp ||
            event.key == Key.DirectionDown ||
            event.key == Key.DirectionLeft ||
            event.key == Key.DirectionRight
        )

/**
 * Column count inferred from the first fully visible row in a vertical lazy grid.
 */
fun LazyGridState.visibleColumnCount(fallback: Int = 4): Int {
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return fallback
    val firstRow = info.visibleItemsInfo.first().row
    return info.visibleItemsInfo.count { it.row == firstRow }.coerceAtLeast(1)
}

/**
 * Leanback-style browse grid scroll: keep the viewport stable while focus moves within
 * visible rows, and only scroll by one row (or the minimum amount) when focus crosses
 * the top or bottom edge — equivalent to scrollIntoView({ block: 'nearest' }).
 */
suspend fun LazyGridState.animateScrollGridItemIntoView(
    index: Int,
    direction: TvLazyFocusScrollDirection = TvLazyFocusScrollDirection.NEUTRAL,
    columnCount: Int = visibleColumnCount(),
    safePaddingPx: Int = 48,
) {
    if (index < 0) return
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return

    val target = index.coerceIn(0, layoutInfo.totalItemsCount - 1)
    val columns = columnCount.coerceAtLeast(1)
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) {
        animateScrollToItem(target)
        return
    }

    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(1)
    val padding = safePaddingPx.coerceAtLeast(0)
    val safeBottom = viewportHeight - padding

    val firstVisible = visible.first()
    val lastVisible = visible.last()
    val firstIndex = firstVisible.index
    val lastIndex = lastVisible.index

    val targetItem = visible.find { it.index == target }
    if (targetItem != null) {
        val itemTop = targetItem.offset.y
        val itemBottom = itemTop + targetItem.size.height
        val fullyVisible = itemTop >= padding && itemBottom <= safeBottom
        if (fullyVisible) return

        when {
            itemBottom > safeBottom && direction == TvLazyFocusScrollDirection.DOWN -> {
                val scrollIndex = (firstIndex + columns).coerceAtMost(layoutInfo.totalItemsCount - 1)
                animateScrollToItem(scrollIndex, scrollOffset = 0)
            }
            itemTop < padding && direction == TvLazyFocusScrollDirection.UP -> {
                val scrollIndex = (firstIndex - columns).coerceAtLeast(0)
                animateScrollToItem(scrollIndex, scrollOffset = padding)
            }
            itemBottom > safeBottom -> {
                animateScrollToItem(target, scrollOffset = padding)
            }
            itemTop < padding -> {
                animateScrollToItem(target, scrollOffset = padding)
            }
        }
        return
    }

    when {
        target > lastIndex -> {
            if (direction == TvLazyFocusScrollDirection.DOWN && target <= lastIndex + columns) {
                val scrollIndex = (firstIndex + columns).coerceAtMost(layoutInfo.totalItemsCount - 1)
                animateScrollToItem(scrollIndex, scrollOffset = 0)
            } else {
                val rowHeight = visible
                    .filter { it.row == lastVisible.row }
                    .maxOfOrNull { it.size.height }
                    ?: lastVisible.size.height
                val scrollOffset = (viewportHeight - rowHeight - padding).coerceAtLeast(0)
                animateScrollToItem(target, scrollOffset)
            }
        }
        target < firstIndex -> {
            animateScrollToItem(target, scrollOffset = padding)
        }
        direction == TvLazyFocusScrollDirection.DOWN &&
            target == lastIndex &&
            target < layoutInfo.totalItemsCount - 1 -> {
            val scrollIndex = (firstIndex + columns).coerceAtMost(layoutInfo.totalItemsCount - 1)
            animateScrollToItem(scrollIndex, scrollOffset = 0)
        }
        direction == TvLazyFocusScrollDirection.UP &&
            target == firstIndex &&
            target > 0 -> {
            val scrollIndex = (firstIndex - columns).coerceAtLeast(0)
            animateScrollToItem(scrollIndex, scrollOffset = padding)
        }
    }
}

/** Non-animated variant for one-shot grid restoration. */
suspend fun LazyGridState.scrollGridItemIntoViewIfNeeded(
    index: Int,
    columnCount: Int = visibleColumnCount(),
    safePaddingPx: Int = 48,
) {
    if (index < 0) return
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return

    val target = index.coerceIn(0, layoutInfo.totalItemsCount - 1)
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) {
        scrollToItem(target)
        return
    }

    val firstIndex = visible.first().index
    val lastIndex = visible.last().index
    if (target in firstIndex..lastIndex) {
        val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
            .coerceAtLeast(1)
        val padding = safePaddingPx.coerceAtLeast(0)
        val item = visible.first { it.index == target }
        val itemTop = item.offset.y
        val itemBottom = itemTop + item.size.height
        if (itemTop >= padding && itemBottom <= viewportHeight - padding) return
    }

    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(1)
    val rowHeight = visible.maxOfOrNull { it.size.height } ?: visible.last().size.height
    val scrollOffset = (viewportHeight - rowHeight - safePaddingPx).coerceAtLeast(0)
    if (target > lastIndex) {
        scrollToItem(target, scrollOffset)
    } else {
        scrollToItem(target, scrollOffset = safePaddingPx)
    }
}
