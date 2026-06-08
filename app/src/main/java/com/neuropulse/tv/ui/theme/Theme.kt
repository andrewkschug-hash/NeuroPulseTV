package com.neuropulse.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.darkColorScheme
import com.neuropulse.tv.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme

private val OledColors = darkColorScheme(
    primary = Color(0xFF1E90FF),
    onPrimary = Color.White,
    secondary = Color(0xFF1E90FF),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEAEAEA)
)

private val provider = Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val nunitoFamily = FontFamily(
    Font(googleFont = GoogleFont("Nunito"), fontProvider = provider)
)

private val condensedFamily = FontFamily(
    Font(googleFont = GoogleFont("Barlow Condensed"), fontProvider = provider)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OledColors,
        typography = androidx.tv.material3.Typography(
            bodyLarge = TextStyle(fontFamily = nunitoFamily, fontSize = 18.sp),
            titleLarge = TextStyle(fontFamily = condensedFamily, fontSize = 28.sp),
            headlineMedium = TextStyle(fontFamily = condensedFamily, fontSize = 34.sp)
        ),
        content = content
    )
}
