package com.grid.tv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.StreamHealthDao
import com.grid.tv.data.db.entity.StreamHealthEntity
import com.grid.tv.feature.health.StreamHealthEngine
import com.grid.tv.feature.scanner.ChannelProbe
import com.grid.tv.feature.scanner.ChannelScanner
import com.grid.tv.feature.scanner.ProbeResult
import com.grid.tv.feature.scanner.ScanConcurrencyLimiter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@HiltWorker
class ChannelHealthProbeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val channelDao: ChannelDao,
    private val streamHealthDao: StreamHealthDao,
    private val channelProbe: ChannelProbe,
    private val channelScanner: ChannelScanner
) : CoroutineWorker(appContext, params) {

    private val healthEngine = StreamHealthEngine()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (channelScanner.isPlaybackScanSuspended) {
            android.util.Log.i("ChannelHealthProbe", "Skipped — live fullscreen playback active")
            return@withContext Result.success()
        }
        val channels = channelDao.all().take(40)
        channels.forEach { channel ->
            val started = System.currentTimeMillis()
            val success = channelProbe.probe(channel.streamUrl).result == ProbeResult.LIVE
            val loadMs = (System.currentTimeMillis() - started).coerceAtLeast(1)
            val previous = streamHealthDao.get(channel.id)?.let {
                com.grid.tv.domain.model.StreamHealth(
                    it.channelId,
                    it.reliabilityScore,
                    it.averageLoadTimeMs,
                    it.bufferEventsPerSession,
                    it.lastSuccessfulLoad
                )
            }
            val scored = healthEngine.compute(previous, loadMs, bufferEvents = if (success) 0 else 2, success = success)
                .copy(channelId = channel.id)
            streamHealthDao.upsert(
                StreamHealthEntity(
                    channelId = channel.id,
                    lastSuccessfulLoad = scored.lastSuccessfulLoad,
                    bufferEventsPerSession = scored.bufferEventsPerSession,
                    averageLoadTimeMs = scored.averageLoadTimeMs,
                    reliabilityScore = scored.reliabilityScore,
                    sessions = (streamHealthDao.get(channel.id)?.sessions ?: 0) + 1
                )
            )
            delay(ScanConcurrencyLimiter.INTER_PROBE_DELAY_MS)
        }
        Result.success()
    }
}
