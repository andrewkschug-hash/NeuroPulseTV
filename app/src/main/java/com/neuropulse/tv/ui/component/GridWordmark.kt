package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropulse.tv.ui.theme.BarlowCondensedFamily
import com.neuropulse.tv.ui.theme.EpgColors

private val BrandAccentLight = Color(0xFF7EB8FF)
private val BrandAccentPale = Color(0xFFB8D4FF)

@Composable
fun GridWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    color: Color = Color(0xFFF2F2F5),
    letterSpacing: TextUnit = fontSize * 0.25f
) {
    Text(
        text = "GRID",
        modifier = modifier,
        color = color,
        fontFamily = BarlowCondensedFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = letterSpacing
    )
}

@Composable
fun GridBrandWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 44.sp,
    letterSpacing: TextUnit = 14.sp,
    alpha: Float = 1f,
    scale: Float = 1f,
    glowAlpha: Float = 0.55f,
    showDivider: Boolean = true,
    dividerWidth: Dp = 140.dp,
    dividerAlpha: Float = 1f,
    dividerProgress: Float = 1f
) {
    val accent = EpgColors.FocusBorder
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
        }
    ) {
        Text(
            text = "GRID",
            fontFamily = BarlowCondensedFamily,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = letterSpacing,
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent,
                        BrandAccentLight,
                        BrandAccentPale,
                        BrandAccentLight,
                        accent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(fontSize.value * 4f, fontSize.value * 0.5f)
                ),
                shadow = Shadow(
                    color = accent.copy(alpha = glowAlpha),
                    offset = Offset(0f, 0f),
                    blurRadius = fontSize.value * 0.7f
                )
            )
        )
        if (showDivider) {
            Spacer(modifier = Modifier.height((fontSize.value * 0.38f).dp))
            Box(
                modifier = Modifier
                    .width(dividerWidth * dividerProgress.coerceIn(0f, 1f))
                    .height(1.5.dp)
                    .graphicsLayer { this.alpha = dividerAlpha }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accent.copy(alpha = 0.15f),
                                accent.copy(alpha = 0.55f),
                                accent.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
