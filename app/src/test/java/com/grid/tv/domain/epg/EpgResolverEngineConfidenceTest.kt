package com.grid.tv.domain.epg

import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EpgResolverEngineConfidenceTest {

    private lateinit var engine: EpgResolverEngine

    @Before
    fun setup() {
        engine = EpgResolverEngine(
            channelDao = mockk(relaxed = true),
            programDao = mockk(relaxed = true),
            sourceDao = mockk(relaxed = true),
            suggestionDao = mockk(relaxed = true),
            okHttpClient = OkHttpClient()
        )
    }

    @Test
    fun calculateConfidence_exactNearPartialNoMatch() {
        assertEquals(100, engine.calculateConfidence("BBC One HD", "bbc one"))
        assertEquals(90, engine.calculateConfidence("Sky Sports Main Event", "Sky Sports"))
        assertEquals(85, engine.calculateConfidence("hbo", "hboo"))
        assertEquals(75, engine.calculateConfidence("cnn", "cann"))
        assertEquals(70, engine.calculateConfidence("sky sports one", "uk sky sports one live"))
        assertEquals(55, engine.calculateConfidence("sky sports", "sky arena"))
        assertEquals(0, engine.calculateConfidence("bbc one", "espn deportes"))
    }
}
