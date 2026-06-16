package com.grid.tv.ui.theme

import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.repository.IptvRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ThemeManager @Inject constructor(
    private val repository: IptvRepository
) {
    private val _themeId = MutableStateFlow(AppThemeId.NEURO_BLUE)
    val themeId: StateFlow<AppThemeId> = _themeId.asStateFlow()

    val palette: AppThemePalette
        get() = AppThemes.palette(_themeId.value)

    suspend fun loadFromSettings() {
        val themeId = repository.loadSettings().themeId
        _themeId.value = themeId
        EpgColors.applyPalette(themeId)
    }

    suspend fun setTheme(themeId: AppThemeId) {
        if (_themeId.value == themeId) return
        _themeId.value = themeId
        EpgColors.applyPalette(themeId)
        val settings = repository.loadSettings()
        repository.saveSettings(settings.copy(themeId = themeId))
    }
}
