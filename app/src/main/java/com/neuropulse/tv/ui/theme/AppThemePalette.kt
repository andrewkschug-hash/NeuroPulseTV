package com.neuropulse.tv.ui.theme

import androidx.compose.ui.graphics.Color
import com.neuropulse.tv.domain.model.AppThemeId

data class AppThemePalette(
    val background: Color,
    val channelColumnBg: Color,
    val gridBg: Color,
    val cellAiringNow: Color,
    val cellFuture: Color,
    val cellPast: Color,
    val cellPastText: Color,
    val channelRowFocusBg: Color,
    val rowSeparator: Color,
    val accent: Color,
    val liveBadge: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDimmed: Color,
    val textFuture: Color,
    val nowLine: Color,
    val borderSubtle: Color,
    val detailPanelBg: Color,
    val hdBadgeBg: Color,
    val newBadgeBg: Color,
    val favoriteStar: Color,
    val focusBorder: Color,
    val selectedFill: Color
)

object AppThemes {
    private val neuroBlue = AppThemePalette(
        background = Color(0xFF0A0A0F),
        channelColumnBg = Color(0xFF111118),
        gridBg = Color(0xFF0D0D14),
        cellAiringNow = Color(0xFF1A1A2E),
        cellFuture = Color(0xFF13131A),
        cellPast = Color(0xFF0F0F14),
        cellPastText = Color(0xFF44445A),
        channelRowFocusBg = Color(0xFF1C1C2E),
        rowSeparator = Color(0x0AFFFFFF),
        accent = Color(0xFF3B8FFF),
        liveBadge = Color(0xFFFF3B3B),
        textPrimary = Color(0xFFF2F2F5),
        textSecondary = Color(0xFF8888A0),
        textDimmed = Color(0xFF555568),
        textFuture = Color(0xFFAAAABC),
        nowLine = Color(0xFFFF3B3B),
        borderSubtle = Color(0x12FFFFFF),
        detailPanelBg = Color(0xFF1A1A28),
        hdBadgeBg = Color(0xFF2A2A3A),
        newBadgeBg = Color(0xFFF0A732),
        favoriteStar = Color(0xFFF5C518),
        focusBorder = Color(0xFF3B8FFF),
        selectedFill = Color(0xFF3B8FFF)
    )

    private val oledBlack = neuroBlue.copy(
        background = Color(0xFF000000),
        channelColumnBg = Color(0xFF050505),
        gridBg = Color(0xFF020202),
        cellAiringNow = Color(0xFF101010),
        cellFuture = Color(0xFF080808),
        cellPast = Color(0xFF040404),
        channelRowFocusBg = Color(0xFF141414),
        detailPanelBg = Color(0xFF101010),
        accent = Color(0xFF5AA9FF),
        focusBorder = Color(0xFF5AA9FF),
        selectedFill = Color(0xFF5AA9FF)
    )

    private val netflixRed = neuroBlue.copy(
        background = Color(0xFF0B0B0B),
        channelColumnBg = Color(0xFF141414),
        gridBg = Color(0xFF101010),
        cellAiringNow = Color(0xFF221111),
        accent = Color(0xFFE50914),
        focusBorder = Color(0xFFE50914),
        selectedFill = Color(0xFFE50914),
        liveBadge = Color(0xFFE50914),
        nowLine = Color(0xFFE50914)
    )

    private val sportsGreen = neuroBlue.copy(
        background = Color(0xFF071008),
        channelColumnBg = Color(0xFF0E1810),
        gridBg = Color(0xFF0A140C),
        cellAiringNow = Color(0xFF132818),
        accent = Color(0xFF2ECC71),
        focusBorder = Color(0xFF2ECC71),
        selectedFill = Color(0xFF2ECC71),
        liveBadge = Color(0xFF27AE60)
    )

    private val purpleNeon = neuroBlue.copy(
        background = Color(0xFF0A0612),
        channelColumnBg = Color(0xFF120A1E),
        gridBg = Color(0xFF0E0818),
        cellAiringNow = Color(0xFF1E1030),
        accent = Color(0xFFB84DFF),
        focusBorder = Color(0xFFB84DFF),
        selectedFill = Color(0xFFB84DFF),
        liveBadge = Color(0xFFFF4FD8)
    )

    fun palette(themeId: AppThemeId): AppThemePalette = when (themeId) {
        AppThemeId.NEURO_BLUE -> neuroBlue
        AppThemeId.OLED_BLACK -> oledBlack
        AppThemeId.NETFLIX_RED -> netflixRed
        AppThemeId.SPORTS_GREEN -> sportsGreen
        AppThemeId.PURPLE_NEON -> purpleNeon
    }
}
