package com.grid.tv.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.Coil
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Lume-inspired image loading: sync cache peek (no placeholder flash),
 * retry with backoff, and hero prefetch.
 */
object TvImagePipeline {
    private const val TAG = "TvImagePipeline"
    private const val MAX_RETRIES = 3

    fun peekCached(context: Context, url: String?, widthPx: Int, heightPx: Int): BitmapDrawable? {
        if (url.isNullOrBlank()) return null
        val key = memoryKey(url, widthPx, heightPx)
        val cached = Coil.imageLoader(context).memoryCache?.get(key) ?: return null
        return BitmapDrawable(context.resources, cached.bitmap)
    }

    fun memoryKey(url: String, widthPx: Int, heightPx: Int): MemoryCache.Key =
        MemoryCache.Key("${url}|${widthPx}x$heightPx")

    fun buildRequest(
        context: Context,
        url: String?,
        widthPx: Int,
        heightPx: Int,
        crossfadeMs: Int? = null
    ): ImageRequest = TvImageSizing.sizedRequest(
        context = context,
        data = url,
        widthPx = widthPx,
        heightPx = heightPx,
        crossfadeMs = crossfadeMs
    )

    suspend fun executeWithRetry(
        context: Context,
        url: String,
        widthPx: Int,
        heightPx: Int
    ): SuccessResult? = withContext(Dispatchers.IO) {
        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            when (val result = Coil.imageLoader(context).execute(buildRequest(context, url, widthPx, heightPx, 0))) {
                is SuccessResult -> return@withContext result
                is ErrorResult -> {
                    if (attempt >= MAX_RETRIES) {
                        Log.w(TAG, "image failed urlHash=${url.hashCode()} attempts=$attempt")
                        return@withContext null
                    }
                    kotlinx.coroutines.delay(backoffMs(attempt))
                    attempt++
                }
            }
        }
        null
    }

    fun prefetch(context: Context, urls: List<String?>, widthPx: Int, heightPx: Int) {
        val loader = Coil.imageLoader(context)
        urls.filterNot { it.isNullOrBlank() }.distinct().forEach { url ->
            if (peekCached(context, url, widthPx, heightPx) != null) return@forEach
            val request = buildRequest(context, url, widthPx, heightPx, 0)
                .newBuilder()
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            loader.enqueue(request)
        }
    }

    fun prefetchHeroNeighbors(
        context: Context,
        urls: List<String?>,
        centerIndex: Int,
        widthPx: Int,
        heightPx: Int
    ) {
        if (urls.isEmpty()) return
        val neighbors = buildList {
            add(urls.getOrNull(centerIndex - 1))
            add(urls.getOrNull(centerIndex + 1))
        }
        prefetch(context, neighbors, widthPx, heightPx)
    }

    private fun backoffMs(attempt: Int): Long =
        (400.0 * 2.0.pow(attempt.toDouble())).toLong()
}
