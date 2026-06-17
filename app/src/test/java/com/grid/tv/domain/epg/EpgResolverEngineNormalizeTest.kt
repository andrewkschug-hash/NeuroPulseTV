package com.grid.tv.domain.epg

import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.EpgResolutionSuggestionDao
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.dao.ProgramDao
import com.grid.tv.data.network.AppHttpClient
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EpgResolverEngineNormalizeTest {

    private lateinit var engine: EpgResolverEngine

    @Before
    fun setup() {
        val normalizer = ChannelNameNormalizer()
        engine = EpgResolverEngine(
            channelDao = mockk(relaxed = true),
            programDao = mockk(relaxed = true),
            sourceDao = mockk(relaxed = true),
            suggestionDao = mockk(relaxed = true),
            appHttpClient = mockk(relaxed = true),
            normalizer = normalizer,
            matcher = EpgMatcher(normalizer, mockk(relaxed = true), mockk(relaxed = true)),
            canonicalSeeder = CanonicalChannelSeeder(mockk(relaxed = true), normalizer),
            analyticsTracker = EpgMatchAnalyticsTracker(
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                normalizer
            )
        )
    }

    @Test
    fun normalizeChannelName_delegatesToNormalizer() {
        assertEquals("espn", engine.normalizeChannelName("ESPN HD"))
        assertEquals("tsn 5", engine.normalizeChannelName("TSN 5 CA"))
    }
}
