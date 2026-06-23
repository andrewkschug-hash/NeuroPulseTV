package com.grid.tv.util

import coil.EventListener
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult
import coil.size.Size

/**
 * Coil pipeline logging for poster performance audits.
 * Decode and fetch callbacks run on Coil worker threads — never Main.
 */
class PosterImageEventListener : EventListener {

    private var decodeStartMs: Long = 0L
    private var resolvedSize: Size? = null

    override fun onStart(request: ImageRequest) {
        decodeStartMs = 0L
        resolvedSize = null
        PosterImageLogger.requested(request, resolvedSize)
    }

    override fun resolveSizeEnd(request: ImageRequest, size: Size) {
        resolvedSize = size
    }

    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        PosterImageLogger.cacheMiss(request, resolvedSize)
    }

    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decodeStartMs = System.currentTimeMillis()
    }

    override fun decodeEnd(
        request: ImageRequest,
        decoder: Decoder,
        options: Options,
        result: DecodeResult?
    ) {
        if (decodeStartMs > 0L) {
            PosterImageLogger.decoded(request, resolvedSize, System.currentTimeMillis() - decodeStartMs)
            decodeStartMs = 0L
        }
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        when (result.dataSource) {
            DataSource.MEMORY_CACHE,
            DataSource.MEMORY -> PosterImageLogger.cacheHit(request, resolvedSize, "memory")
            DataSource.DISK -> PosterImageLogger.cacheHit(request, resolvedSize, "disk")
            DataSource.NETWORK -> Unit
        }
    }

    override fun onError(request: ImageRequest, result: ErrorResult) = Unit

    override fun onCancel(request: ImageRequest) = Unit

    object Factory : EventListener.Factory {
        override fun create(request: ImageRequest): EventListener = PosterImageEventListener()
    }
}
