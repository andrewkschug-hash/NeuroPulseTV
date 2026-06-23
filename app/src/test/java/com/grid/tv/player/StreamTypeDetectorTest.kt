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

    @Test
    fun applyVodOverride_unknownMapsToProgressive() {
        val overridden = StreamTypeDetector.applyVodOverride(
            StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                reason = "insufficient_signal"
            )
        )
        assertEquals(IptvStreamFormat.PROGRESSIVE, overridden.format)
        assertEquals("vod_unknown_progressive_fallback", overridden.reason)
    }

    @Test
    fun applyVodOverride_hlsUnchanged() {
        val detection = StreamTypeDetector.Detection(
            format = IptvStreamFormat.HLS,
            reason = "m3u8_url+manifest_or_mpegurl"
        )
        assertEquals(detection, StreamTypeDetector.applyVodOverride(detection))
    }

    @Test
    fun isVodProgressiveUrl_recognizesMkvMp4Mov() {
        assertTrue(StreamTypeDetector.isVodProgressiveUrl("http://host/movie/1.mkv"))
        assertTrue(StreamTypeDetector.isVodProgressiveUrl("http://host/movie/1.mp4"))
        assertTrue(StreamTypeDetector.isVodProgressiveUrl("http://host/movie/1.mov"))
        assertFalse(StreamTypeDetector.isVodProgressiveUrl("http://host/live/1.m3u8"))
    }

    @Test
    fun isVodProgressiveContentType_recognizesMatroskaAndOctetStream() {
        assertTrue(StreamTypeDetector.isVodProgressiveContentType("video/x-matroska"))
        assertTrue(StreamTypeDetector.isVodProgressiveContentType("video/mp4"))
        assertTrue(StreamTypeDetector.isVodProgressiveContentType("application/octet-stream"))
        assertFalse(StreamTypeDetector.isVodProgressiveContentType("text/html"))
    }

    @Test
    fun isFatalVodPreflightBlock_http4xxAndHtml() {
        assertTrue(
            StreamTypeDetector.isFatalVodPreflightBlock(
                httpCode = 403,
                contentType = "video/mp4",
                firstBytes = "binary"
            )
        )
        assertTrue(
            StreamTypeDetector.isFatalVodPreflightBlock(
                httpCode = 200,
                contentType = "text/html",
                firstBytes = "<html><body>login</body></html>"
            )
        )
        assertTrue(
            StreamTypeDetector.isFatalVodPreflightBlock(
                httpCode = 200,
                contentType = "video/mp4",
                firstBytes = ""
            )
        )
        assertFalse(
            StreamTypeDetector.isFatalVodPreflightBlock(
                httpCode = 200,
                contentType = "video/x-matroska",
                firstBytes = "\u0000\u0000\u0000\u001c"
            )
        )
    }
}
