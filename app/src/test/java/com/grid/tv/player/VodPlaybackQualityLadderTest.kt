package com.grid.tv.player

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VodPlaybackQualityLadderTest {

    @Before
    fun setUp() {
        mockkObject(CodecCapabilityChecker)
        every { CodecCapabilityChecker.isVariantSupported(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(CodecCapabilityChecker)
    }

    @Test
    fun build_xtreamMovieUrl_includesHlsAndTranscodeVariants() {
        val original = "http://host.example/movie/user/pass/12345.mkv"
        val ladder = VodPlaybackQualityLadder.build(original, title = "Sample 4K Dolby Vision")

        assertEquals(original, ladder.first().url)
        assertTrue(ladder.any { it.url.endsWith(".m3u8") })
        assertTrue(ladder.any { it.url.contains("bitrate=2500") })
        assertTrue(ladder.any { it.url.contains("bitrate=800") })
    }

    @Test
    fun build_nonXtreamUrl_keepsOriginalOnly() {
        val original = "https://cdn.example/vod/movie.mp4"
        val ladder = VodPlaybackQualityLadder.build(original, title = null)

        assertEquals(1, ladder.size)
        assertEquals(original, ladder.single().url)
    }

    @Test
    fun selection_urlsMatchVariantOrder() {
        val original = "http://host.example/movie/user/pass/99.mkv"
        val selection = VodPlaybackQualityLadder.selectInitialVariant(original, title = null)

        assertEquals(selection.variants.map { it.url }, selection.urls)
        assertTrue(selection.selectedIndex in selection.variants.indices)
    }
}
