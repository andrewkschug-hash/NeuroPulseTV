package com.grid.tv.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class SearchFocusZone { FIELD, MIC, RECENT, RESULTS }

internal class SearchFocusUiState {
    var focusZone by mutableStateOf(SearchFocusZone.FIELD)
    var focusedIndex by mutableIntStateOf(0)
    var recentChipIndex by mutableIntStateOf(0)
}
