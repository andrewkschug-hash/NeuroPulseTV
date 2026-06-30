package com.grid.tv.ui.component

import android.util.Log
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

    /** Non-focusable section label above favourite groups. */
    data object FavoriteSectionHeader : GuideGroupVisibleRow()

    /** Non-focusable hint when the favourites section has no items yet. */
    data object FavoriteSectionEmpty : GuideGroupVisibleRow()

    enum class ListSection {
        Favorites,
        Catalog,
    }

    data class Category(
        val categoryIndex: Int,
        val displayName: String,
        val channelCount: Int,
        val subGroupCount: Int,
        val expanded: Boolean
    ) : GuideGroupVisibleRow()

    data class Group(
        val fullName: String,
        val categoryIndex: Int,
        val listSection: ListSection = ListSection.Catalog,
    ) : GuideGroupVisibleRow()

    data class SelectAll(
        val categoryIndex: Int,
        val displayName: String,
        val groupNames: List<String>
    ) : GuideGroupVisibleRow()
}

fun guideGroupVisibleRowKey(row: GuideGroupVisibleRow): String = when (row) {
    GuideGroupVisibleRow.AllChannels -> "all"
    GuideGroupVisibleRow.FavoriteSectionHeader -> "fav_header"
    GuideGroupVisibleRow.FavoriteSectionEmpty -> "fav_empty"
    is GuideGroupVisibleRow.Category -> "cat_${row.categoryIndex}"
    is GuideGroupVisibleRow.SelectAll -> "select_all_${row.categoryIndex}"
    is GuideGroupVisibleRow.Group -> "grp_${row.listSection}_${row.fullName}"
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
    val focusGroup = primarySelectedGroupForFocus(categories, selectedGroups) ?: return emptySet()
    val index = categories.indexOfFirst { focusGroup in it.groups }
    return if (index >= 0) setOf(index) else emptySet()
}

/**
 * Stable focus target for the nested category picker: lexicographically first selected group
 * that still exists in [categories]. [GuideChannelFilter.decode] preserves sorted encode order
 * in a [LinkedHashSet], so iteration is deterministic — not random [HashSet] order.
 */
