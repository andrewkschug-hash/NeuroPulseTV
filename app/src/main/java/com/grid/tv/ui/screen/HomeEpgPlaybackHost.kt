package com.grid.tv.ui.screen

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.StreamPlaybackStatus
import com.grid.tv.ui.viewmodel.HomeEpgViewModel

/** Side effects for live playback — no UI, isolated recomposition scope. */
@Composable
internal fun HomeEpgLivePlaybackSideEffects(
    livePlayerManager: LivePlayerManager,
    viewModel: HomeEpgViewModel,
    context: Context,
    channels: List<com.grid.tv.domain.model.Channel>,
    guidePreviewEnabled: Boolean,
    previewChannelId: Long?
) {
    LaunchedEffect(livePlayerManager, channels) {
        livePlayerManager.playbackUiState.collect { ui ->
            ui.activeChannelId?.let { id ->
                channels.find { it.id == id }?.let { viewModel.setLastPlayedChannel(it) }
            }
        }
    }
    LaunchedEffect(livePlayerManager) {
        livePlayerManager.playbackUiState.collect { ui ->
            ui.activeChannelId?.let { id ->
                viewModel.reportPlaybackHealth(id, ui.status)
            }
        }
    }
    LaunchedEffect(guidePreviewEnabled, previewChannelId, livePlayerManager) {
        livePlayerManager.playbackUiState.collect { ui ->
            if (livePlayerManager.isFullscreenActive()) return@collect
            if (!guidePreviewEnabled) return@collect
            val previewId = previewChannelId ?: return@collect
            if (ui.activeChannelId != previewId) return@collect
            viewModel.resumeGuidePreviewIfEnabled(context)
        }
    }
}

@Composable
internal fun rememberPreviewPlaybackPlayer(
    livePlayerManager: LivePlayerManager,
    context: Context,
    previewChannelId: Long?,
    guidePreviewEnabled: Boolean
): ExoPlayer? {
    val playerGeneration by livePlayerManager.playerGeneration.collectAsStateWithLifecycle()
    val needsPreviewPlayer = guidePreviewEnabled && previewChannelId != null
    return remember(playerGeneration, context, needsPreviewPlayer) {
        if (needsPreviewPlayer) {
            livePlayerManager.activePlayer() ?: livePlayerManager.getOrCreatePlayer(context)
        } else {
            null
        }
    }
}

@Composable
internal fun rememberPreviewStreamStatus(
    livePlayerManager: LivePlayerManager,
    previewChannelId: Long?
): StreamPlaybackStatus {
    val playbackUi by livePlayerManager.playbackUiState.collectAsStateWithLifecycle()
    return if (previewChannelId == playbackUi.activeChannelId) {
        playbackUi.status
    } else {
        StreamPlaybackStatus.LOADING
    }
}
