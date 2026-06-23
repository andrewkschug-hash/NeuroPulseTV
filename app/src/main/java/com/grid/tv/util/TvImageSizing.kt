package com.grid.tv.util

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.grid.tv.player.LowEndDeviceMode

/** TV-tuned Coil decode sizes — smaller on low-end devices to reduce bitmap heap pressure. */
object TvImageSizing {

    fun crossfadeMs(context: Context): Int =
        if (LowEndDeviceMode.isActive(context)) 0 else 200

    /** Prefer smaller CDN variants on low-end devices before decode downscale. */
    fun posterThumbnailUrl(url: String?, context: Context): String? {
        if (url.isNullOrBlank()) return url
        if (!LowEndDeviceMode.isActive(context)) return url
        if (!url.contains("image.tmdb.org/t/p/")) return url
        return url
            .replace("/original/", "/w342/")
            .replace("/w780/", "/w342/")
            .replace("/w500/", "/w185/")
    }

    fun channelLogoPx(context: Context, displayDp: Int = 36): Int {
        val density = context.resources.displayMetrics.density
        val scale = if (LowEndDeviceMode.isActive(context)) 0.85f else 1f
        return (displayDp * density * scale).toInt().coerceIn(32, 128)
    }

    fun channelBrowserLogoPx(context: Context): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val scale = if (LowEndDeviceMode.isActive(context)) 0.85f else 1f
        return Pair(
            (180 * density * scale).toInt().coerceAtLeast(120),
            (110 * density * scale).toInt().coerceAtLeast(72)
        )
    }

    fun searchResultLogoPx(context: Context): Int = channelLogoPx(context, displayDp = 36)

    fun seriesCoverSize(context: Context): Pair<Int, Int> = vodPosterSize(context)

    fun epgPreviewPx(context: Context): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val scale = if (LowEndDeviceMode.isActive(context)) 0.8f else 1f
        return Pair(
            (72 * density * scale).toInt().coerceAtLeast(48),
            (44 * density * scale).toInt().coerceAtLeast(32)
        )
    }

    fun vodPosterSize(context: Context): Pair<Int, Int> =
        if (LowEndDeviceMode.isActive(context)) 128 to 192 else 224 to 336

    fun vodLandscapeSize(context: Context): Pair<Int, Int> =
        if (LowEndDeviceMode.isActive(context)) 384 to 216 else 600 to 336

    fun vodBackdropSize(context: Context): Pair<Int, Int> =
        if (LowEndDeviceMode.isActive(context)) 960 to 405 else 1920 to 800

    fun continueWatchingPosterPx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        val dp = if (LowEndDeviceMode.isActive(context)) 88 else 120
        return (dp * density).toInt().coerceIn(72, 240)
    }

    fun recordingThumbnailSize(context: Context): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val scale = if (LowEndDeviceMode.isActive(context)) 0.85f else 1f
        return Pair(
            (160 * density * scale).toInt().coerceAtLeast(120),
            (90 * density * scale).toInt().coerceAtLeast(68)
        )
    }

    fun vodPagingPrefetchDistance(): Int =
        if (LowEndDeviceMode.current().active) 6 else 20

    fun sizedRequest(
        context: Context,
        data: Any?,
        widthPx: Int,
        heightPx: Int,
        crossfadeMs: Int? = null,
        builder: ImageRequest.Builder.() -> Unit = {}
    ): ImageRequest {
        val lowEnd = LowEndDeviceMode.isActive(context)
        val thumbnailData = (data as? String)?.let { posterThumbnailUrl(it, context) } ?: data
        return ImageRequest.Builder(context)
            .data(thumbnailData)
            .size(widthPx, heightPx)
            .crossfade(crossfadeMs ?: crossfadeMs(context))
            .allowRgb565(lowEnd)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .apply(builder)
            .build()
    }
}
