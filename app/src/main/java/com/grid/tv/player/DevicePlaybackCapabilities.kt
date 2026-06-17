package com.grid.tv.player

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import com.grid.tv.util.isTelevision

/**
 * Runtime device capability profile used to tune ExoPlayer without separate app variants.
 */
data class DevicePlaybackCapabilities(
    val isTelevision: Boolean,
    val isLowEndDevice: Boolean,
    val isPhone: Boolean
) {
    val startupPriority: PlaybackStartupPriority = when {
        isTelevision -> PlaybackStartupPriority.STABLE
        isPhone -> PlaybackStartupPriority.FAST
        else -> PlaybackStartupPriority.BALANCED
    }
}

enum class PlaybackStartupPriority {
    /** Phones: minimize time-to-first-frame. */
    FAST,
    BALANCED,
    /** Android TV / Fire TV: larger buffers for stability. */
    STABLE
}

fun Context.devicePlaybackCapabilities(): DevicePlaybackCapabilities {
    val am = getSystemService(ActivityManager::class.java)
    val lowRam = am?.isLowRamDevice == true
    val lowMemory = (am?.memoryClass ?: 256) < 128
    val tv = isTelevision()
    val sw = resources.configuration.smallestScreenWidthDp
    val phone = !tv && sw in 0..599
    return DevicePlaybackCapabilities(
        isTelevision = tv,
        isLowEndDevice = lowRam || lowMemory,
        isPhone = phone
    )
}
