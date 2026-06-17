package com.grid.tv.ui.platform

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.grid.tv.util.isTelevision

/**
 * Describes how the app should behave for the current device.
 * TV remotes keep D-pad-first navigation; phones/tablets enable touch gestures.
 */
data class DeviceFormFactor(
    val isTelevision: Boolean,
    val hasTouchscreen: Boolean
) {
    /** Leanback / TV UI without a touch panel — D-pad is primary. */
    val prefersDpadNavigation: Boolean
        get() = isTelevision && !hasTouchscreen

    /** Touch scrolling, tapping, and larger hit targets. */
    val enableTouchGestures: Boolean
        get() = hasTouchscreen

    /** Compact phone layouts (future); tablets may still use touch. */
    val useCompactLayout: Boolean
        get() = !isTelevision && hasTouchscreen
}

val LocalDeviceFormFactor = staticCompositionLocalOf {
    DeviceFormFactor(isTelevision = true, hasTouchscreen = false)
}

fun Context.resolveDeviceFormFactor(): DeviceFormFactor {
    val tv = isTelevision()
    val touch = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    return DeviceFormFactor(isTelevision = tv, hasTouchscreen = touch)
}

@Composable
fun rememberDeviceFormFactor(): DeviceFormFactor {
    val context = LocalContext.current
    return remember(context) { context.resolveDeviceFormFactor() }
}
