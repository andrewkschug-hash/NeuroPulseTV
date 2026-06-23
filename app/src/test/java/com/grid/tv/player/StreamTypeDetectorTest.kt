package com.grid.tv.player

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamTypeDetectorTest {

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun classify_tsUrl_isProgressiveWithoutNetwork() {
        val detection = StreamTypeDetector.classify(
            url = "http://host/live/channel.ts",
            contentType = null,
            firstBytes = null
        )
        assertEquals(IptvStreamFormat.PROGRESSIVE, detection.format)
    }

    @Test
    fun classify_m3u8WithExtM3u_isHls() {
        val detection = StreamTypeDetector.classify(
            url = "http://host/live/stream.m3u8",
            contentType = null,
            firstBytes = "#EXTM3U\n#EXTINF:10,\n"
        )
        assertEquals(IptvStreamFormat.HLS, detection.format)
    }

    @Test
    fun classify_m3u8WithMpegurlContentType_isHls() {
        val detection = StreamTypeDetector.classify(
            url = "http://host/live/stream.m3u8",
            contentType = "application/vnd.apple.mpegurl",
            firstBytes = "<html>blocked</html>"
        )
        assertEquals(IptvStreamFormat.HLS, detection.format)
    }

    @Test
    fun classify_m3u8WithHtmlBody_isUnknown() {
        val detection = StreamTypeDetector.classify(
            url = "http://host/live/stream.m3u8",
            contentType = "text/html",
            firstBytes = "<html>403 forbidden</html>"
        )
        assertEquals(IptvStreamFormat.UNKNOWN, detection.format)
    }

    @Test
    fun classify_xtreamLivePathWithoutM3u8_isUnknown() {
        val detection = StreamTypeDetector.classify(
            url = "http://host:8080/live/user/pass/12345",
            contentType = null,
            firstBytes = "#EXTM3U\n"
        )
        assertEquals(IptvStreamFormat.UNKNOWN, detection.format)
    }

    @Test
    fun classify_videoMp2t_isProgressive() {
        val detection = StreamTypeDetector.classify(
            url = "http://host/live/12345",
            contentType = "video/mp2t",
            firstBytes = null
        )
        assertEquals(IptvStreamFormat.PROGRESSIVE, detection.format)
    }

    @Test
    fun isTsUrl_neverRoutesToHls() {
        assertTrue(StreamTypeDetector.isTsUrl("http://host/a.ts"))
        assertFalse(StreamTypeDetector.isTsUrl("http://host/a.m3u8"))
    }
}
