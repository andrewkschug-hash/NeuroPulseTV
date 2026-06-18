package com.grid.tv.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grid.tv.domain.model.EpgFetchAttempt
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.recording.SeriesRuleScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IptvRepository,
    private val seriesRuleScheduler: SeriesRuleScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i(
            TAG,
            "EpgRefreshWorker started (workId=$id, runAttempt=$runAttemptCount, tags=$tags)"
        )
        return try {
            Log.i(TAG, "Invoking repository.refreshEpgNow()")
            val report = repository.refreshEpgNow()
            logRefreshReport(report.attempts)
            Log.i(
                TAG,
                "refreshEpgNow summary: playlists=${report.playlistsTotal}, " +
                    "fetches=${report.urlsAttempted}, bytes=${report.totalBytesReceived}, " +
                    "channelsStored=${report.totalChannelsStored}, " +
                    "programmesStored=${report.totalProgrammesStored}, " +
                    "failures=${report.failures.size}"
            )
            Log.i(TAG, "Applying series recording rules after EPG refresh")
            seriesRuleScheduler.applyRulesAfterEpgRefresh()
            Log.i(TAG, "EpgRefreshWorker finished OK")
            Result.success()
        } catch (e: CancellationException) {
            Log.w(TAG, "EpgRefreshWorker cancelled", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "EpgRefreshWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun logRefreshReport(attempts: List<EpgFetchAttempt>) {
        if (attempts.isEmpty()) {
            Log.w(TAG, "No playlists processed — check that a playlist exists in the DB")
            return
        }
        attempts.forEach { attempt ->
            when {
                attempt.skippedReason != null -> Log.w(
                    TAG,
                    "Playlist '${attempt.playlistName}' (id=${attempt.playlistId}) skipped: " +
                        attempt.skippedReason
                )
                attempt.error != null -> Log.e(
                    TAG,
                    "Playlist '${attempt.playlistName}' (id=${attempt.playlistId}) failed: " +
                        "url=${attempt.url}, http=${attempt.httpCode}, error=${attempt.error}"
                )
                else -> Log.i(
                    TAG,
                    "Playlist '${attempt.playlistName}' (id=${attempt.playlistId}) " +
                        "${attempt.endpointKind}: url=${attempt.url}, http=${attempt.httpCode}, " +
                        "bytes=${attempt.bytesReceived}, parsed=${attempt.channelsParsed} channels / " +
                        "${attempt.programmesParsed} programmes, stored=${attempt.channelsStored} channels / " +
                        "${attempt.programmesStored} programmes"
                )
            }
        }
    }

    companion object {
        private const val TAG = "EpgFlow"
    }
}
