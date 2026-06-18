package com.grid.tv.data.network.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private val parser = M3uParser()

    @Test
    fun parseHeaderEpgUrl_readsExtM3uAttribute() {
        val content = """
            #EXTM3U x-tvg-url="http://example.com/epg.xml"
            #EXTINF:-1 tvg-id="ch1",Channel 1
            http://stream/1
        """.trimIndent()

        assertEquals("http://example.com/epg.xml", parser.parseHeaderEpgUrl(content))
    }

    @Test
    fun parseHeaderEpgUrl_readsUrlTvgAlias() {
        val content = """
            #EXTM3U url-tvg="https://cdn.example/epg.xml.gz"
            #EXTINF:-1,Channel
            http://x
        """.trimIndent()

        assertEquals("https://cdn.example/epg.xml.gz", parser.parseHeaderEpgUrl(content))
    }

    @Test
    fun parseHeaderEpgUrl_readsExtVlcOpt() {
        val content = """
            #EXTM3U
            #EXTVLCOPT:url-tvg=http://epg.local/xmltv.xml
            #EXTINF:-1,Ch
            http://x
        """.trimIndent()

        assertEquals("http://epg.local/xmltv.xml", parser.parseHeaderEpgUrl(content))
    }

    @Test
    fun parseHeaderEpgUrl_returnsNullWhenMissing() {
        assertNull(parser.parseHeaderEpgUrl("#EXTM3U\n#EXTINF:-1,Ch\nhttp://x"))
    }
}
