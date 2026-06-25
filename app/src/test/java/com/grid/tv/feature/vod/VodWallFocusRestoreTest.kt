package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodWallItem
import com.grid.tv.domain.model.VodWallRow
import org.junit.Assert.assertEquals
import org.junit.Test

class VodWallFocusRestoreTest {

    @Test
    fun resolveVodWallFocus_restoresByContentKey() {
        val movie = VodItem(
            id = 1L,
            streamId = 42L,
            playlistId = 1L,
            title = "EN - Test",
            streamUrl = "http://test",
            posterUrl = null,
            plot = null,
            cast = null,
            director = null,
            categoryId = null,
            rating = null,
            duration = null,
            genre = null
        )
        val rows = listOf(
            VodWallRow(
                id = "recommended",
                title = "Recommended",
                items = listOf(VodWallItem.MovieItem(movie))
            )
        )
        val (row, col) = resolveVodWallFocus(
            wallRows = rows,
            savedContentKey = "movie_1_42",
            fallbackRow = 0,
            fallbackCol = 0
        )
        assertEquals(0, row)
        assertEquals(0, col)
    }

    @Test
    fun resolveVodWallFocus_fallsBackWhenKeyMissing() {
        val rows = listOf(
            VodWallRow(id = "trending", title = "Trending", items = emptyList())
        )
        val (row, col) = resolveVodWallFocus(
            wallRows = rows,
            savedContentKey = "missing_key",
            fallbackRow = 0,
            fallbackCol = 3
        )
        assertEquals(0, row)
        assertEquals(0, col)
    }
}
