package com.grid.tv.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween

/**
 * Single motion scale for TV UI — avoids overlapping/conflicting animation timings.
 */
object UiMotion {
    const val DurationFastMs = 150
    const val DurationStandardMs = 250
    const val DurationSlowMs = 400
    const val ShimmerDurationMs = 900
    const val MicPulseDurationMs = 700
    const val ScrollDurationMs = 280

    fun <T> fastTween(): AnimationSpec<T> = tween(DurationFastMs)
    fun <T> standardTween(): AnimationSpec<T> = tween(DurationStandardMs)
    fun <T> shimmerTween(): AnimationSpec<T> = tween(ShimmerDurationMs)
}
