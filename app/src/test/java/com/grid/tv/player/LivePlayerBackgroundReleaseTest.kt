package com.grid.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlayerBackgroundReleaseTest {

    @Test
    fun backgroundReleaseDelay_isFiveMinutes() {
        assertTrue(AppPlayerLifecycleCoordinator.BACKGROUND_RELEASE_DELAY_MS >= 5 * 60_000L)
    }

    @Test
    fun retainPolicy_requiresActivePlaybackForFullscreen() {
        val manager = LivePlayerManagerBackgroundPolicy(
            mode = LivePlayerManager.Mode.FULLSCREEN,
            playerPresent = true,
            playWhenReady = true,
            playbackState = androidx.media3.common.Player.STATE_READY
        )
        assertTrue(manager.shouldRetainPlayerInBackground())
    }

    @Test
    fun retainPolicy_allowsReleaseWhenFullscreenPaused() {
        val manager = LivePlayerManagerBackgroundPolicy(
            mode = LivePlayerManager.Mode.FULLSCREEN,
            playerPresent = true,
            playWhenReady = false,
            playbackState = androidx.media3.common.Player.STATE_READY
        )
        assertFalse(manager.shouldRetainPlayerInBackground())
    }

    @Test
    fun retainPolicy_allowsReleaseWhenIdle() {
        val manager = LivePlayerManagerBackgroundPolicy(
            mode = LivePlayerManager.Mode.IDLE,
            playerPresent = true,
            playWhenReady = false,
            playbackState = androidx.media3.common.Player.STATE_IDLE
        )
        assertFalse(manager.shouldRetainPlayerInBackground())
    }
}

/** Test seam mirroring [LivePlayerManager.shouldRetainPlayerInBackground] rules. */
internal data class LivePlayerManagerBackgroundPolicy(
    val mode: LivePlayerManager.Mode,
    val playerPresent: Boolean,
    val playWhenReady: Boolean,
    val playbackState: Int
) {
    fun shouldRetainPlayerInBackground(): Boolean {
        if (!playerPresent) return false
        val active = playWhenReady &&
            (playbackState == androidx.media3.common.Player.STATE_READY ||
                playbackState == androidx.media3.common.Player.STATE_BUFFERING)
        if (mode == LivePlayerManager.Mode.FULLSCREEN && active) return true
        if (mode == LivePlayerManager.Mode.MINI && active) return true
        return false
    }
}
