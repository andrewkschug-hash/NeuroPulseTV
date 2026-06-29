package com.grid.tv.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorMessagesTest {

    @Test
    fun codecCapabilityErrorMessage_onEmulator_mentionsEmulatorAndLowerQuality() {
        val message = codecCapabilityErrorMessage(isEmulator = true)

        assertTrue(message.contains("emulator", ignoreCase = true))
        assertTrue(message.contains("lower-quality", ignoreCase = true))
    }

    @Test
    fun codecCapabilityErrorMessage_onDevice_mentionsDeviceNotEmulator() {
        val message = codecCapabilityErrorMessage(isEmulator = false)

        assertTrue(message.contains("device", ignoreCase = true))
        assertFalse(message.contains("emulator", ignoreCase = true))
    }

    @Test
    fun playbackErrorCodes_areDistinctForRecoveryRouting() {
        val formatUnsupported = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        val decoderInitFailed = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        assertTrue(formatUnsupported != decoderInitFailed)
    }
}
