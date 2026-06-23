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

class IptvStreamFormatDetectorTest {

    private val registry = IptvStreamFormatRegistry()

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
    fun detect_m3u8ExtensionWithoutSniff_isUnknown() {
        assertEquals(
            IptvStreamFormat.UNKNOWN,
            IptvStreamFormatDetector.detect("http://cdn.example.com/live/stream.m3u8")
        )
    }

    @Test
    fun detect_m3u8WithRegistryManifest_isHls() {
        registry.putManifestSnippet(
            "http://cdn.example.com/live/stream.m3u8",
            "#EXTM3U\n#EXTINF:10,\n"
        )
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.detect("http://cdn.example.com/live/stream.m3u8", registry = registry)
        )
    }

    @Test
    fun resolveForPlayback_xtreamLivePathWithoutExtension_isUnknown() {
        assertEquals(
            IptvStreamFormat.UNKNOWN,
            IptvStreamFormatDetector.resolveForPlayback("http://host:8080/live/user/pass/12345")
        )
    }

    @Test
    fun resolveForPlayback_m3u8WithRegistryContentType_isHls() {
        registry.putContentType(
            "http://host/stream.m3u8",
            "application/vnd.apple.mpegurl"
        )
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.resolveForPlayback("http://host/stream.m3u8", registry = registry)
        )
    }

    @Test
    fun detectsProgressive_fromTsExtension() {
        assertEquals(
            IptvStreamFormat.PROGRESSIVE,
            IptvStreamFormatDetector.detect("http://host/live/channel.ts")
        )
    }

    @Test
    fun isHlsManifestSnippet_recognizesExtM3u() {
        assertTrue(IptvStreamFormatDetector.isHlsManifestSnippet("#EXTM3U\n#EXTINF:10,\n"))
    }

    @Test
    fun unknownVodMp4_isProgressive() {
        assertFalse(
            IptvStreamFormatDetector.resolveForPlayback("http://vod.example.com/movie/file.mp4").isHls()
        )
    }
}
