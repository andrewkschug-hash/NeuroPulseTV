package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VodProgressKeysTest {
    @Test
    fun legacySyntheticIdUsesBareStreamId() {
        val streamId = 42_424L
        assertEquals(-streamId, VodProgressKeys.syntheticChannelId(0L, streamId))
        val decoded = VodProgressKeys.decode(-streamId)
        assertEquals(0L, decoded.playlistId)
        assertEquals(streamId, decoded.streamId)
    }

    @Test
    fun scopedSyntheticIdRoundTrips() {
        val playlistId = 7L
        val streamId = 99_001L
        val channelId = VodProgressKeys.syntheticChannelId(playlistId, streamId)
        val decoded = VodProgressKeys.decode(channelId)
        assertEquals(playlistId, decoded.playlistId)
        assertEquals(streamId, decoded.streamId)
    }

    @Test
    fun distinctPlaylistsDoNotCollide() {
        val streamId = 1_000L
        val a = VodProgressKeys.syntheticChannelId(1L, streamId)
        val b = VodProgressKeys.syntheticChannelId(2L, streamId)
        assert(a != b)
    }
}

class ContinueWatchingKeysTest {
    @Test
    fun scopedMovieKeyIncludesPlaylist() {
        assertEquals("movie:3:100", ContinueWatchingKeys.movieContentKey(3L, 100L))
    }

    @Test
    fun legacyMovieKeyOmitsPlaylist() {
        assertEquals("movie:100", ContinueWatchingKeys.legacyMovieContentKey(100L))
        assertEquals("movie:100", ContinueWatchingKeys.movieContentKey(0L, 100L))
    }

    @Test
    fun scopedSeriesKeyIncludesPlaylist() {
        assertEquals(
            "series:2:50:1:3",
            ContinueWatchingKeys.seriesContentKey(2L, 50L, 1, 3)
        )
    }
}
