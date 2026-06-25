package com.grid.tv.feature.guide

import com.grid.tv.domain.model.ChannelGroupIdentity
import com.grid.tv.ui.component.GuideGroupCategory
import com.grid.tv.util.isAdultChannelGroup
import com.grid.tv.util.parentGroupSortIndex
import com.grid.tv.util.resolveParentGroup

/** Organizes provider channel groups for TV-friendly browsing. */
object GuideCategoryProcessor {

    const val JUNK_CHANNEL_THRESHOLD = 500
    const val SECTION_ALL = "All Channels"
    const val SECTION_COUNTRIES = "Countries"
    const val SECTION_CONTENT = "Content"
    const val SECTION_PROVIDER = "Provider Groups"

    private val JUNK_CATEGORY_NAMES = setOf(
        "other",
        "misc",
        "general",
        "uncategorized",
        "unknown",
        "various"
    )

    private val COUNTRY_PARENTS = setOf(
        "Africa",
        "Americas",
        "Europe",
        "UK",
        "USA",
        "Canada",
        "Australia",
        "New Zealand",
        "Middle East"
    )

    private val CONTENT_PARENTS = setOf(
        "Sports",
        "News",
        "Movies",
        "Kids",
        "24/7 Channels",
        "4K Channels",
        "Entertainment",
        "Documentary"
    )

    fun normalizeGroupName(raw: String): String {
        val trimmed = raw.trim().replace("|", " ").replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase()
        return when {
            lower in setOf("usa", "us", "united states", "u.s.", "u.s.a.") -> "USA"
            lower in setOf("canada", "ca", "can") -> "Canada"
            lower in setOf("uk", "gb", "united kingdom", "great britain") -> "UK"
            lower.contains("sport") -> "Sports"
            lower.contains("movie") || lower.contains("cinema") || lower == "film" -> "Movies"
            lower.contains("news") -> "News"
            lower.contains("kid") || lower.contains("child") -> "Kids"
            lower.contains("entertain") -> "Entertainment"
            lower.contains("document") -> "Documentary"
            else -> trimmed.split(" ")
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                    }
                }
        }
    }

    fun shouldHideAdultGroup(displayName: String, hideAdult: Boolean): Boolean {
        if (!hideAdult) return false
        return isAdultChannelGroup(displayName)
    }

    fun isJunkCategory(displayName: String): Boolean {
        val lower = displayName.trim().lowercase()
        return lower in JUNK_CATEGORY_NAMES
    }

    data class OrganizedGuideGroups(
        val allChannelCount: Int,
        val countryCategories: List<GuideGroupCategory>,
        val contentCategories: List<GuideGroupCategory>,
        val providerCategories: List<GuideGroupCategory>,
        /** Flat list for legacy picker compatibility. */
        val flatCategories: List<GuideGroupCategory>
    )

    fun organizeGroups(
        channelGroups: List<String>,
        groupChannelCounts: Map<String, Int> = emptyMap(),
        hideAdult: Boolean = true
    ): OrganizedGuideGroups {
        val dedupedKeys = linkedMapOf<String, String>()
        channelGroups.forEach { key ->
            val name = normalizeGroupName(ChannelGroupIdentity.groupName(key))
            if (name.isBlank()) return@forEach
            if (shouldHideAdultGroup(name, hideAdult)) return@forEach
            val normalizedKey = dedupedKeys.keys.firstOrNull { existing ->
                normalizeGroupName(ChannelGroupIdentity.groupName(existing))
                    .equals(name, ignoreCase = true)
            }
            if (normalizedKey == null) {
                dedupedKeys[key] = name
            }
        }

        val parentBuckets = linkedMapOf<String, MutableList<String>>()
        dedupedKeys.keys.forEach { key ->
            val groupName = dedupedKeys.getValue(key)
            val parent = resolveParentGroup(groupName)
            parentBuckets.getOrPut(parent) { mutableListOf() }.add(key)
        }

        val allCategories = parentBuckets.entries
            .map { (parent, groups) ->
                GuideGroupCategory(
                    displayName = parent,
                    groups = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                        ChannelGroupIdentity.groupName(it)
                    }),
                    channelCount = groups.sumOf { groupChannelCounts[it] ?: 0 }
                )
            }
            .sortedWith(compareBy({ parentGroupSortIndex(it.displayName) }, { it.displayName }))

        val countries = allCategories.filter { it.displayName in COUNTRY_PARENTS }
        val content = allCategories.filter { it.displayName in CONTENT_PARENTS }

        val provider = allCategories.filter { category ->
            val junk = isJunkCategory(category.displayName)
            val oversized = category.channelCount > JUNK_CHANNEL_THRESHOLD
            category.displayName !in COUNTRY_PARENTS &&
                category.displayName !in CONTENT_PARENTS &&
                (junk || oversized || category.displayName !in setOf("Other", "Adult (18+)"))
        } + allCategories.filter { category ->
            (isJunkCategory(category.displayName) || category.channelCount > JUNK_CHANNEL_THRESHOLD) &&
                category.displayName !in COUNTRY_PARENTS &&
                category.displayName !in CONTENT_PARENTS
        }.distinctBy { it.displayName }

        val flat = buildList {
            addAll(countries)
            addAll(content)
            addAll(provider.distinctBy { it.displayName })
        }

        return OrganizedGuideGroups(
            allChannelCount = dedupedKeys.keys.sumOf { groupChannelCounts[it] ?: 0 },
            countryCategories = countries,
            contentCategories = content,
            providerCategories = provider.distinctBy { it.displayName },
            flatCategories = flat.ifEmpty { allCategories.filter { !shouldHideAdultGroup(it.displayName, hideAdult) } }
        )
    }
}
