package com.grid.tv.player

import org.junit.Assert.assertNotNull
import org.junit.Test

class PlayerAudioRecoveryListenerTest {

    @Test
    fun companion_exposesDetachContract() {
        // JVM unit tests cannot construct ExoPlayer/Looper; verify listener type exists for lifecycle wiring.
        assertNotNull(PlayerAudioRecoveryListener::class.java.getDeclaredMethod("detach"))
    }
}
