package com.neuropulse.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.neuropulse.tv.R

private val GridColors = darkColorScheme(
    primary = Color(0xFF3B8FFF),
    onPrimary = Color.White,
    secondary = Color(0xFF3B8FFF),
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF111118),
    onSurface = Color(0xFFF2F2F5)
)

private val provider = Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val DmSansFamily = FontFamily(
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = provider, weight = FontWeight.Bold)
)

val BarlowCondensedFamily = FontFamily(
    Font(googleFont = GoogleFont("Barlow Condensed"), fontProvider = provider, weight = FontWeight.ExtraBold)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GridTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GridColors,
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
fun StreamFlowTheme(content: @Composable () -> Unit) = GridTheme(content)
