package com.grid.tv.feature.epg

import com.grid.tv.domain.model.Channel

data class ChannelCategoryFilter(
    val label: String = "All",
    val presetId: String? = null,
    val groupName: String? = null
) {
    val isActive: Boolean = presetId != null || groupName != null

    companion object {
        val All = ChannelCategoryFilter()
    }
}

object ChannelCategoryPresets {

    data class Preset(val id: String, val label: String, val matcher: (String) -> Boolean)

    private val usWord = Regex("""\bus\b""")
    private val ukWord = Regex("""\buk\b""")
    private val caWord = Regex("""\bca\b""")

    val presets: List<Preset> = listOf(
        Preset("usa", "USA") { group ->
            val g = group.lowercase()
            g.contains("usa") || g.contains("united states") || g.contains("u.s.a") ||
                g.contains("american") || usWord.containsMatchIn(g) ||
                g.startsWith("us|") || g.startsWith("us ") || g.startsWith("us:") ||
                g.startsWith("us-") || g.startsWith("us★") || g.startsWith("usa★") ||
                g.contains("|us|") || g.contains("| us") || g.endsWith("|us") ||
                g == "us" || g == "usa"
        },
        Preset("canada", "Canada") { group ->
            val g = group.lowercase()
            g.contains("canada") || caWord.containsMatchIn(g) ||
                g.startsWith("ca|") || g.startsWith("ca ") || g.startsWith("ca★") ||
                g.contains("|ca|") || g == "ca"
        },
        Preset("uk", "UK") { group ->
            val g = group.lowercase()
            g.contains("united kingdom") || ukWord.containsMatchIn(g) ||
                g.contains("|gb|") || g.startsWith("gb|") || g.startsWith("gb ") ||
                g.startsWith("uk|") || g.startsWith("uk ") || g.startsWith("uk★") ||
                g.contains("|uk|") || g == "uk" || g == "gb"
        },
        Preset("news", "News") { group ->
            group.lowercase().contains("news")
        },
        Preset("sports", "Sports") { group ->
            group.lowercase().contains("sport")
        },
        Preset("entertainment", "Entertainment") { group ->
            val g = group.lowercase()
            g.contains("entertain") || g.contains("general") || g.contains("variety")
        },
        Preset("movies", "Movies") { group ->
            val g = group.lowercase()
            g.contains("movie") || g.contains("cinema") || g.contains("film")
        },
        Preset("kids", "Kids") { group ->
            val g = group.lowercase()
            g.contains("kid") || g.contains("children") || g.contains("family")
        }
    )

    fun matches(presetId: String, groupName: String): Boolean =
        presets.firstOrNull { it.id == presetId }?.matcher?.invoke(groupName) == true

    fun fromPreset(id: String): ChannelCategoryFilter {
        val preset = presets.first { it.id == id }
        return ChannelCategoryFilter(label = preset.label, presetId = id)
    }

    fun fromGroup(name: String): ChannelCategoryFilter =
        ChannelCategoryFilter(label = name, groupName = name)

    fun apply(channels: List<Channel>, filter: ChannelCategoryFilter): List<Channel> = when {
        filter.presetId != null -> channels.filter { matches(filter.presetId, it.group) }
        filter.groupName != null -> channels.filter { it.group.equals(filter.groupName, ignoreCase = true) }
        else -> channels
    }
}
