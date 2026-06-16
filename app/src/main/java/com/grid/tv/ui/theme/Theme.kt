package com.grid.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.grid.tv.domain.model.AppThemeId

// System fonts — no Google Play Services dependency (Fire Stick / sideload safe)
val DmSansFamily = FontFamily.SansSerif
val BarlowCondensedFamily = FontFamily.SansSerif

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GridTheme(
    themeId: AppThemeId = AppThemeId.NEURO_BLUE,
    content: @Composable () -> Unit
) {
    LaunchedEffect(themeId) {
        EpgColors.applyPalette(themeId)
    }
    val palette = AppThemes.palette(themeId)
    val gridColors = darkColorScheme(
        primary = palette.accent,
        onPrimary = Color.White,
        secondary = palette.accent,
        background = palette.background,
        surface = palette.channelColumnBg,
        onSurface = palette.textPrimary
    )
    MaterialTheme(
        colorScheme = gridColors,
        typography = androidx.tv.material3.Typography(
            bodyLarge = TextStyle(fontFamily = DmSansFamily, fontSize = 14.sp),
            titleLarge = TextStyle(fontFamily = DmSansFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontFamily = DmSansFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            labelSmall = TextStyle(fontFamily = DmSansFamily, fontSize = 11.sp),
            labelMedium = TextStyle(fontFamily = DmSansFamily, fontSize = 12.sp)
        ),
        content = content
    )
}

/** @deprecated Use [GridTheme] */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamFlowTheme(content: @Composable () -> Unit) = GridTheme(content = content)

