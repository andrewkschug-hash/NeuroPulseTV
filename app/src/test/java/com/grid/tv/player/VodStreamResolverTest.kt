package com.grid.tv.player

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VodStreamResolverTest {

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        mockkObject(CodecCapabilityChecker)
        every { CodecCapabilityChecker.isVariantSupported(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        unmockkObject(CodecCapabilityChecker)
    }

    @Test
    fun resolve_matroskaContentType_isProgressiveWithoutOverride() {
        val resolved = VodStreamResolver.resolve(
            url = "http://host/movie/12345",
            detection = StreamTypeDetector.Detection(
                format = IptvStreamFormat.PROGRESSIVE,
                contentType = "video/x-matroska",
                reason = "vod_content_type"
            )
        )

        requireNotNull(resolved)
        assertEquals(VodMediaSourceType.PROGRESSIVE, resolved.mediaSourceType)
        assertEquals("video/x-matroska", resolved.resolutionSource)
        assertFalse(resolved.wasUnknownOverride)
    }

    @Test
    fun resolve_unknownInsufficientSignal_appliesProgressiveOverride() {
        val resolved = VodStreamResolver.resolve(
            url = "http://host/movie/12345.mkv",
            detection = StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                contentType = "application/octet-stream",
                reason = "insufficient_signal"
            )
        )

        requireNotNull(resolved)
        assertEquals(VodMediaSourceType.PROGRESSIVE, resolved.mediaSourceType)
        assertEquals("application/octet-stream", resolved.resolutionSource)
        assertTrue(resolved.wasUnknownOverride)
    }

    @Test
    fun resolve_http403_isBlocked() {
        val resolved = VodStreamResolver.resolve(
            url = "http://host/movie/1.mkv",
            detection = StreamTypeDetector.Detection(
                format = IptvStreamFormat.UNKNOWN,
                reason = "http_403"
            )
        )
        assertNull(resolved)
    }

    @Test
    fun resolve_m3u8Manifest_isHls() {
        val resolved = VodStreamResolver.resolve(
            url = "http://host/movie/1.m3u8",
            detection = StreamTypeDetector.Detection(
                format = IptvStreamFormat.HLS,
                contentType = "application/vnd.apple.mpegurl",
                reason = "m3u8_url+manifest_or_mpegurl"
            )
        )

        requireNotNull(resolved)
        assertEquals(VodMediaSourceType.HLS, resolved.mediaSourceType)
        assertFalse(resolved.wasUnknownOverride)
    }
}
