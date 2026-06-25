package com.grid.tv.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grid.tv.domain.model.SearchBarState
import com.grid.tv.domain.model.SearchResultItem
import com.grid.tv.domain.model.UnifiedSearchResults
import com.grid.tv.ui.component.SearchOverlay
import com.grid.tv.ui.theme.EpgColors

/** Full-screen guide search — not a modal overlay. */
@Composable
fun GuideSearchScreen(
    query: String,
    unifiedResults: UnifiedSearchResults,
    flatResults: List<SearchResultItem>,
    searchBarState: SearchBarState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onMicClick: () -> Unit,
    onResultSelected: (SearchResultItem) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EpgColors.Background)
    ) {
        SearchOverlay(
            query = query,
            unifiedResults = unifiedResults,
            flatResults = flatResults,
            searchBarState = searchBarState,
            onQueryChange = onQueryChange,
            onClear = onClear,
            onDismiss = onBack,
            onMicClick = onMicClick,
            onResultSelected = onResultSelected,
            onSuggestionSelected = onSuggestionSelected,
            onClearHistory = onClearHistory
        )
    }
}
