package com.grid.tv.ui.theme

import androidx.compose.ui.graphics.Color
import com.grid.tv.domain.model.AppThemeId

object EpgColors {
    private var palette: AppThemePalette = AppThemes.palette(AppThemeId.NEURO_BLUE)

    fun applyPalette(themeId: AppThemeId) {
        palette = AppThemes.palette(themeId)
    }

    val Background: Color get() = palette.background
    val ChannelColumnBg: Color get() = palette.channelColumnBg
    val GridBg: Color get() = palette.gridBg
    val CellAiringNow: Color get() = palette.cellAiringNow
    val CellFuture: Color get() = palette.cellFuture
    val CellPast: Color get() = palette.cellPast
    val CellPastText: Color get() = palette.cellPastText
    val ChannelRowFocusBg: Color get() = palette.channelRowFocusBg
    val RowSeparator: Color get() = palette.rowSeparator
    val Accent: Color get() = palette.accent
    val LiveBadge: Color get() = palette.liveBadge
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary
    val TextDimmed: Color get() = palette.textDimmed
    val TextFuture: Color get() = palette.textFuture
    val NowLine: Color get() = palette.nowLine
    val BorderSubtle: Color get() = palette.borderSubtle
    val DetailPanelBg: Color get() = palette.detailPanelBg
    val HdBadgeBg: Color get() = palette.hdBadgeBg
    val NewBadgeBg: Color get() = palette.newBadgeBg
    val FavoriteStar: Color get() = palette.favoriteStar
    val FocusBorder: Color get() = palette.focusBorder
    val SelectedFill: Color get() = palette.selectedFill

    val TextDimmedStatic: Color get() = palette.textDimmed
}
