package com.grid.tv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiStateTest {

    @Test
    fun shouldShowNoResults_onlyWhenSearchCompleteAndEmpty() {
        val state = SearchUiState.Active(
            query = "espn",
            isSearching = false,
            results = UnifiedSearchResults(),
            lastCompletedQuery = "espn",
            inFlightQuery = "espn",
            channelsReady = true,
            vodReady = true,
            seriesReady = true,
        )
        assertTrue(state.shouldShowNoResults)
    }

    @Test
    fun shouldShowNoResults_falseWhileSearching() {
        val state = SearchUiState.Active(
            query = "espn",
            isSearching = true,
            results = UnifiedSearchResults(),
            lastCompletedQuery = "",
            inFlightQuery = "espn",
            channelsReady = false,
            vodReady = false,
            seriesReady = false,
        )
        assertFalse(state.shouldShowNoResults)
        assertTrue(state.shouldShowSearching)
    }

    @Test
    fun shouldShowNoResults_falseWhenQueryDiffersFromLastCompleted() {
        val state = SearchUiState.Active(
            query = "espn2",
            isSearching = false,
            results = UnifiedSearchResults(),
            lastCompletedQuery = "espn",
            inFlightQuery = "espn2",
        )
        assertFalse(state.shouldShowNoResults)
    }

    @Test
    fun displayResults_showsProgressiveRowsForInFlightQuery() {
        val partial = UnifiedSearchResults(
            channels = listOf(
                SearchResultItem(
                    id = "ch-1",
                    primaryTitle = "ESPN",
                    secondaryLine = "Channel",
                    imageUrl = null,
                    type = SearchResultType.CHANNEL,
                    channelId = 1L,
                )
            )
        )
        val state = SearchUiState.Active(
            query = "espn",
            isSearching = true,
            results = partial,
            lastCompletedQuery = "",
            inFlightQuery = "espn",
            channelsReady = true,
            vodReady = false,
            seriesReady = false,
            hasAnyResults = true,
        )
        assertTrue(state.displayResults.channels.isNotEmpty())
        assertFalse(state.shouldShowNoResults)
    }

    @Test
    fun displayResults_hidesStaleRowsWhileSearchingNewQuery() {
        val stale = UnifiedSearchResults(
            channels = listOf(
                SearchResultItem(
                    id = "ch-1",
                    primaryTitle = "ESPN",
                    secondaryLine = "Channel",
                    imageUrl = null,
                    type = SearchResultType.CHANNEL,
                    channelId = 1L,
                )
            )
        )
        val state = SearchUiState.Active(
            query = "cnn",
            isSearching = true,
            results = stale,
            lastCompletedQuery = "espn",
            inFlightQuery = "cnn",
        )
        assertTrue(state.displayResults.channels.isEmpty())
    }

    @Test
    fun displayResults_showsRowsWhenSearchComplete() {
        val results = UnifiedSearchResults(
            channels = listOf(
                SearchResultItem(
                    id = "ch-1",
                    primaryTitle = "ESPN",
                    secondaryLine = "Channel",
                    imageUrl = null,
                    type = SearchResultType.CHANNEL,
                    channelId = 1L,
                )
            )
        )
        val state = SearchUiState.Active(
            query = "espn",
            isSearching = false,
            results = results,
            lastCompletedQuery = "espn",
            inFlightQuery = "espn",
            channelsReady = true,
            vodReady = true,
            seriesReady = true,
            hasAnyResults = true,
        )
        assertTrue(state.displayResults.channels.isNotEmpty())
    }
}
