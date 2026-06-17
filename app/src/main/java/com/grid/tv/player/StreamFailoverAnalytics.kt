package com.grid.tv.player

import com.grid.tv.data.db.dao.StreamFailoverStatsDao
import com.grid.tv.data.db.entity.StreamFailoverStatsEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Singleton
class StreamFailoverAnalytics @Inject constructor(
    private val dao: StreamFailoverStatsDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recordFailover(channelId: Long) {
        scope.launch {
            val now = System.currentTimeMillis()
            val existing = dao.get(channelId)
            dao.upsert(
                (existing ?: StreamFailoverStatsEntity(channelId = channelId)).copy(
                    failoverCount = (existing?.failoverCount ?: 0) + 1,
                    lastFailoverAt = now
                )
            )
        }
    }

    fun recordSuccessfulRecovery(channelId: Long) {
        scope.launch {
            val now = System.currentTimeMillis()
            val existing = dao.get(channelId)
            dao.upsert(
                (existing ?: StreamFailoverStatsEntity(channelId = channelId)).copy(
                    successfulRecoveryCount = (existing?.successfulRecoveryCount ?: 0) + 1,
                    lastRecoveryAt = now
                )
            )
        }
    }

    fun problematicChannels(limit: Int = 20): Flow<List<StreamFailoverStatsEntity>> =
        dao.problematicChannels(limit)
}
