package com.grid.tv.domain.epg

import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.EpgResolutionSuggestionDao
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.dao.ProgramDao
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EpgResolverEngineNormalizeTest {

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
    fun normalizeChannelName_realWorldExamples() {
        val cases = listOf(
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
            assertEquals(expected, engine.normalizeChannelName(input))
        }
    }
}
