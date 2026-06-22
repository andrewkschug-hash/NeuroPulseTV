package com.grid.tv.player

import android.os.Build

/**
 * Hardware decoder budgets for constrained Android TV devices (Chromecast with Google TV 4K).
 *
 * Amlogic-based Chromecast units typically allow ~3–4 concurrent hardware video sessions before
 * silent degradation (dropped frames, software fallback, surface stall).
 */
data class DeviceDecoderProfile(
    val deviceLabel: String,
    val warnConcurrentDecoders: Int,
    val criticalConcurrentDecoders: Int,
    val warnConcurrentSurfaces: Int,
    val criticalConcurrentSurfaces: Int,
    val isChromecastGoogleTv: Boolean
)

object DeviceDecoderLimits {

    fun profile(): DeviceDecoderProfile {
        val model = Build.MODEL.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()

        val chromecastGtv = model.contains("chromecast") ||
            device.contains("sabrina") ||
            device.contains("boreal") ||
            product.contains("sabrina") ||
            product.contains("boreal") ||
            model.contains("google tv streamer") ||
            product.contains("google_tv_streamer")

        val chromecast4k = chromecastGtv && (
            model.contains("4k") ||
                device.contains("boreal") ||
                product.contains("boreal") ||
                model.contains("streamer 4k")
            )

        return when {
            chromecast4k -> DeviceDecoderProfile(
                deviceLabel = "Chromecast-GTV-4K",
                warnConcurrentDecoders = 2,
                criticalConcurrentDecoders = 3,
                warnConcurrentSurfaces = 2,
                criticalConcurrentSurfaces = 3,
                isChromecastGoogleTv = true
            )
            chromecastGtv -> DeviceDecoderProfile(
                deviceLabel = "Chromecast-GTV",
                warnConcurrentDecoders = 2,
                criticalConcurrentDecoders = 4,
                warnConcurrentSurfaces = 2,
                criticalConcurrentSurfaces = 4,
                isChromecastGoogleTv = true
            )
            manufacturer.contains("google") && model.contains("tv") -> DeviceDecoderProfile(
                deviceLabel = "Google-TV",
                warnConcurrentDecoders = 3,
                criticalConcurrentDecoders = 4,
                warnConcurrentSurfaces = 3,
                criticalConcurrentSurfaces = 4,
                isChromecastGoogleTv = false
            )
            else -> DeviceDecoderProfile(
                deviceLabel = "default",
                warnConcurrentDecoders = 3,
                criticalConcurrentDecoders = 5,
                warnConcurrentSurfaces = 3,
                criticalConcurrentSurfaces = 5,
                isChromecastGoogleTv = false
            )
        }
    }
}
