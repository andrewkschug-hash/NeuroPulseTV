package com.neuropulse.tv.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvFocusScrollTest {

    @Test
    fun `returns null when focused item is inside safe zone`() {
        val result = calculateFocusScrollTarget(
            currentScroll = 200,
            maxScroll = 1000,
            viewportHeight = 600,
            itemTop = 350f,
            itemBottom = 400f,
            safeZonePx = 80f,
            preferCenter = true
        )
        assertNull(result)
    }

    @Test
    fun `scrolls down when item is below safe zone`() {
        val result = calculateFocusScrollTarget(
            currentScroll = 0,
            maxScroll = 2000,
            viewportHeight = 600,
            itemTop = 900f,
            itemBottom = 950f,
            safeZonePx = 80f,
            preferCenter = true
        )
        assertEquals(625, result)
    }

    @Test
    fun `scrolls up when item is above safe zone`() {
        val result = calculateFocusScrollTarget(
            currentScroll = 800,
            maxScroll = 2000,
            viewportHeight = 600,
            itemTop = 50f,
            itemBottom = 100f,
            safeZonePx = 80f,
            preferCenter = true
        )
        assertEquals(0, result)
    }

    @Test
    fun `uses nearest edge when preferCenter is false`() {
        val result = calculateFocusScrollTarget(
            currentScroll = 0,
            maxScroll = 2000,
            viewportHeight = 600,
            itemTop = 700f,
            itemBottom = 740f,
            safeZonePx = 80f,
            preferCenter = false
        )
        assertEquals(220, result)
    }

    @Test
    fun `preferTopAlign scrolls card top into view when below viewport`() {
        val result = calculateFocusScrollTarget(
            currentScroll = 0,
            maxScroll = 2000,
            viewportHeight = 600,
            itemTop = 700f,
            itemBottom = 1100f,
            safeZonePx = 80f,
            preferTopAlign = true
        )
        assertEquals(620, result)
    }

    @Test
    fun `clamps target to max scroll`() {
        val result = calculateFocusScrollTarget(
            currentScroll = 1800,
            maxScroll = 1900,
            viewportHeight = 600,
            itemTop = 5000f,
            itemBottom = 5050f,
            safeZonePx = 80f,
            preferCenter = true
        )
        assertEquals(1900, result)
    }
}
