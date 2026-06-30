package com.grid.tv.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.ui.focus.TvFocusDispatcher
import com.grid.tv.ui.focus.dispatchFocus

@Composable
internal fun SearchFocusDispatcher(
    ui: SearchFocusUiState,
    fieldFocusRequester: FocusRequester,
    micFocusRequester: FocusRequester,
    modalTrapFocusRequester: FocusRequester,
    searchBarState: SearchBarState,
) {
    TvFocusDispatcher(
        enabled = true,
        ui.focusZone,
        ui.focusedIndex,
        ui.recentChipIndex,
        searchBarState,
    ) {
        when (ui.focusZone) {
            SearchFocusZone.FIELD -> fieldFocusRequester.dispatchFocus()
            SearchFocusZone.MIC -> micFocusRequester.dispatchFocus()
            SearchFocusZone.RECENT, SearchFocusZone.RESULTS ->
                modalTrapFocusRequester.dispatchFocus()
        }
    }
}
