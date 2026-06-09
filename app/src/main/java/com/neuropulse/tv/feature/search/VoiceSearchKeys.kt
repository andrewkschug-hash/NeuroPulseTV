package com.neuropulse.tv.feature.search

import android.view.KeyEvent

object VoiceSearchKeys {
    /** Fire TV / Fire Stick remote microphone toggle. */
    const val FIRE_TV_MIC_TOGGLE = 393

    fun isMicKey(keyCode: Int): Boolean =
        keyCode == FIRE_TV_MIC_TOGGLE ||
            keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
            keyCode == KeyEvent.KEYCODE_SEARCH
}
