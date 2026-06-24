package com.grid.tv.feature.epg

import com.grid.tv.domain.model.Channel

/** Live guide filter using Xtream/provider channel group names (`Channel.group`). */
data class GuideChannelFilter(
    val selectedGroups: Set<String> = emptySet()
) {
    val isActive: Boolean get() = selectedGroups.isNotEmpty()

    val label: String
        get() = when {
            selectedGroups.isEmpty() -> "All channels"
            selectedGroups.size == 1 -> com.grid.tv.domain.model.ChannelGroupIdentity.groupName(selectedGroups.first())
            else -> "${selectedGroups.size} groups"
        }

    fun appliesTo(channel: Channel): Boolean =
        selectedGroups.isEmpty() ||
            selectedGroups.any { com.grid.tv.domain.model.ChannelGroupIdentity.matches(channel, it) }

    companion object {
        val All = GuideChannelFilter()

        private const val SEPARATOR = '\u001F'

        fun encode(groups: Set<String>): String =
            groups.filter { it.isNotBlank() }.sorted().joinToString(SEPARATOR.toString())

        fun decode(raw: String): Set<String> =
            if (raw.isBlank()) emptySet()
            else raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
    }
}
