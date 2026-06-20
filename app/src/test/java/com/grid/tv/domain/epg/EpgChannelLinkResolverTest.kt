package com.grid.tv.domain.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgChannelLinkResolverTest {

    private val normalizer = ChannelNameNormalizer()

    @Test
    fun resolve_matchesNormalizedId() {
        val resolver = EpgChannelLinkResolver(
            listOf(
                XmlTvChannelRef("BBC.One.uk", "BBC One"),
                XmlTvChannelRef("espn.us", "ESPN")
            ),
            normalizer = normalizer
        )
        val result = resolver.resolve("bbc.one.uk", null)
        assertEquals("BBC.One.uk", result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.EXACT_ID, result.reason)
    }

    @Test
    fun resolve_matchesNormalizedDisplayName() {
        val resolver = EpgChannelLinkResolver(
            listOf(
                XmlTvChannelRef("BBC.One.uk", "BBC One"),
                XmlTvChannelRef("espn.us", "ESPN")
            ),
            normalizer = normalizer
        )
        val result = resolver.resolve(null, "ESPN")
        assertEquals("espn.us", result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.NORMALIZED_NAME, result.reason)
    }

    @Test
    fun resolve_fuzzyMatchesWhenTvgIdMissingFromEpg() {
        val resolver = EpgChannelLinkResolver(
            listOf(
                XmlTvChannelRef("ohl01.ca", "OHL 01"),
                XmlTvChannelRef("ohl02.ca", "OHL 02")
            ),
            normalizer = normalizer
        )
        val result = resolver.resolve("1948726", "OHL 01")
        assertEquals("ohl01.ca", result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.NORMALIZED_NAME, result.reason)
    }

    @Test
    fun resolve_fuzzyMatchesSimilarDisplayName() {
        val resolver = EpgChannelLinkResolver(
            listOf(
                XmlTvChannelRef("ohl01.ca", "OHL Ontario Hockey League 01")
            ),
            normalizer = normalizer
        )
        val result = resolver.resolve("1948726", "OHL 01")
        assertEquals("ohl01.ca", result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.FUZZY_NAME, result.reason)
    }

    @Test
    fun resolve_usesLearnedMappingBeforeFuzzy() {
        val resolver = EpgChannelLinkResolver(
            listOf(
                XmlTvChannelRef("ohl01.ca", "OHL Ontario Hockey League 01"),
                XmlTvChannelRef("other.ca", "Other Channel")
            ),
            learnedMappings = mapOf("ohl 1" to "ohl01.ca"),
            normalizer = normalizer
        )
        val result = resolver.resolve("1948726", "OHL 01")
        assertEquals("ohl01.ca", result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.LEARNED_MAPPING, result.reason)
    }

    @Test
    fun resolve_returnsNoMatchWhenNothingFound() {
        val resolver = EpgChannelLinkResolver(
            listOf(
                XmlTvChannelRef("BBC.One.uk", "BBC One"),
                XmlTvChannelRef("espn.us", "ESPN")
            ),
            normalizer = normalizer
        )
        val result = resolver.resolve("missing.id", "Unknown Channel")
        assertEquals(null, result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.NO_MATCH, result.reason)
    }
}
