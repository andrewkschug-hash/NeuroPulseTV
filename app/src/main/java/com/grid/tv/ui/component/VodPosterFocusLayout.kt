package com.grid.tv.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Focus-safe layout math for VOD poster cards.
 *
 * Netflix-style rows use [POSTER_FOCUS_SCALE] (1.08×) plus a 3dp TV focus border.
 * Grid cards use [GRID_FOCUS_BORDER] with no scale.
 *
 * Pixel equivalents at common TV densities (px = dp × densityDpi / 160):
 * | Density | Typical device   | 10dp edge inset |
 * |---------|------------------|-----------------|
 * | 160 mdpi| 720p (some)      | 10px            |
 * | 213 tvdpi| Android TV 1080p| 13px            |
 * | 240 hdpi| 720p             | 15px            |
 * | 320 xhdpi| 1080p panels   | 20px            |
 */
object VodPosterFocusLayout {
    const val POSTER_FOCUS_SCALE = 1.08f

    val POSTER_WIDTH = 112.dp
    val POSTER_HEIGHT = 168.dp
    val POSTER_TITLE_HEIGHT = 40.dp
    val POSTER_TITLE_GAP = 4.dp

    val NETFLIX_FOCUS_BORDER = 3.dp
    val GRID_FOCUS_BORDER = 2.dp

    fun scaleOverflow(dimension: Dp, scale: Float = POSTER_FOCUS_SCALE): Dp =
        ((dimension.value * (scale - 1f)) / 2f).dp

    val scaleOverflowX: Dp get() = scaleOverflow(POSTER_WIDTH)
    val scaleOverflowY: Dp get() = scaleOverflow(POSTER_HEIGHT)

    /** Horizontal inset so scaled poster + border are not clipped by sibling items. */
    val netflixEdgePaddingHorizontal: Dp get() = scaleOverflowX + NETFLIX_FOCUS_BORDER

    /** Vertical inset so scaled poster + border are not clipped by row title / adjacent rows. */
    val netflixEdgePaddingVertical: Dp get() = scaleOverflowY + NETFLIX_FOCUS_BORDER

    val gridEdgePadding: Dp get() = GRID_FOCUS_BORDER

    /** Two-line title band below the poster in [VodPosterCard] (12sp + 6dp padding). */
    val gridPosterTitleBandHeight = 40.dp

    val gridCardWidth: Dp get() = POSTER_WIDTH + gridEdgePadding * 2

    val gridCardHeight: Dp get() = gridEdgePadding * 2 + POSTER_HEIGHT + gridPosterTitleBandHeight

    val categoryRowTopPadding = 10.dp
    val categoryRowBottomPadding = 16.dp
    val categoryTitleBottomGap = 14.dp

    val lazyRowVerticalPadding: Dp get() = netflixEdgePaddingVertical

    val netflixCardWidth: Dp get() = POSTER_WIDTH + netflixEdgePaddingHorizontal * 2

    val netflixCardHeight: Dp
        get() = netflixEdgePaddingVertical * 2 +
            POSTER_HEIGHT +
            scaleOverflowY +
            POSTER_TITLE_GAP +
            POSTER_TITLE_HEIGHT

    fun dpToPx(densityDpi: Int, dp: Dp): Int =
        kotlin.math.round(dp.value * densityDpi / 160f).toInt()
}
