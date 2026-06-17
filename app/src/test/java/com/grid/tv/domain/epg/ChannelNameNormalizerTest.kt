package com.grid.tv.domain.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNameNormalizerTest {

    private val normalizer = ChannelNameNormalizer()

    @Test
    fun normalizeChannelName_realWorldExamples() {
        val cases = listOf(
            "ESPN HD" to "espn",
            "TSN 5 CA" to "tsn 5",
            "BBC One HD" to "bbc one",
            "US: CNN FHD" to "cnn",
            "Sky Sports 1 +1" to "sky sports 1",
            "Canal+ FR" to "canal",
            "[UK] ITV 1 HD" to "itv 1",
            "(US) HBO 1080p" to "hbo",
            "Discovery Channel UHD" to "discovery",
            "Network 10 AU" to "10",
            "TV3 NZ" to "3",
            "Fox News Channel" to "fox news",
            "Eurosport 4K" to "eurosport",
            "RTE One IE" to "rte one",
            "MTV HD" to "mtv",
            "CBBC SD" to "cbbc",
            "Sky Cinema Action +24" to "sky cinema action",
            "ABC | US" to "abc",
            "[CA] TSN 2" to "tsn 2",
            "Zee TV HD" to "zee",
            "TVP Info PL" to "tvp info pl",
            "MBC 1 HQ" to "mbc 1",
            "CBeebies (UK)" to "cbeebies",
            "Channel 5 UK" to "5"
        )

        cases.forEach { (input, expected) ->
            assertEquals("Failed for $input", expected, normalizer.normalize(input))
        }
    }

    @Test
    fun calculateConfidence_exactNearPartialNoMatch() {
        assertEquals(100, normalizer.calculateConfidence("BBC One HD", "bbc one"))
        assertEquals(90, normalizer.calculateConfidence("Sky Sports Main Event", "Sky Sports"))
        assertEquals(85, normalizer.calculateConfidence("hbo", "hboo"))
        assertEquals(75, normalizer.calculateConfidence("cnn", "cann"))
        assertEquals(70, normalizer.calculateConfidence("sky sports one", "uk sky sports one live"))
        assertEquals(55, normalizer.calculateConfidence("sky sports", "sky arena"))
        assertEquals(0, normalizer.calculateConfidence("bbc one", "espn deportes"))
    }
}
