package com.grid.tv.feature.vod

import com.grid.tv.domain.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodHubHeroResolverTest {

    @Test
    fun itemAt_emptyCarousel_returnsNull() {
        assertNull(VodHubHeroResolver.itemAt(emptyList(), 0))
    }

    @Test
    fun itemAt_clampsIndex() {
        val items = listOf(
            VodItem(id = 1L, title = "A", streamId = 1L, streamUrl = "", posterUrl = null, plot = null, cast = null, director = null, genre = null, rating = null, duration = null, playlistId = 1L),
            VodItem(id = 2L, title = "B", streamId = 2L, streamUrl = "", posterUrl = null, plot = null, cast = null, director = null, genre = null, rating = null, duration = null, playlistId = 1L)
        )
        assertEquals("A", VodHubHeroResolver.itemAt(items, -1)?.title)
        assertEquals("B", VodHubHeroResolver.itemAt(items, 99)?.title)
        assertEquals("B", VodHubHeroResolver.itemAt(items, 1)?.title)
    }

    @Test
    fun enrichmentFor_invalidPlaylist_returnsNull() {
        val item = VodItem(id = 1L, title = "A", streamId = 1L, streamUrl = "", posterUrl = null, plot = null, cast = null, director = null, genre = null, rating = null, duration = null, playlistId = 0L)
        assertNull(VodHubHeroResolver.enrichmentFor(item, emptyMap()))
        assertNull(VodHubHeroResolver.enrichmentFor(null, emptyMap()))
    }
}
