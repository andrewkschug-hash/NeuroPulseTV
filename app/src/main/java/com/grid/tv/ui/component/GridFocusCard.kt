package com.grid.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.theme.EpgColors

private val DefaultPosterShape = RoundedCornerShape(6.dp)
private const val FOCUS_SCALE = 1.07f

/**
 * Unified TV focus surface for poster cards and content tiles.
 * Uses theme accent borders and scale lift — shared across VOD and guide surfaces.
 */
@Composable
fun GridFocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    externallyFocused: Boolean = false,
    shape: Shape = DefaultPosterShape,
    focusScale: Float = FOCUS_SCALE,
    containerColor: Color = EpgColors.DetailPanelBg,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused || externallyFocused
    val scale by animateFloatAsState(
        targetValue = if (showFocused) focusScale else 1f,
        animationSpec = tween(150),
        label = "gridFocusScale"
    )

    GridFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .zIndex(if (showFocused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(
                focused = showFocused,
                shape = shape,
                unfocusedColor = Color.Transparent
            ),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor
        ),
        content = content
    )
}
