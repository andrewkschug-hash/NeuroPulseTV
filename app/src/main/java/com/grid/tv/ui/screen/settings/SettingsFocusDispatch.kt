package com.grid.tv.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.ui.focus.TvFocusDispatcher
import com.grid.tv.ui.focus.dispatchFocus

@Composable
internal fun SettingsFocusDispatcher(
    ui: SettingsFocusUiState,
    categoryCount: Int,
    optionRowCount: Int,
    categoryFocusRequesters: List<FocusRequester>,
    optionFocusRequesters: List<FocusRequester>,
    topBarFocusRequester: FocusRequester,
    enabled: Boolean,
) {
    TvFocusDispatcher(
        enabled = enabled,
        ui.focusZone,
        ui.categoryIndex,
        ui.optionIndex,
        categoryCount,
        optionRowCount,
    ) {
        when (ui.focusZone) {
            SettingsFocusZone.TOP_BAR -> topBarFocusRequester.dispatchFocus()
            SettingsFocusZone.CATEGORIES ->
                categoryFocusRequesters.getOrNull(ui.categoryIndex)?.dispatchFocus()
            SettingsFocusZone.OPTIONS ->
                optionFocusRequesters.getOrNull(ui.optionIndex)?.dispatchFocus()
        }
    }
}
