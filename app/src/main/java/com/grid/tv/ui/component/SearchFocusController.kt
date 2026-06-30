package com.grid.tv.ui.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.ui.focus.TvFocusController
import com.grid.tv.util.TvTextInputSession

internal data class SearchFocusDeps(
    val onDismiss: () -> Unit,
    val onMicClick: () -> Unit,
    val onSuggestionSelected: (String) -> Unit,
    val onClearHistory: () -> Unit,
    val onResultSelected: (SearchResultItem) -> Unit,
    val selectableResults: () -> List<SearchResultItem>,
    val showRecentChips: () -> Boolean,
    val recentSearches: () -> List<String>,
)

internal class SearchFocusController(
    private val ui: SearchFocusUiState,
) : TvFocusController<SearchFocusZone> {
    private var deps: SearchFocusDeps? = null

    fun bind(deps: SearchFocusDeps) {
        this.deps = deps
    }

    private val d: SearchFocusDeps
        get() = deps ?: error("SearchFocusController.bind() must run before interaction")

    override val focusZone: SearchFocusZone
        get() = ui.focusZone

    override fun transitionToZone(zone: SearchFocusZone, detail: String) {
        ui.focusZone = zone
        if (zone == SearchFocusZone.RESULTS && ui.focusedIndex < 0) {
            ui.focusedIndex = 0
        }
    }

    fun moveFocusToSearchResults() {
        val selectable = d.selectableResults()
        transitionToZone(
            when {
                selectable.isNotEmpty() -> SearchFocusZone.RESULTS
                d.showRecentChips() -> SearchFocusZone.RECENT
                else -> SearchFocusZone.MIC
            },
            "imeSubmitted",
        )
    }

    fun selectAt(index: Int) {
        d.selectableResults().getOrNull(index)?.let(d.onResultSelected)
    }

    override fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        val selectable = d.selectableResults()
        val showRecentChips = d.showRecentChips()
        val recentSearches = d.recentSearches()
        return when (event.key) {
            Key.Back, Key.Escape -> {
                d.onDismiss()
                true
            }
            Key.DirectionDown -> {
                when (ui.focusZone) {
                    SearchFocusZone.FIELD -> transitionToZone(
                        if (showRecentChips) SearchFocusZone.RECENT else SearchFocusZone.MIC,
                        "down",
                    )
                    SearchFocusZone.RECENT -> transitionToZone(SearchFocusZone.MIC, "down")
                    SearchFocusZone.MIC -> if (selectable.isNotEmpty()) {
                        transitionToZone(SearchFocusZone.RESULTS, "down")
                    }
                    SearchFocusZone.RESULTS -> if (ui.focusedIndex < selectable.lastIndex) {
                        ui.focusedIndex += 1
                    }
                }
                true
            }
            Key.DirectionUp -> {
                when (ui.focusZone) {
                    SearchFocusZone.RESULTS -> {
                        if (ui.focusedIndex <= 0) {
                            transitionToZone(
                                if (showRecentChips) SearchFocusZone.RECENT else SearchFocusZone.MIC,
                                "up",
                            )
                        } else {
                            ui.focusedIndex -= 1
                        }
                    }
                    SearchFocusZone.MIC -> transitionToZone(
                        if (showRecentChips) SearchFocusZone.RECENT else SearchFocusZone.FIELD,
                        "up",
                    )
                    SearchFocusZone.RECENT -> transitionToZone(SearchFocusZone.FIELD, "up")
                    SearchFocusZone.FIELD -> Unit
                }
                true
            }
            Key.DirectionRight -> when (ui.focusZone) {
                SearchFocusZone.FIELD -> {
                    transitionToZone(SearchFocusZone.MIC, "right")
                    true
                }
                SearchFocusZone.RECENT -> if (ui.recentChipIndex < recentSearches.size) {
                    ui.recentChipIndex += 1
                    true
                } else {
                    false
                }
                else -> false
            }
            Key.DirectionLeft -> when (ui.focusZone) {
                SearchFocusZone.MIC -> {
                    transitionToZone(SearchFocusZone.FIELD, "left")
                    true
                }
                SearchFocusZone.RECENT -> if (ui.recentChipIndex > 0) {
                    ui.recentChipIndex -= 1
                    true
                } else {
                    false
                }
                else -> false
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> when (ui.focusZone) {
                SearchFocusZone.MIC -> {
                    d.onMicClick()
                    true
                }
                SearchFocusZone.RECENT -> when {
                    ui.recentChipIndex == recentSearches.size -> {
                        d.onClearHistory()
                        true
                    }
                    else -> {
                        recentSearches.getOrNull(ui.recentChipIndex)?.let(d.onSuggestionSelected)
                        true
                    }
                }
                SearchFocusZone.RESULTS -> if (selectable.isNotEmpty()) {
                    selectAt(ui.focusedIndex.coerceAtLeast(0))
                    true
                } else {
                    false
                }
                SearchFocusZone.FIELD -> false
            }
            else -> false
        }
    }
}