internal fun primarySelectedGroupForFocus(
    categories: List<GuideGroupCategory>,
    selectedGroups: Set<String>
): String? = selectedGroups.sorted().firstOrNull { group ->
    categories.any { category -> group in category.groups }
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

/** Flat smart buckets: Country → Category as selectable rows. */
fun buildFlatSmartBucketRows(
    categories: List<GuideGroupCategory>,
    favoriteGroups: List<String> = emptyList(),
): List<GuideGroupVisibleRow> = buildList {
    add(GuideGroupVisibleRow.FavoriteSectionHeader)
    if (favoriteGroups.isEmpty()) {
        add(GuideGroupVisibleRow.FavoriteSectionEmpty)
    } else {
        favoriteGroups.forEach { fullName ->
            add(
                GuideGroupVisibleRow.Group(
                    fullName = fullName,
                    categoryIndex = -1,
                    listSection = GuideGroupVisibleRow.ListSection.Favorites,
                )
            )
        }
    }
    add(GuideGroupVisibleRow.AllChannels)
    categories.forEach { country ->
        country.groups.forEach { smartKey ->
            add(
                GuideGroupVisibleRow.Group(
                    fullName = smartKey,
                    categoryIndex = -1,
                    listSection = GuideGroupVisibleRow.ListSection.Catalog,
                )
            )
        }
    }
}

fun smartGroupDisplayLabel(key: String): String {
    val bucket = com.grid.tv.feature.guide.SmartGroupFilterKey.decode(key) ?: return key
    return "${bucket.first} · ${bucket.second}"
}

/** Flat provider groups straight from M3U/Xtream (`group-title` / live categories). */
fun buildFlatProviderVisibleRows(
    channelGroups: List<String>,
    favoriteGroups: List<String> = emptyList(),
): List<GuideGroupVisibleRow> = buildList {
    add(GuideGroupVisibleRow.FavoriteSectionHeader)
    if (favoriteGroups.isEmpty()) {
        add(GuideGroupVisibleRow.FavoriteSectionEmpty)
    } else {
        favoriteGroups.forEach { fullName ->
            add(
                GuideGroupVisibleRow.Group(
                    fullName = fullName,
                    categoryIndex = -1,
                    listSection = GuideGroupVisibleRow.ListSection.Favorites,
                )
            )
        }
    }
    add(GuideGroupVisibleRow.AllChannels)
    channelGroups.forEach { fullName ->
        add(
            GuideGroupVisibleRow.Group(
                fullName = fullName,
                categoryIndex = -1,
                listSection = GuideGroupVisibleRow.ListSection.Catalog,
            )
        )
    }
}

fun GuideGroupVisibleRow.isFocusableGroupRow(): Boolean = when (this) {
    GuideGroupVisibleRow.AllChannels,
    is GuideGroupVisibleRow.Group -> true
    else -> false
}

fun nextFocusableGroupRowIndex(rows: List<GuideGroupVisibleRow>, from: Int, delta: Int): Int {
    if (rows.isEmpty()) return 0
    var index = from
    repeat(rows.size) {
        index = (index + delta).coerceIn(0, rows.lastIndex)
        if (rows[index].isFocusableGroupRow()) return index
    }
    return from.coerceIn(0, rows.lastIndex)
}

fun firstFocusableFlatGroupRowIndex(
    channelGroups: List<String>,
    favoriteGroups: List<String> = emptyList(),
): Int {
    val rows = buildFlatProviderVisibleRows(channelGroups, favoriteGroups)
    return rows.indexOfFirst { it.isFocusableGroupRow() }.coerceAtLeast(0)
}

fun visibleRowIndexForFlatSelection(
    channelGroups: List<String>,
    selectedGroups: Set<String>,
    favoriteGroups: List<String> = emptyList(),
): Int {
    if (selectedGroups.isEmpty()) {
        return firstFocusableFlatGroupRowIndex(channelGroups, favoriteGroups)
    }
    val rows = buildFlatProviderVisibleRows(channelGroups, favoriteGroups)
    val target = selectedGroups.first()
    val favoriteIndex = rows.indexOfFirst { row ->
        row is GuideGroupVisibleRow.Group &&
            row.fullName == target &&
            row.listSection == GuideGroupVisibleRow.ListSection.Favorites
    }
    if (favoriteIndex >= 0) return favoriteIndex
    return rows.indexOfFirst { row ->
        row is GuideGroupVisibleRow.Group &&
            row.fullName == target &&
            row.listSection == GuideGroupVisibleRow.ListSection.Catalog
    }.coerceAtLeast(firstFocusableFlatGroupRowIndex(channelGroups, favoriteGroups))
}

/** Keep highlight on the last focused row when reopening the panel from the live grid. */
fun resolveChannelGroupsFocusIndex(
    channelGroups: List<String>,
    favoriteGroups: List<String>,
    committedFilter: com.grid.tv.feature.epg.GuideChannelFilter,
    currentIndex: Int,
    lastRowKey: String? = null,
): Int {
    val rows = buildFlatProviderVisibleRows(channelGroups, favoriteGroups)
    if (committedFilter.isActive) {
        val byCommitted = visibleRowIndexForFlatSelection(
            channelGroups,
            committedFilter.selectedGroups,
            favoriteGroups,
        )
        val committedRow = rows.getOrNull(byCommitted)
        if (committedRow != null && committedRow.isFocusableGroupRow()) {
            return byCommitted
        }
    }
    if (!lastRowKey.isNullOrBlank()) {
        val byKey = rows.indexOfFirst { guideGroupVisibleRowKey(it) == lastRowKey }
        if (byKey >= 0 && rows[byKey].isFocusableGroupRow()) {
            return byKey
        }
    }
    val current = rows.getOrNull(currentIndex)
    val keepCurrent = current != null &&
        current.isFocusableGroupRow() &&
        guideChannelFilterForVisibleRow(current) == committedFilter
    return if (keepCurrent) {
        currentIndex
    } else {
        visibleRowIndexForFlatSelection(channelGroups, committedFilter.selectedGroups, favoriteGroups)
    }
}

fun guideChannelFilterForVisibleRow(row: GuideGroupVisibleRow): com.grid.tv.feature.epg.GuideChannelFilter =
    when (row) {
        GuideGroupVisibleRow.AllChannels -> com.grid.tv.feature.epg.GuideChannelFilter.All
        GuideGroupVisibleRow.FavoriteSectionHeader -> com.grid.tv.feature.epg.GuideChannelFilter.All
        GuideGroupVisibleRow.FavoriteSectionEmpty -> com.grid.tv.feature.epg.GuideChannelFilter.All
        is GuideGroupVisibleRow.Group -> com.grid.tv.feature.epg.GuideChannelFilter(setOf(row.fullName))
        is GuideGroupVisibleRow.SelectAll -> com.grid.tv.feature.epg.GuideChannelFilter(row.groupNames.toSet())
        is GuideGroupVisibleRow.Category -> com.grid.tv.feature.epg.GuideChannelFilter.All
    }

fun visibleRowIndexForSelection(
    categories: List<GuideGroupCategory>,
    expandedCategories: Set<Int>,
    selectedGroups: Set<String>
): Int {
    if (selectedGroups.isEmpty()) return 0
    val focusGroup = primarySelectedGroupForFocus(categories, selectedGroups)
    if (focusGroup == null) {
        Log.w(
            GUIDE_GROUP_FOCUS_TAG,
            "visibleRowIndexForSelection fallback=0 reason=focus_group_not_in_categories " +
                "selectedGroups=$selectedGroups categoryCount=${categories.size}"
        )
        return 0
    }
    val rows = buildVisibleGuideGroupRows(categories, expandedCategories)
    val index = rows.indexOfFirst { row ->
        row is GuideGroupVisibleRow.Group && row.fullName == focusGroup
    }
    if (index < 0) {
        Log.w(
            GUIDE_GROUP_FOCUS_TAG,
            "visibleRowIndexForSelection fallback=0 reason=row_not_visible " +
                "target=$focusGroup expandedCategories=$expandedCategories " +
                "visibleRowCount=${rows.size} selectedGroups=$selectedGroups"
        )
        return 0
    }
    return index
}

private const val GUIDE_GROUP_FOCUS_TAG = "GuideGroupFocus"

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
