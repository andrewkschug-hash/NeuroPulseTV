package com.grid.tv.data.cache

import com.grid.tv.data.db.entity.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodPlaylistCacheManifestTest {

    @Test
    fun versionKey_changesWhenCredentialsChange() {
        val playlist = PlaylistEntity(
            id = 1L,
            name = "Test",
            url = "http://example.com",
            xtreamServerUrl = "http://server.com",
            xtreamUsername = "user1",
        )
        val other = playlist.copy(xtreamUsername = "user2")
        assertNotEquals(
            vodPlaylistCacheVersionKey(playlist),
            vodPlaylistCacheVersionKey(other),
        )
    }

    @Test
    fun manifest_isFreshWithinTtl() {
        val now = 1_000_000L
        val manifest = VodPlaylistCacheManifest(
            playlistId = 1L,
            playlistVersionKey = "abc",
            savedAtMs = now,
            moviesCount = 100,
            seriesCount = 6800,
            moviesContentFingerprint = "m",
            seriesContentFingerprint = "s",
        )
        assertTrue(manifest.isFresh(nowMs = now + 1_000L, ttlMs = 6 * 60 * 60 * 1000L))
        assertFalse(manifest.isFresh(nowMs = now + 7 * 60 * 60 * 1000L, ttlMs = 6 * 60 * 60 * 1000L))
    }

    @Test
    fun sha256Hex_isStable() {
        assertEquals(
            sha256Hex("hello"),
            sha256Hex("hello"),
        )
    }
}
