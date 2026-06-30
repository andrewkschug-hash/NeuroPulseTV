package com.grid.tv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSectionUiTest {

    @Test
    fun snapshot_showsChannelSkeletonWhileChannelsLoading() {
        val state = SearchUiState.Active(
            query = "espn",
            isSearching = true,
            inFlightQuery = "espn",
            channelsReady = false,
        )
        val snapshot = SearchSectionUi.snapshot(state)
        assertTrue(snapshot.showChannelsSkeleton)
        assertTrue(snapshot.channels.isEmpty())
    }

    @Test
    fun snapshot_preservesChannelsWhileVodLoading() {
        val channel = SearchResultItem(
            id = "ch-1",
            primaryTitle = "ESPN",
            secondaryLine = "Channel",
            imageUrl = null,
            type = SearchResultType.CHANNEL,
            channelId = 1L,
        )
        val state = SearchUiState.Active(
            query = "espn",
            isSearching = true,
            inFlightQuery = "espn",
            channelsReady = true,
            vodReady = false,
            results = UnifiedSearchResults(channels = listOf(channel)),
            hasAnyResults = true,
        )
        val snapshot = SearchSectionUi.snapshot(state)
        assertTrue(snapshot.channels.isNotEmpty())
        assertTrue(snapshot.showVodSkeleton)
        assertFalse(snapshot.showChannelsSkeleton)
    }

    @Test
    fun snapshot_hidesStaleRowsForNewQuery() {
        val stale = SearchResultItem(
            id = "ch-1",
            primaryTitle = "ESPN",
            secondaryLine = "Channel",
            imageUrl = null,
            type = SearchResultType.CHANNEL,
            channelId = 1L,
        )
        val state = SearchUiState.Active(
            query = "cnn",
            isSearching = true,
            lastCompletedQuery = "espn",
            inFlightQuery = "cnn",
            results = UnifiedSearchResults(channels = listOf(stale)),
        )
        val snapshot = SearchSectionUi.snapshot(state)
        assertTrue(snapshot.showChannelsSkeleton)
        assertTrue(snapshot.channels.isEmpty())
    }
}
