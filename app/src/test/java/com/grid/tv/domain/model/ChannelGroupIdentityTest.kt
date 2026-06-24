package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChannelGroupIdentityTest {
    @Test
    fun groupKeyRoundTrips() {
        val key = ChannelGroupIdentity.groupKey(3L, "Sports")
        val (playlistId, groupName) = ChannelGroupIdentity.parseGroupKey(key)
        assertEquals(3L, playlistId)
        assertEquals("Sports", groupName)
    }

    @Test
    fun matchesRequiresPlaylistScope() {
        val channel = Channel(
            id = 1L,
            number = 1,
            name = "ESPN",
            group = "Sports",
            logoUrl = null,
            epgId = null,
            streamUrl = "http://example.com",
            playlistId = 2L,
            isFavorite = false
        )
        val samePlaylist = ChannelGroupIdentity.groupKey(2L, "Sports")
        val otherPlaylist = ChannelGroupIdentity.groupKey(9L, "Sports")
        assert(ChannelGroupIdentity.matches(channel, samePlaylist))
        assertFalse(ChannelGroupIdentity.matches(channel, otherPlaylist))
    }
}

class VodSearchIdentityTest {
    @Test
    fun vodResultIdIncludesPlaylist() {
        assertEquals("vod-7-100", VodSearchIdentity.vodResultId(7L, 100L))
    }

    @Test
    fun dedupKeysDoNotCollideAcrossPlaylists() {
        val a = VodSearchIdentity.vodDedupKey(1L, 50L)
        val b = VodSearchIdentity.vodDedupKey(2L, 50L)
        assert(a != b)
    }
}
