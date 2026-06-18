package com.grid.tv.ui.component

/**
 * Visible rows for the collapsible channel-group picker / filter menu.
 * Does not change how groups are fetched or stored — UI navigation only.
 */
sealed class GuideGroupVisibleRow {
    data object AllChannels : GuideGroupVisibleRow()

    data class Category(
        val categoryIndex: Int,
        val prefix: String,
        val childCount: Int,
        val expanded: Boolean
    ) : GuideGroupVisibleRow()

    data class Group(
        val fullName: String,
        val suffixLabel: String,
        val categoryIndex: Int
    ) : GuideGroupVisibleRow()
}

data class GuideGroupCategory(
    val prefix: String,
    val groups: List<String>
)

fun buildGuideGroupCategories(channelGroups: List<String>): List<GuideGroupCategory> {
    val grouped = linkedMapOf<String, MutableList<String>>()
    val order = mutableListOf<String>()
    channelGroups.forEach { group ->
        val prefix = guideGroupRegionPrefix(group)
            ?: group.trim().substringBefore(' ').ifBlank { group }
        if (!grouped.containsKey(prefix)) {
            grouped[prefix] = mutableListOf()
            order.add(prefix)
        }
        grouped.getValue(prefix).add(group)
    }
    return order.map { prefix ->
        GuideGroupCategory(prefix = prefix, groups = grouped.getValue(prefix).toList())
    }
}

fun guideGroupSuffixLabel(fullName: String): String {
    val trimmed = fullName.trim()
    for (separator in GUIDE_GROUP_REGION_SEPARATORS) {
        if (separator in trimmed) {
            val suffix = trimmed.substringAfter(separator).trim()
            return if (suffix.isNotBlank()) "❖ $suffix" else trimmed
        }
    }
    val prefix = guideGroupRegionPrefix(trimmed)
    if (prefix != null && trimmed.startsWith(prefix, ignoreCase = true)) {
        val rest = trimmed.removePrefix(prefix).trim().trimStart('❖', '|', '-', '–', ' ')
        return if (rest.isNotBlank()) "❖ $rest" else trimmed
    }
    return trimmed
}

fun expandedCategoriesForSelection(
    categories: List<GuideGroupCategory>,
    selectedGroups: Set<String>
): Set<Int> = categories.mapIndexedNotNull { index, category ->
    if (category.groups.any { it in selectedGroups }) index else null
}.toSet()

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
                prefix = category.prefix,
                childCount = category.groups.size,
                expanded = expanded
            )
        )
        if (expanded) {
            category.groups.forEach { fullName ->
                add(
                    GuideGroupVisibleRow.Group(
                        fullName = fullName,
                        suffixLabel = guideGroupSuffixLabel(fullName),
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

fun toggleCategoryExpansion(
    expandedCategories: Set<Int>,
    categoryIndex: Int
): Set<Int> {
    val next = expandedCategories.toMutableSet()
    if (categoryIndex in next) next.remove(categoryIndex) else next.add(categoryIndex)
    return next
}
