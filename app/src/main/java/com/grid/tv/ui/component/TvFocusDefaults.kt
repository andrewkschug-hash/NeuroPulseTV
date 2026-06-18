package com.grid.tv.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.grid.tv.ui.platform.touchTarget
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvRemoteKeyboard

object TvFocusDefaults {
    val NoScale: ClickableSurfaceScale = ClickableSurfaceScale.None

    @Composable
    fun noBorder() = ClickableSurfaceDefaults.border(
        border = Border.None,
        focusedBorder = Border.None,
        pressedBorder = Border.None,
        disabledBorder = Border.None,
        focusedDisabledBorder = Border.None
    )

    fun noGlow() = ClickableSurfaceDefaults.glow(
        glow = Glow.None,
        focusedGlow = Glow.None,
        pressedGlow = Glow.None
    )
}

fun Modifier.tvFocusBorder(
    focused: Boolean,
    shape: Shape,
    width: Dp = 2.dp,
    unfocusedColor: Color = EpgColors.BorderSubtle,
    focusedColor: Color = EpgColors.FocusBorder
): Modifier = border(
    width = width,
    color = if (focused) focusedColor else unfocusedColor,
    shape = shape
)

@Composable
fun GridFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    content: @Composable BoxScope.() -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    Surface(
        onClick = {
            keyboard?.hide()
            TvRemoteKeyboard.dismissKeyboard(view)
            onClick()
        },
        modifier = modifier.touchTarget(),
        enabled = enabled,
        shape = shape,
        colors = colors,
        scale = TvFocusDefaults.NoScale,
        border = TvFocusDefaults.noBorder(),
        glow = TvFocusDefaults.noGlow(),
        content = content
    )
}
