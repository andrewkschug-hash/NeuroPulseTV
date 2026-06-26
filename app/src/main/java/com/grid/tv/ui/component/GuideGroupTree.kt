package com.grid.tv.ui.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.grid.tv.util.parentGroupSortIndex
import com.grid.tv.util.resolveParentGroup

/**
 * Visible rows for the collapsible channel-group picker / filter menu.
 * Does not change how groups are fetched or stored — UI navigation only.
 */
sealed class GuideGroupVisibleRow {
    data object AllChannels : GuideGroupVisibleRow()

    data class Category(
        val categoryIndex: Int,
        val displayName: String,
        val channelCount: Int,
        val subGroupCount: Int,
        val expanded: Boolean
    ) : GuideGroupVisibleRow()

    data class Group(
        val fullName: String,
        val categoryIndex: Int
    ) : GuideGroupVisibleRow()

    data class SelectAll(
        val categoryIndex: Int,
        val displayName: String,
        val groupNames: List<String>
    ) : GuideGroupVisibleRow()
}

fun guideGroupVisibleRowKey(row: GuideGroupVisibleRow): String = when (row) {
    GuideGroupVisibleRow.AllChannels -> "all"
    is GuideGroupVisibleRow.Category -> "cat_${row.categoryIndex}"
    is GuideGroupVisibleRow.SelectAll -> "select_all_${row.categoryIndex}"
    is GuideGroupVisibleRow.Group -> "grp_${row.fullName}"
}

data class GuideGroupCategory(
    val displayName: String,
    val groups: List<String>,
    val channelCount: Int = 0
)

fun buildGuideGroupCategories(
    channelGroups: List<String>,
    groupChannelCounts: Map<String, Int> = emptyMap(),
    hideAdult: Boolean = true
): List<GuideGroupCategory> {
    return com.grid.tv.feature.guide.GuideCategoryProcessor.organizeGroups(
        channelGroups = channelGroups,
        groupChannelCounts = groupChannelCounts,
        hideAdult = hideAdult
    ).flatCategories
}

fun expandedCategoriesForSelection(
    categories: List<GuideGroupCategory>,
    selectedGroups: Set<String>
): Set<Int> {
    val index = categories.indexOfFirst { category ->
        category.groups.any { it in selectedGroups }
    }
    return if (index >= 0) setOf(index) else emptySet()
}

fun buildVisibleGuideGroupRows(
    categories: List<GuideGroupCategory>,
    expandedCategories: Set<Int>
): List<GuideGroupVisibleRow> = buildList {
    add(GuideGroupVisibleRow.AllChannels)
    categories.forEachIndexed { categoryIndex, category ->
        val expanded = categoryIndex in expandedCategories
        add(
            GuideGroupVisibleRow.Category(
                categoryIndex = categoryIndex,
                displayName = category.displayName,
                channelCount = category.channelCount,
                subGroupCount = category.groups.size,
                expanded = expanded
            )
        )
        if (expanded) {
            add(
                GuideGroupVisibleRow.SelectAll(
                    categoryIndex = categoryIndex,
                    displayName = category.displayName,
                    groupNames = category.groups
                )
            )
            category.groups.forEach { fullName ->
                add(
                    GuideGroupVisibleRow.Group(
                        fullName = fullName,
                        categoryIndex = categoryIndex
                    )
                )
            }
        }
    }
}

fun visibleRowIndexForSelection(
    categories: List<GuideGroupCategory>,
    expandedCategories: Set<Int>,
    selectedGroups: Set<String>
): Int {
    if (selectedGroups.isEmpty()) return 0
    val rows = buildVisibleGuideGroupRows(categories, expandedCategories)
    val target = selectedGroups.first()
    return rows.indexOfFirst { row ->
        row is GuideGroupVisibleRow.Group && row.fullName == target
    }.takeIf { it >= 0 } ?: 0
}

/** Only one parent category may be expanded at a time. */
fun toggleCategoryExpansion(
    expandedCategories: Set<Int>,
    categoryIndex: Int
): Set<Int> = if (categoryIndex in expandedCategories) {
    emptySet()
} else {
    setOf(categoryIndex)
}

fun categoryCountLabel(channelCount: Int, subGroupCount: Int): String = when {
    channelCount > 0 -> channelCount.toString()
    subGroupCount > 0 -> "$subGroupCount groups"
    else -> "0"
}

/** Shared D-pad handler for guide group pickers (full-screen, dialog, filter menu). */
fun handleGuideGroupTvKeyEvent(
    event: KeyEvent,
    focusedIndex: Int,
    lastIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    onActivate: () -> Unit,
    onBack: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionDown -> {
            if (focusedIndex < lastIndex) onFocusedIndexChange(focusedIndex + 1)
            true
        }
        Key.DirectionUp -> {
            if (focusedIndex > 0) onFocusedIndexChange(focusedIndex - 1)
            true
        }
        Key.Enter,
        Key.NumPadEnter,
        Key.DirectionCenter -> {
            onActivate()
            true
        }
        Key.Back,
        Key.Escape -> {
            onBack()
            true
        }
        else -> false
    }
}
