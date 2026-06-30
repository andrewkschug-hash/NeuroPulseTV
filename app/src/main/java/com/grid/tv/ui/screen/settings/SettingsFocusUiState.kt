package com.grid.tv.ui.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsFocusUiState {
    var focusZone by mutableStateOf(SettingsFocusZone.CATEGORIES)
    var categoryIndex by mutableIntStateOf(0)
    var optionIndex by mutableIntStateOf(0)
    var topBarFocusIndex by mutableIntStateOf(3)
}
