package com.grid.tv.feature.vod

import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.categoryBrowseRowId
import org.junit.Assert.assertEquals
import org.junit.Test

class VodBrowseRowCategoryIndexTest {

    @Test
    fun fromBrowseRows_indexesCountsAndCategoriesByCategoryKey() {
        val movie = VodItem(
            id = 1L,
            streamId = 1L,
            playlistId = 1L,
            title = "Movie",
            streamUrl = "http://example.com/movie",
            posterUrl = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            rating = null,
            duration = null,
            categoryId = "1006",
        )
        val show = SeriesShow(
            id = 2L,
            name = "Show",
            coverUrl = null,
            categoryId = "1040",
            playlistId = 1L,
        )
        val rows = listOf(
            VodBrowseRow(
                id = categoryBrowseRowId(1L, "1006"),
                title = "Action",
                movies = listOf(movie, movie, movie),
            ),
            VodBrowseRow(
                id = categoryBrowseRowId(1L, "1040"),
                title = "Drama",
                series = listOf(show, show),
            ),
        )

        val index = VodBrowseRowCategoryIndex.fromBrowseRows(rows)

        assertEquals(3, index.itemCountByCategoryKey[VodCategoryNameResolver.categoryKey(1L, "1006")])
        assertEquals(2, index.itemCountByCategoryKey[VodCategoryNameResolver.categoryKey(1L, "1040")])
        assertEquals(2, index.categoriesFromRows.size)
    }
}
