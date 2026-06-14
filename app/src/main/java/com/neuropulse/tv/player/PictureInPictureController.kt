package com.neuropulse.tv.player

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PictureInPictureController @Inject constructor() {
    @Volatile
    var playbackActive: Boolean = false
        private set

    @Volatile
    var pictureInPictureEnabled: Boolean = true

    fun setPlaybackActive(active: Boolean) {
        playbackActive = active
    }

    fun canEnterPictureInPicture(): Boolean = pictureInPictureEnabled && playbackActive
}
