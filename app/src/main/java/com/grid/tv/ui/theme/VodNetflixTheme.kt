package com.grid.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VOD surface colors — delegates to the active [EpgColors] / [AppThemePalette]
 * so Live guide and VOD hub share one visual system.
 */
object VodNetflixColors {
    val Background: Color get() = EpgColors.Background
    val TextPrimary: Color get() = EpgColors.TextPrimary
    val TextSecondary: Color get() = EpgColors.TextSecondary
    val Accent: Color get() = EpgColors.Accent
    val FocusBorder: Color get() = EpgColors.FocusBorder
    val CardPlaceholder: Color get() = EpgColors.DetailPanelBg
    val HeroGradientEnd: Color get() = EpgColors.Background
    val SurfaceElevated: Color get() = EpgColors.DetailPanelBg
    val BorderSubtle: Color get() = EpgColors.BorderSubtle
}
