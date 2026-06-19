package com.grid.tv.ui.viewmodel

/**
 * Windowed pagination for large VOD grids. Exposes at most [MAX_WINDOW] items to Compose
 * and grows or slides the window as the user scrolls near the bottom.
 */
class VodCatalogPager<T> {
  private var allItems: List<T> = emptyList()
  private var windowStart: Int = 0
  private var visibleCount: Int = 0

  fun reset(items: List<T>) {
    allItems = items
    windowStart = 0
    visibleCount = if (items.isEmpty()) {
      0
    } else {
      minOf(PAGE_SIZE, items.size)
    }
  }

  fun totalCount(): Int = allItems.size

  fun currentSlice(): List<T> {
    if (allItems.isEmpty() || visibleCount <= 0) return emptyList()
    val end = minOf(windowStart + visibleCount, allItems.size)
    if (windowStart >= end) return emptyList()
    return allItems.subList(windowStart, end)
  }

  fun loadMore(): Boolean {
    if (allItems.isEmpty()) return false
    val currentEnd = windowStart + visibleCount
    if (currentEnd >= allItems.size) return false

    if (visibleCount < MAX_WINDOW) {
      visibleCount = minOf(visibleCount + PAGE_SIZE, MAX_WINDOW, allItems.size - windowStart)
      return true
    }

    val nextStart = windowStart + PAGE_SIZE
    if (nextStart >= allItems.size) return false
    windowStart = nextStart
    visibleCount = minOf(MAX_WINDOW, allItems.size - windowStart)
    return true
  }

  companion object {
    const val PAGE_SIZE = 20
    const val MAX_WINDOW = 60
    const val PREFETCH_THRESHOLD = 5
  }
}
