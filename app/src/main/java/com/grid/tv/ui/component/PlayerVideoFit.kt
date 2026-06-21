package com.grid.tv.ui.component

import androidx.media3.ui.AspectRatioFrameLayout
import com.grid.tv.domain.model.AspectRatioSetting

enum class PlaybackVideoFit(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    COVER("Cover")
}

fun PlaybackVideoFit.toResizeMode(): Int = when (this) {
    PlaybackVideoFit.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    PlaybackVideoFit.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    PlaybackVideoFit.COVER -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

fun AspectRatioSetting.toPlaybackVideoFit(): PlaybackVideoFit = when (this) {
    AspectRatioSetting.STRETCH -> PlaybackVideoFit.FILL
    AspectRatioSetting.RATIO_16_9, AspectRatioSetting.RATIO_4_3 -> PlaybackVideoFit.FIT
    AspectRatioSetting.AUTO -> PlaybackVideoFit.COVER
}
