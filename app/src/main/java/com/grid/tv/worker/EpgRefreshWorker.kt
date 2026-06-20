package com.grid.tv.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grid.tv.domain.model.EpgFetchAttempt
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.epg.EpgFlowLogger
import com.grid.tv.feature.epg.EpgJobCoordinator
import com.grid.tv.feature.epg.EpgJobSource
import com.grid.tv.feature.scanner.ChannelScanGate
import com.grid.tv.feature.recording.SeriesRuleScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: IptvRepository,
    private val channelScanGate: ChannelScanGate,
    private val epgJobCoordinator: EpgJobCoordinator,
    private val seriesRuleScheduler: SeriesRuleScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(KEY_SOURCE)?.let { raw ->
            runCatching { EpgJobSource.valueOf(raw) }.getOrNull()
        }
        Log.i(
            TAG,
            "EpgRefreshWorker started source=$source workId=$id runAttempt=$runAttemptCount tags=$tags"
        )
        return try {
            if (source == EpgJobSource.IMPORT || source == EpgJobSource.STARTUP) {
                channelScanGate.awaitValidationIdle()
                if (channelScanGate.isValidationActive.value) {
                    Log.w(TAG, "EpgRefreshWorker deferred — priority validation still active source=$source")
                    return Result.retry()
                }
            }
            Log.i(TAG, "Invoking repository.refreshEpgNow() source=$source")
            val report = repository.refreshEpgNow()
            logRefreshReport(report.attempts)
            Log.i(
                TAG,
                "refreshEpgNow summary source=$source playlists=${report.playlistsTotal}, " +
                    "fetches=${report.urlsAttempted}, bytes=${report.totalBytesReceived}, " +
                    "channelsStored=${report.totalChannelsStored}, " +
                    "programmesStored=${report.totalProgrammesStored}, " +
                    "failures=${report.failures.size}"
            )
            Log.i(TAG, "Applying series recording rules after EPG refresh source=$source")
            seriesRuleScheduler.applyRulesAfterEpgRefresh()
            Log.i(TAG, "EpgRefreshWorker finished OK source=$source")
            Result.success()
        } catch (e: CancellationException) {
            Log.i(TAG, "EpgRefreshWorker superseded or stopped source=$source")
            throw e
        } catch (e: Exception) {
            EpgFlowLogger.importFailed(playlistId = -1L, playlistName = "all", url = null, error = e)
            Log.e(TAG, "EpgRefreshWorker failed source=$source: ${e.message}", e)
            Result.retry()
        } finally {
            if (source == EpgJobSource.IMPORT) {
                epgJobCoordinator.onImportEpgWorkerFinished()
            }
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
        const val KEY_SOURCE = "epg_source"
    }
}
