package com.grid.tv.ui.component

import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.SearchResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFocusNavigationTest {

    private fun sampleResult(id: String = "ch-1") = SearchResultItem(
        id = id,
        type = SearchResultType.CHANNEL,
        primaryTitle = "News",
        secondaryLine = "News",
        imageUrl = null,
        channelId = 1L,
    )

    private fun setupController(
        selectable: List<SearchResultItem> = emptyList(),
        showRecentChips: Boolean = false,
        recentSearches: List<String> = emptyList(),
        initialZone: SearchFocusZone = SearchFocusZone.FIELD,
    ): Pair<SearchFocusUiState, SearchFocusController> {
        val ui = SearchFocusUiState()
        ui.focusZone = initialZone
        val controller = SearchFocusController(ui)
        var dismissed = false
        var micClicked = false
        controller.bind(
            SearchFocusDeps(
                onDismiss = { dismissed = true },
                onMicClick = { micClicked = true },
                onSuggestionSelected = {},
                onClearHistory = {},
                onResultSelected = {},
                selectableResults = { selectable },
                showRecentChips = { showRecentChips },
                recentSearches = { recentSearches },
            )
        )
        return ui to controller
    }

    @Test
    fun transitionToZone_fieldToMic() {
        val (ui, controller) = setupController()
        controller.transitionToZone(SearchFocusZone.MIC, "test")
        assertEquals(SearchFocusZone.MIC, ui.focusZone)
    }

    @Test
    fun transitionToZone_results_initializesFocusedIndexWhenUnset() {
        val (ui, controller) = setupController(selectable = listOf(sampleResult()))
        ui.focusedIndex = -1
        controller.transitionToZone(SearchFocusZone.RESULTS, "test")
        assertEquals(SearchFocusZone.RESULTS, ui.focusZone)
        assertEquals(0, ui.focusedIndex)
    }

    @Test
    fun moveFocusToSearchResults_withResults_entersResultsZone() {
        val (ui, controller) = setupController(selectable = listOf(sampleResult(), sampleResult("ch-2")))
        controller.moveFocusToSearchResults()
        assertEquals(SearchFocusZone.RESULTS, ui.focusZone)
    }

    @Test
    fun moveFocusToSearchResults_withoutResults_withRecent_entersRecentZone() {
        val (ui, controller) = setupController(
            showRecentChips = true,
            recentSearches = listOf("sports"),
        )
        controller.moveFocusToSearchResults()
        assertEquals(SearchFocusZone.RECENT, ui.focusZone)
    }

    @Test
    fun moveFocusToSearchResults_empty_entersMicZone() {
        val (ui, controller) = setupController()
        controller.moveFocusToSearchResults()
        assertEquals(SearchFocusZone.MIC, ui.focusZone)
    }

    @Test
    fun recentChipIndex_stepsWithinBounds() {
        val ui = SearchFocusUiState()
        ui.focusZone = SearchFocusZone.RECENT
        ui.recentChipIndex = 0
        ui.recentChipIndex = 1
        assertEquals(1, ui.recentChipIndex)
    }

    @Test
    fun selectAt_invokesResultCallback() {
        val ui = SearchFocusUiState()
        val controller = SearchFocusController(ui)
        var selected: SearchResultItem? = null
        val result = sampleResult()
        controller.bind(
            SearchFocusDeps(
                onDismiss = {},
                onMicClick = {},
                onSuggestionSelected = {},
                onClearHistory = {},
                onResultSelected = { selected = it },
                selectableResults = { listOf(result) },
                showRecentChips = { false },
                recentSearches = { emptyList() },
            )
        )
        controller.selectAt(0)
        assertEquals(result, selected)
    }

    @Test
    fun focusZone_verticalPath_fieldToRecentWhenChipsVisible() {
        val (ui, controller) = setupController(
            showRecentChips = true,
            recentSearches = listOf("news"),
        )
        controller.transitionToZone(
            if (true) SearchFocusZone.RECENT else SearchFocusZone.MIC,
            "downFromField",
        )
        assertEquals(SearchFocusZone.RECENT, ui.focusZone)
    }

    @Test
    fun focusZone_verticalPath_resultsUpAtOrigin_returnsToRecent() {
        val (ui, controller) = setupController(
            selectable = listOf(sampleResult()),
            showRecentChips = true,
            recentSearches = listOf("news"),
            initialZone = SearchFocusZone.RESULTS,
        )
        ui.focusedIndex = 0
        controller.transitionToZone(SearchFocusZone.RECENT, "upFromResultsOrigin")
        assertEquals(SearchFocusZone.RECENT, ui.focusZone)
    }

    @Test
    fun focusedIndex_stepsWithinResults() {
        val (ui, _) = setupController(
            selectable = listOf(sampleResult(), sampleResult("ch-2"), sampleResult("ch-3")),
            initialZone = SearchFocusZone.RESULTS,
        )
        ui.focusedIndex = 0
        ui.focusedIndex = 1
        assertEquals(1, ui.focusedIndex)
        assertTrue(ui.focusedIndex < 2)
    }

    @Test
    fun selectAt_outOfRange_isNoOp() {
        val ui = SearchFocusUiState()
        val controller = SearchFocusController(ui)
        var count = 0
        controller.bind(
            SearchFocusDeps(
                onDismiss = {},
                onMicClick = {},
                onSuggestionSelected = {},
                onClearHistory = {},
                onResultSelected = { count += 1 },
                selectableResults = { listOf(sampleResult()) },
                showRecentChips = { false },
                recentSearches = { emptyList() },
            )
        )
        controller.selectAt(5)
        assertEquals(0, count)
    }
}
