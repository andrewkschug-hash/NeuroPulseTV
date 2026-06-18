package com.grid.tv.domain.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgChannelLinkResolverTest {

    private val resolver = EpgChannelLinkResolver(
        listOf(
            XmlTvChannelRef("BBC.One.uk", "BBC One"),
            XmlTvChannelRef("espn.us", "ESPN")
        )
    )

    @Test
    fun resolve_matchesNormalizedId() {
        val result = resolver.resolve("bbc.one.uk", null)
        assertEquals("BBC.One.uk", result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.NORMALIZED_ID, result.reason)
    }

    @Test
    fun resolve_matchesNormalizedDisplayName() {
        val result = resolver.resolve(null, "ESPN")
        assertEquals("espn.us", result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.NORMALIZED_NAME, result.reason)
    }

    @Test
    fun resolve_returnsNoMatchWhenNothingFound() {
        val result = resolver.resolve("missing.id", "Unknown Channel")
        assertEquals(null, result.xmlTvChannelId)
        assertEquals(EpgLinkMatchReason.NO_MATCH, result.reason)
    }
}
