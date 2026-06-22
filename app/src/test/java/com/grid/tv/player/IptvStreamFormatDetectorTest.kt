package com.grid.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvStreamFormatDetectorTest {

    private val registry = IptvStreamFormatRegistry()

    @Test
    fun detectsHls_fromM3u8Extension() {
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.detect("http://cdn.example.com/live/stream.m3u8")
        )
    }

    @Test
    fun detectsHls_fromXtreamLivePathWithoutExtension() {
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.resolveForPlayback("http://host:8080/live/user/pass/12345")
        )
    }

    @Test
    fun detectsHls_fromLivePhpPath() {
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.resolveForPlayback("http://host/play/live.php?stream=1")
        )
    }

    @Test
    fun detectsHls_fromRegistryContentType() {
        registry.putContentType(
            "http://host/stream/12345",
            "application/vnd.apple.mpegurl"
        )
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.detect("http://host/stream/12345", registry = registry)
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
    fun tokenizedUrl_withoutExtension_defaultsToHlsForLivePattern() {
        assertEquals(
            IptvStreamFormat.HLS,
            IptvStreamFormatDetector.resolveForPlayback("http://host/stream?id=12345&token=abc")
        )
    }

    @Test
    fun unknownVodMp4_isProgressive() {
        assertFalse(
            IptvStreamFormatDetector.resolveForPlayback("http://vod.example.com/movie/file.mp4").isHls()
        )
    }
}
