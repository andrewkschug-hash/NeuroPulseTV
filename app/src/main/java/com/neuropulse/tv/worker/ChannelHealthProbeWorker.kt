package com.neuropulse.tv.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.StreamHealthDao
import com.neuropulse.tv.data.db.entity.StreamHealthEntity
import com.neuropulse.tv.feature.health.StreamHealthEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.neuropulse.tv.data.network.AppHttpClient
import okhttp3.Request

@HiltWorker
class ChannelHealthProbeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val channelDao: ChannelDao,
    private val streamHealthDao: StreamHealthDao,
    private val appHttpClient: AppHttpClient
) : CoroutineWorker(appContext, params) {

    private val healthEngine = StreamHealthEngine()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val channels = channelDao.all().take(40)
        channels.forEach { channel ->
            val started = System.currentTimeMillis()
            val success = probe(channel.streamUrl)
            val loadMs = (System.currentTimeMillis() - started).coerceAtLeast(1)
            val previous = streamHealthDao.get(channel.id)?.let {
                com.neuropulse.tv.domain.model.StreamHealth(
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
        }
        Result.success()
    }

    private fun probe(url: String): Boolean {
        if (url.isBlank()) return false
        return runCatching {
            val request = Request.Builder().url(url).head().build()
            appHttpClient.client().newCall(request).execute().use { it.isSuccessful }
        }.getOrElse {
            runCatching {
                val request = Request.Builder().url(url).get().header("Range", "bytes=0-1").build()
                appHttpClient.client().newCall(request).execute().use { it.isSuccessful || it.code == 206 }
            }.getOrDefault(false)
        }
    }
}
