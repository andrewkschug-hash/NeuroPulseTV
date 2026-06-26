package com.grid.tv.ui.theme

import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.startup.StartupTiming
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ThemeManager @Inject constructor(
    private val repository: IptvRepository
) {
    init {
        StartupTiming.log(
            "ThemeManager constructor complete — IptvRepository was constructed before this init block"
        )
    }

    private val _themeId = MutableStateFlow(AppThemeId.NEURO_BLUE)
    val themeId: StateFlow<AppThemeId> = _themeId.asStateFlow()

    val palette: AppThemePalette
        get() = AppThemes.palette(_themeId.value)

    suspend fun loadFromSettings() {
        StartupTiming.log("ThemeManager.loadFromSettings start")
        val startNs = System.nanoTime()
        val themeId = repository.loadSettings().themeId
        val durationMs = (System.nanoTime() - startNs) / 1_000_000L
        StartupTiming.recordSpan("ThemeManager.loadFromSettings.repository.loadSettings", durationMs)
        StartupTiming.log("ThemeManager.loadFromSettings repository.loadSettings took ${durationMs}ms")
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
