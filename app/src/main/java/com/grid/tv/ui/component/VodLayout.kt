package com.grid.tv.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared vertical rhythm for every VOD hub surface (Home wall, Movies/Series grids, Search, language views).
 * Apply via scroll container [contentPadding], not per-row modifiers.
 */
object VodLayout {
    val ContentTopPadding = 24.dp
    val ContentBottomPadding = 64.dp
    val ContentHorizontalPadding = 16.dp
    val RowSpacing = 20.dp
    val SectionSpacing = 32.dp
    val PosterSpacing = 12.dp

    fun scrollContentPadding(bottom: Dp = ContentBottomPadding): PaddingValues =
        PaddingValues(
            start = ContentHorizontalPadding,
            end = ContentHorizontalPadding,
            top = ContentTopPadding,
            bottom = bottom,
        )

    fun wallLazyColumnPadding(): PaddingValues =
        PaddingValues(
            start = ContentHorizontalPadding,
            end = ContentHorizontalPadding,
            top = ContentTopPadding,
            bottom = ContentBottomPadding,
        )

    fun gridContentPadding(): PaddingValues =
        PaddingValues(
            start = ContentHorizontalPadding,
            end = ContentHorizontalPadding,
            top = ContentTopPadding,
            bottom = ContentBottomPadding,
        )
}
