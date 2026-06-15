package com.neuropulse.tv.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
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

    fun enterPictureInPicture(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !canEnterPictureInPicture()) {
            return false
        }
        return runCatching {
            activity.enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }.getOrDefault(false)
    }
}
