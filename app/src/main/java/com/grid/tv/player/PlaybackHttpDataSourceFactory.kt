package com.grid.tv.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.data.network.NetworkPlaybackConfig
import com.grid.tv.data.network.toNetworkPlaybackConfig
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.util.PerformanceAudit
import okhttp3.OkHttpClient

/**
 * App-lifetime playback network stack for ExoPlayer.
 *
 * [DataSource.Factory], [OkHttpDataSource.Factory], and [MediaSource.Factory] are created once
 * per OkHttp playback client generation and reused across channel zaps and ExoPlayer instances.
 * Recreated only when proxy/connect-timeout settings change (via [syncNetworkSettings]).
 */
class PlaybackHttpDataSourceFactory(
    private val appHttpClient: AppHttpClient,
    private val playbackMetricsLogger: PlaybackMetricsLogger,
    private val streamFormatRegistry: IptvStreamFormatRegistry
) {
    @Volatile
    private var stack: PlaybackSourceStack? = null

    @Volatile
    private var vodStack: PlaybackSourceStack? = null

    @Volatile
    private var lastConfig: NetworkPlaybackConfig? = null

    private val lock = Any()

    /**
     * Returns the shared [DataSource.Factory]. Prefer [mediaSourceFactory] for ExoPlayer construction.
     */
    @UnstableApi
    fun create(settings: AppSettings? = null): DataSource.Factory =
        dataSourceFactory(settings)

    @UnstableApi
    fun dataSourceFactory(settings: AppSettings? = null): DataSource.Factory =
        ensureStack(settings).dataSourceFactory

    @UnstableApi
    fun mediaSourceFactory(settings: AppSettings? = null): MediaSource.Factory =
        ensureStack(settings).mediaSourceFactory

    /** VOD-only factory stack with zero HTTP retries and no live failover policy. */
    @UnstableApi
    fun mediaSourceFactoryForOnDemand(settings: AppSettings? = null): MediaSource.Factory =
        ensureVodStack(settings).mediaSourceFactory

    /**
     * Aligns the playback OkHttp client and invalidates cached factories when network settings change.
     */
    fun syncNetworkSettings(settings: AppSettings) {
        val config = settings.toNetworkPlaybackConfig()
        synchronized(lock) {
            if (config == lastConfig && stack != null) return
            lastConfig = config
            appHttpClient.applySettings(settings)
            invalidateStackLocked(reason = "network_settings")
            invalidateVodStackLocked()
            Log.i(TAG, "PLAYBACK_NETWORK ${config.toLogLine()}")
        }
    }

    /** For tests and diagnostics. */
    internal fun stackGeneration(): Int = stack?.generation ?: 0

    internal fun isStackInitialized(): Boolean = stack != null

    @UnstableApi
    private fun ensureStack(settings: AppSettings?): PlaybackSourceStack {
        settings?.toNetworkPlaybackConfig()?.let { config ->
            synchronized(lock) {
                if (lastConfig == null) {
                    lastConfig = config
                }
            }
        }
        val client = appHttpClient.playbackClient()
        val clientId = System.identityHashCode(client)
        stack?.let { existing ->
            if (existing.okHttpClientId == clientId) {
                PerformanceAudit.logPlaybackFactoryReuse(
                    stackId = existing.generation,
                    okHttpClientId = clientId
                )
                return existing
            }
        }
        synchronized(lock) {
            stack?.let { existing ->
                if (existing.okHttpClientId == clientId) {
                    PerformanceAudit.logPlaybackFactoryReuse(
                        stackId = existing.generation,
                        okHttpClientId = clientId
                    )
                    return existing
                }
            }
            return buildStackLocked(client, reason = "okhttp_client_changed")
        }
    }

    private fun invalidateStackLocked(reason: String) {
        stack = null
        PerformanceAudit.logPlaybackFactoryInvalidated(reason)
    }

    private fun invalidateVodStackLocked() {
        vodStack = null
    }

    @UnstableApi
    private fun ensureVodStack(settings: AppSettings?): PlaybackSourceStack {
        settings?.toNetworkPlaybackConfig()?.let { config ->
            synchronized(lock) {
                if (lastConfig == null) {
                    lastConfig = config
                }
            }
        }
        val client = appHttpClient.playbackClient()
        val clientId = System.identityHashCode(client)
        vodStack?.let { existing ->
            if (existing.okHttpClientId == clientId) {
                return existing
            }
        }
        synchronized(lock) {
            vodStack?.let { existing ->
                if (existing.okHttpClientId == clientId) {
                    return existing
                }
            }
            return buildVodStackLocked(client)
        }
    }

    @UnstableApi
    private fun buildStackLocked(client: OkHttpClient, reason: String): PlaybackSourceStack {
        val generation = (stack?.generation ?: 0) + 1
        val clientId = System.identityHashCode(client)
        val dataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(STREAM_USER_AGENT)
        val loadErrorPolicy = IptvLoadErrorHandlingPolicy(playbackMetricsLogger)
        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)
        val mediaSourceFactory = IptvMediaSourceFactory(
            dataSourceFactory = dataSourceFactory,
            hlsFactory = hlsFactory,
            streamFormatRegistry = streamFormatRegistry
        )
            .also { it.setLoadErrorHandlingPolicy(loadErrorPolicy) }
        val newStack = PlaybackSourceStack(
            generation = generation,
            okHttpClientId = clientId,
            dataSourceFactory = dataSourceFactory,
            mediaSourceFactory = mediaSourceFactory
        )
        stack = newStack
        PerformanceAudit.logPlaybackFactoryCreated(
            stackId = generation,
            okHttpClientId = clientId,
            reason = reason,
            config = lastConfig
        )
        if (lastConfig == null) {
            Log.i(TAG, "PLAYBACK_NETWORK stack=AppHttpClient.playbackClient dns=IptvDns")
        }
        return newStack
    }

    @UnstableApi
    private fun buildVodStackLocked(client: OkHttpClient): PlaybackSourceStack {
        val generation = (vodStack?.generation ?: 0) + 1
        val clientId = System.identityHashCode(client)
        val dataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(STREAM_USER_AGENT)
        val loadErrorPolicy = VodLoadErrorHandlingPolicy(playbackMetricsLogger)
        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)
        val mediaSourceFactory = IptvMediaSourceFactory(
            dataSourceFactory = dataSourceFactory,
            hlsFactory = hlsFactory,
            streamFormatRegistry = streamFormatRegistry
        )
            .also { it.setLoadErrorHandlingPolicy(loadErrorPolicy) }
        val newStack = PlaybackSourceStack(
            generation = generation,
            okHttpClientId = clientId,
            dataSourceFactory = dataSourceFactory,
            mediaSourceFactory = mediaSourceFactory
        )
        vodStack = newStack
        Log.i(TAG, "PLAYBACK_NETWORK vodStack generation=$generation")
        return newStack
    }

    private data class PlaybackSourceStack(
        val generation: Int,
        val okHttpClientId: Int,
        val dataSourceFactory: DataSource.Factory,
        val mediaSourceFactory: MediaSource.Factory
    )

    @UnstableApi
    internal class IptvMediaSourceFactory(
        dataSourceFactory: DataSource.Factory,
        private val hlsFactory: HlsMediaSource.Factory,
        private val streamFormatRegistry: IptvStreamFormatRegistry
    ) : MediaSource.Factory {
        private val defaultFactory = DefaultMediaSourceFactory(dataSourceFactory)

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            val format = resolveFormat(uri, mediaItem)
            if (format == IptvStreamFormat.UNKNOWN) {
                Log.w(TAG, "MEDIA_SOURCE_SELECTED source=none urlHash=${uri.hashCode()} format=UNKNOWN")
            } else {
                StreamTypeDetector.logMediaSourceSelected(format, uri)
            }
            if (StreamTypeDetector.isTsUrl(uri)) {
                return defaultFactory.createMediaSource(stripHlsMimeType(mediaItem))
            }
            return if (format.isHls()) {
                hlsFactory.createMediaSource(ensureHlsMimeType(mediaItem))
            } else {
                defaultFactory.createMediaSource(stripHlsMimeType(mediaItem))
            }
        }

        private fun resolveFormat(uri: String, mediaItem: MediaItem): IptvStreamFormat {
            if (StreamTypeDetector.isTsUrl(uri)) {
                return IptvStreamFormat.PROGRESSIVE
            }
            readMetadataFormat(mediaItem)?.let { return it }
            streamFormatRegistry.get(uri)?.let { return it }
            return when (IptvOnDemandMediaItem.readPlaybackScope(mediaItem)) {
                IptvPlaybackScope.ON_DEMAND -> {
                    val contentKind = IptvOnDemandMediaItem.readContentKind(mediaItem)
                        ?: IptvOnDemandContentKind.VOD_MOVIE
                    IptvStreamFormatDetector.resolveForOnDemandPlayback(
                        url = uri,
                        contentKind = contentKind,
                        mediaItem = mediaItem,
                        registry = streamFormatRegistry
                    )
                }
                IptvPlaybackScope.LIVE -> StreamTypeDetector.classifyUrlPath(uri)
            }
        }

        private fun readMetadataFormat(mediaItem: MediaItem): IptvStreamFormat? {
            val extras = mediaItem.mediaMetadata?.extras ?: return null
            return when (extras.getString(IptvStreamFormatDetector.METADATA_KEY_STREAM_FORMAT)?.lowercase()) {
                "hls" -> IptvStreamFormat.HLS
                "progressive" -> IptvStreamFormat.PROGRESSIVE
                "unknown" -> IptvStreamFormat.UNKNOWN
                else -> null
            }
        }

        private fun stripHlsMimeType(mediaItem: MediaItem): MediaItem {
            val mime = mediaItem.localConfiguration?.mimeType ?: return mediaItem
            if (mime != androidx.media3.common.MimeTypes.APPLICATION_M3U8) return mediaItem
            return mediaItem.buildUpon().setMimeType(null).build()
        }

        private fun ensureHlsMimeType(mediaItem: MediaItem): MediaItem {
            val mime = mediaItem.localConfiguration?.mimeType
            if (mime == androidx.media3.common.MimeTypes.APPLICATION_M3U8) return mediaItem
            return mediaItem.buildUpon()
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                .build()
        }

        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider
        ): MediaSource.Factory {
            defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            hlsFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            return this
        }

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy
        ): MediaSource.Factory {
            defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            hlsFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            return this
        }

        override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes
    }

    companion object {
        private const val TAG = "PlaybackNetwork"
        const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 GridTV/1.0"
    }
}
