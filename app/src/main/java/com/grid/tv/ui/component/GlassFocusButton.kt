package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.theme.VodNetflixColors

private val GlassShape = RoundedCornerShape(10.dp)

@Composable
fun GlassFocusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val accent = VodNetflixColors.Accent
    val glassBase = if (primary) Color(0xFF1A2438) else Color(0xFF121820)
    val backgroundBrush = when {
        !enabled -> Brush.linearGradient(listOf(Color(0x66101820), Color(0x44081018)))
        focused && primary -> Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.38f),
                glassBase.copy(alpha = 0.72f)
            )
        )
        focused -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.18f),
                glassBase.copy(alpha = 0.62f)
            )
        )
        primary -> Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.22f),
                glassBase.copy(alpha = 0.55f)
            )
        )
        else -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                glassBase.copy(alpha = 0.48f)
            )
        )
    }
    val borderColor = when {
        !enabled -> Color.White.copy(alpha = 0.10f)
        focused -> accent.copy(alpha = if (primary) 0.95f else 0.75f)
        else -> Color.White.copy(alpha = if (primary) 0.28f else 0.20f)
    }
    val borderWidth = if (focused) 2.dp else 1.dp
    val glowModifier = if (focused) {
        Modifier.border(1.dp, accent.copy(alpha = 0.35f), GlassShape)
    } else {
        Modifier
    }

    GridFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(glowModifier)
            .clip(GlassShape)
            .background(backgroundBrush, GlassShape)
            .border(borderWidth, borderColor, GlassShape)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        shape = ClickableSurfaceDefaults.shape(GlassShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val labelColor = when {
                !enabled -> EpgColors.TextDimmed
                focused -> Color.White
                else -> Color(0xFFE8ECF2)
            }
            CompositionLocalProvider(LocalContentColor provides labelColor) {
                ProvideTextStyle(
                    value = TextStyle(
                        color = labelColor,
                        fontFamily = DmSansFamily,
                        fontSize = 15.sp,
                        fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium
                    )
                ) {
                    content()
                }
            }
        }
    }
}
