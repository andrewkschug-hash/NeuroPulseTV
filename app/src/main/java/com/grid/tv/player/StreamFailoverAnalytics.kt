package com.grid.tv.player

import android.util.Log
import com.grid.tv.data.db.dao.StreamFailoverStatsDao
import com.grid.tv.data.db.entity.StreamFailoverStatsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class StreamFailoverAnalytics(
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

    fun recordSuccessfulRecovery(channelId: Long, attemptsUsed: Int = 1) {
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
        Log.i(
            TAG,
            "RECOVERY_SUCCESS channelId=$channelId attemptsUsed=$attemptsUsed"
        )
    }

    fun recordFinalFailure(channelId: Long, attemptsUsed: Int) {
        Log.w(
            TAG,
            "RECOVERY_FINAL_FAILURE channelId=$channelId attemptsUsed=$attemptsUsed"
        )
    }

    fun logRetryAttempt(
        channelId: Long?,
        trigger: String,
        category: String,
        attempt: Int,
        maxAttempts: Int,
        step: String
    ) {
        Log.i(
            TAG,
            "RETRY_ATTEMPT channelId=$channelId trigger=$trigger category=$category " +
                "attempt=$attempt/$maxAttempts step=${step.take(120)}"
        )
    }

    fun logRecoverySuccess(
        channelId: Long?,
        trigger: String,
        category: String,
        attemptsUsed: Int,
        maxAttempts: Int
    ) {
        Log.i(
            TAG,
            "RETRY_SUCCESS channelId=$channelId trigger=$trigger category=$category " +
                "attemptsUsed=$attemptsUsed maxAttempts=$maxAttempts"
        )
    }

    fun logFinalFailure(
        channelId: Long?,
        trigger: String,
        category: String,
        attemptsUsed: Int,
        maxAttempts: Int
    ) {
        Log.w(
            TAG,
            "RETRY_FINAL_FAILURE channelId=$channelId trigger=$trigger category=$category " +
                "attemptsUsed=$attemptsUsed maxAttempts=$maxAttempts"
        )
    }

    fun problematicChannels(limit: Int = 20): Flow<List<StreamFailoverStatsEntity>> =
        dao.problematicChannels(limit)

    companion object {
        private const val TAG = "StreamFailover"
    }
}
