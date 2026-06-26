package com.grid.tv.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodPosterFocusLayoutTest {

    @Test
    fun scaleOverflow_matchesFocusScale() {
        assertEquals(5.6f, VodPosterFocusLayout.scaleOverflowX.value, 0.01f)
        assertEquals(8.4f, VodPosterFocusLayout.scaleOverflowY.value, 0.01f)
    }

    @Test
    fun netflixEdgePadding_coversScaleAndBorder() {
        val layout = VodPosterFocusLayout
        assertTrue(
            layout.netflixEdgePaddingHorizontal.value >=
                layout.scaleOverflowX.value + layout.NETFLIX_FOCUS_BORDER.value
        )
        assertTrue(
            layout.netflixEdgePaddingVertical.value >=
                layout.scaleOverflowY.value + layout.NETFLIX_FOCUS_BORDER.value
        )
    }

    @Test
    fun netflixCardHeight_includesTitleBelowScaledPoster() {
        val layout = VodPosterFocusLayout
        val expected =
            layout.netflixEdgePaddingVertical.value * 2f +
                layout.POSTER_HEIGHT.value +
                layout.scaleOverflowY.value +
                layout.POSTER_TITLE_GAP.value +
                layout.POSTER_TITLE_HEIGHT.value
        assertEquals(expected, layout.netflixCardHeight.value, 0.01f)
    }

    @Test
    fun focusSafePixels_atCommonTvDensities() {
        val edgeV = VodPosterFocusLayout.netflixEdgePaddingVertical
        // 720p-class mdpi
        assertEquals(11, VodPosterFocusLayout.dpToPx(160, edgeV))
        // Android TV 1080p tvdpi
        assertEquals(15, VodPosterFocusLayout.dpToPx(213, edgeV))
        // 720p hdpi
        assertEquals(17, VodPosterFocusLayout.dpToPx(240, edgeV))
        // 1080p xhdpi
        assertEquals(23, VodPosterFocusLayout.dpToPx(320, edgeV))
    }

    @Test
    fun gridCardSlot_matchesVodPosterCardLayout() {
        val layout = VodPosterFocusLayout
        assertEquals(116f, layout.gridCardWidth.value, 0.01f)
        assertEquals(212f, layout.gridCardHeight.value, 0.01f)
    }

    @Test
    fun skeletonSlot_matchesNetflixPosterCardLayout() {
        val layout = VodPosterFocusLayout
        val expectedWidth = layout.POSTER_WIDTH.value + layout.netflixEdgePaddingHorizontal.value * 2f
        val expectedHeight =
            layout.netflixEdgePaddingVertical.value * 2f +
                layout.POSTER_HEIGHT.value +
                layout.scaleOverflowY.value +
                layout.POSTER_TITLE_GAP.value +
                layout.POSTER_TITLE_HEIGHT.value
        assertEquals(expectedWidth, layout.netflixCardWidth.value, 0.01f)
        assertEquals(expectedHeight, layout.netflixCardHeight.value, 0.01f)
    }

    @Test
    fun scaledPosterVisualSize_at1080pTvdpi() {
        val dpi = 213
        val scaledW = VodPosterFocusLayout.dpToPx(
            dpi,
            VodPosterFocusLayout.POSTER_WIDTH * VodPosterFocusLayout.POSTER_FOCUS_SCALE
        )
        val scaledH = VodPosterFocusLayout.dpToPx(
            dpi,
            VodPosterFocusLayout.POSTER_HEIGHT * VodPosterFocusLayout.POSTER_FOCUS_SCALE
        )
        assertEquals(164, scaledW)
        assertEquals(246, scaledH)
    }
}
