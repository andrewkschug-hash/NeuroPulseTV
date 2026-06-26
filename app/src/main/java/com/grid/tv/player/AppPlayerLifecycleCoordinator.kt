package com.grid.tv.player

import android.content.Context
import android.util.Log
import com.grid.tv.feature.preview.PreviewPlayerManager
import com.grid.tv.player.multiview.MultiViewManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Defers ExoPlayer teardown while the app is briefly backgrounded; releases decoders and
 * buffers after [BACKGROUND_RELEASE_DELAY_MS] unless an active playback session is running.
 *
 * Teardown order on background timeout / destroy:
 * 1. [PreviewPlayerManager] (guide / browser mini preview via [LivePlayerManager])
 * 2. [MultiPanePlaybackPool] (Split View + MultiView pane ExoPlayers)
 * 3. [MultiViewManager.releaseAll] (same pool; idempotent)
 * 4. [LivePlayerManager] full release
 *
 * Split View pane players live in [MultiPanePlaybackPool], not in the composable — screen
 * [DisposableEffect] calls [PlaybackOrchestrator.releaseSession] on exit which clears the pool.
 */
class AppPlayerLifecycleCoordinator(
    private val livePlayerManager: LivePlayerManager,
    private val previewPlayerManager: PreviewPlayerManager,
    private val pipController: PictureInPictureController,
    private val playbackOrchestrator: PlaybackOrchestrator,
    private val multiPanePlaybackPool: MultiPanePlaybackPool,
    private val multiViewManager: MultiViewManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var backgroundReleaseJob: Job? = null

    fun onActivityStarted(context: Context) {
        backgroundReleaseJob?.cancel()
        backgroundReleaseJob = null
        livePlayerManager.restoreSessionAfterBackgroundRelease(context)
    }

    fun onActivityStopped(context: Context) {
        backgroundReleaseJob?.cancel()
        backgroundReleaseJob = scope.launch {
            delay(BACKGROUND_RELEASE_DELAY_MS)
            if (shouldRetainForActivePlayback()) {
                Log.i(TAG, "Background release skipped — active playback session")
                return@launch
            }
            releaseDeferredPlayback(context.applicationContext)
        }
    }

    fun onActivityDestroyFinishing(context: Context) {
        backgroundReleaseJob?.cancel()
        backgroundReleaseJob = null
        releaseAllPlaybackImmediate(context.applicationContext)
    }

    private fun releaseDeferredPlayback(appContext: Context) {
        previewPlayerManager.stopPreview(appContext)
        multiPanePlaybackPool.releaseAll()
        multiViewManager.releaseAll()
        if (livePlayerManager.hasPlayerInstance()) {
            livePlayerManager.releasePlayerResources(appContext, reason = "background_timeout")
        }
    }

    private fun releaseAllPlaybackImmediate(appContext: Context) {
        previewPlayerManager.stopPreview(appContext)
        playbackOrchestrator.releaseAllPlayback(appContext)
        multiViewManager.releaseAll()
        livePlayerManager.release(appContext)
    }

    private fun shouldRetainForActivePlayback(): Boolean {
        if (pipController.playbackActive && livePlayerManager.isPlaybackActive()) {
            return true
        }
        return livePlayerManager.shouldRetainPlayerInBackground()
    }

    companion object {
        private const val TAG = "AppPlayerLifecycle"
        /** Idle/mini preview held in memory for quick resume; released after this delay. */
        const val BACKGROUND_RELEASE_DELAY_MS = 300_000L
    }
}
