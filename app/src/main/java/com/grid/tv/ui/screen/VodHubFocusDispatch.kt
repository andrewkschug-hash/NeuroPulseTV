package com.grid.tv.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.ui.component.VodHubLanguageFilterFocusIndex
import com.grid.tv.ui.focus.GuideNavDrawerFocusTargets
import com.grid.tv.ui.focus.TvFocusDispatcher
import com.grid.tv.ui.focus.dispatchFocus

@Composable
internal fun rememberVodLibraryRowFocusRequesters(): List<FocusRequester> =
    remember { List(VodHubLanguageFilterFocusIndex + 1) { FocusRequester() } }

internal data class VodHubFocusDispatchContext(
    val movieDetailOpen: Boolean,
    val seriesDetailOpen: Boolean,
    val showInlineSearch: Boolean,
    val vodSearchFocused: Boolean,
    val hasBrowseResults: Boolean,
    val showBrowseGrid: Boolean,
    val browseGridCount: Int,
    val displayWallRowCount: Int,
    val blocksGridFocus: Boolean,
    val imeTypingActive: Boolean,
)

@Composable
internal fun VodHubFocusDispatcher(
    focusUi: VodHubFocusUiState,
    navDrawerTargets: GuideNavDrawerFocusTargets,
    libraryRowFocusRequesters: List<FocusRequester>,
    rootFocusRequester: FocusRequester,
    browseGridFocusRequester: FocusRequester,
    browseEmptyStateFocusRequester: FocusRequester,
    genrePanelFocusRequester: FocusRequester,
    languageSubmenuFocusRequester: FocusRequester,
    inlineSearchFocusRequester: FocusRequester,
    movieWatchFocusRequester: FocusRequester,
    context: VodHubFocusDispatchContext,
    wallRowsRevision: String,
) {
    TvFocusDispatcher(
        enabled = !context.imeTypingActive,
        focusUi.focusZone,
        focusUi.navDrawerFocusIndex,
        focusUi.filterFocusIndex,
        focusUi.genreFocusIndex,
        context.movieDetailOpen,
        context.seriesDetailOpen,
        context.showInlineSearch,
        context.vodSearchFocused,
        context.hasBrowseResults,
        context.showBrowseGrid,
        context.browseGridCount,
        context.displayWallRowCount,
        context.blocksGridFocus,
        wallRowsRevision,
    ) {
        when {
            context.movieDetailOpen -> movieWatchFocusRequester.dispatchFocus()
            context.seriesDetailOpen -> Unit
            context.showInlineSearch && context.vodSearchFocused ->
                inlineSearchFocusRequester.dispatchFocus()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                context.showInlineSearch &&
                context.hasBrowseResults &&
                !context.vodSearchFocused &&
                !context.blocksGridFocus ->
                browseGridFocusRequester.dispatchFocus()
            focusUi.focusZone == VodFocusZone.NAV_DRAWER ->
                navDrawerTargets.forIndex(focusUi.navDrawerFocusIndex).dispatchFocus()
            focusUi.focusZone == VodFocusZone.FILTER_PANEL ->
                libraryRowFocusRequesters
                    .getOrNull(focusUi.filterFocusIndex.coerceIn(0, libraryRowFocusRequesters.lastIndex))
                    ?.dispatchFocus()
            focusUi.focusZone == VodFocusZone.GENRE_PANEL ->
                genrePanelFocusRequester.dispatchFocus()
            focusUi.focusZone == VodFocusZone.LANGUAGE_SUBMENU ->
                languageSubmenuFocusRequester.dispatchFocus()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                !context.showInlineSearch &&
                !context.showBrowseGrid &&
                context.displayWallRowCount > 0 &&
                !context.blocksGridFocus ->
                rootFocusRequester.dispatchFocus()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                !context.showInlineSearch &&
                (context.showBrowseGrid || context.displayWallRowCount == 0) &&
                !context.blocksGridFocus &&
                (!context.showBrowseGrid || context.browseGridCount > 0) ->
                rootFocusRequester.dispatchFocus()
            focusUi.focusZone == VodFocusZone.CONTENT &&
                context.showBrowseGrid &&
                context.browseGridCount <= 0 &&
                !context.blocksGridFocus ->
                browseEmptyStateFocusRequester.dispatchFocus()
            focusUi.focusZone == VodFocusZone.HERO ->
                rootFocusRequester.dispatchFocus()
        }
    }
}
