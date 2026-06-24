package com.grid.tv.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistContextTest {
    @Test
    fun explicitPlaylistWinsOverActive() {
        assertEquals(5L, PlaylistContext.resolve(explicit = 5L, active = 9L))
        assertEquals(5L, PlaylistContext.resolveOrNull(explicit = 5L, active = 9L))
    }

    @Test
    fun activeUsedWhenExplicitUnspecified() {
        assertEquals(9L, PlaylistContext.resolve(explicit = null, active = 9L))
        assertEquals(9L, PlaylistContext.resolve(explicit = 0L, active = 9L))
        assertEquals(9L, PlaylistContext.resolveOrNull(explicit = null, active = 9L))
    }

    @Test
    fun unspecifiedWhenNeitherScoped() {
        assertEquals(PlaylistContext.UNSPECIFIED, PlaylistContext.resolve(explicit = null, active = 0L))
        assertNull(PlaylistContext.resolveOrNull(explicit = null, active = 0L))
    }
}
