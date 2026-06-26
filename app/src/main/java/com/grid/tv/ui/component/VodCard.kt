package com.grid.tv.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared TV focus pop for VOD poster cards ([VodPosterCard], [NetflixPosterCard]). */
object VodCardDefaults {
    const val FOCUS_SCALE = 1.1f
    const val FOCUS_ANIMATION_MS = 150
    val FOCUSED_SHADOW_ELEVATION: Dp = 10.dp
}

@Composable
fun InteractionSource.collectVodCardFocused(externallyFocused: Boolean = false): Boolean {
    val isFocused by collectIsFocusedAsState()
    return isFocused || externallyFocused
}

/**
 * Focus pop via [graphicsLayer] — scale 1.0→1.1 and shadow lift over 150ms.
 * Pair with [TvFocusDefaults.NoScale] on the clickable surface so platform scaling stays off.
 */
@Composable
fun Modifier.vodCardFocusPop(
    interactionSource: InteractionSource,
    externallyFocused: Boolean = false,
    focusedScale: Float = VodCardDefaults.FOCUS_SCALE,
): Modifier {
    val focused = interactionSource.collectVodCardFocused(externallyFocused)
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(VodCardDefaults.FOCUS_ANIMATION_MS),
        label = "vodCardScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (focused) VodCardDefaults.FOCUSED_SHADOW_ELEVATION else 0.dp,
        animationSpec = tween(VodCardDefaults.FOCUS_ANIMATION_MS),
        label = "vodCardShadow"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.shadowElevation = shadowElevation.toPx()
        clip = false
    }
}
