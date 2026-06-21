package com.grid.tv.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.onFocusChanged
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

private val DefaultContainerColor = Color(0xFF2E2E3E)
private val DisabledContainerColor = Color(0xFF1E1E28)
private val ButtonShape = RoundedCornerShape(8.dp)

@Composable
fun GlowFocusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DefaultContainerColor,
    contentDescription: String? = null,
    externallyFocused: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused || externallyFocused
    val resolvedContainer = when {
        !enabled -> DisabledContainerColor
        else -> containerColor
    }
    GridFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .tvFocusBorder(
                focused = showFocused,
                shape = ButtonShape,
                unfocusedColor = Color.Transparent
            ),
        shape = ClickableSurfaceDefaults.shape(ButtonShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = resolvedContainer,
            focusedContainerColor = resolvedContainer,
            pressedContainerColor = resolvedContainer.copy(alpha = 0.85f),
            disabledContainerColor = DisabledContainerColor
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val labelColor = if (enabled) Color.White else EpgColors.TextDimmed
            CompositionLocalProvider(LocalContentColor provides labelColor) {
                ProvideTextStyle(
                    value = TextStyle(
                        color = labelColor,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun GlowFocusButtonText(
    text: String,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Medium
) {
    androidx.tv.material3.Text(
        text = text,
        color = color,
        fontFamily = DmSansFamily,
        fontSize = fontSize,
        fontWeight = fontWeight
    )
}

@Composable
fun GlowFocusMenuRow(
    onClick: () -> Unit,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = DefaultContainerColor,
    content: @Composable () -> Unit
) {
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .tvFocusBorder(focused = isFocused, shape = ButtonShape),
        shape = ClickableSurfaceDefaults.shape(ButtonShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
            pressedContainerColor = containerColor.copy(alpha = 0.85f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}
