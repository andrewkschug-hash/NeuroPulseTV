package com.grid.tv.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.grid.tv.util.isTelevision

class PictureInPictureController() {
    @Volatile
    var playbackActive: Boolean = false
        private set

    @Volatile
    var pictureInPictureEnabled: Boolean = true

    fun setPlaybackActive(active: Boolean) {
        playbackActive = active
    }

    fun canEnterPictureInPicture(): Boolean = pictureInPictureEnabled && playbackActive

    fun isSystemPictureInPictureSupported(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (activity.isTelevision()) return false
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterPictureInPicture(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !canEnterPictureInPicture()) {
            return false
        }
        if (!isSystemPictureInPictureSupported(activity)) return false
        val componentActivity = activity as? ComponentActivity
        if (componentActivity != null &&
            !componentActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return false
        }
        return runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setSeamlessResizeEnabled(true)
            }
            activity.enterPictureInPictureMode(builder.build())
        }.getOrDefault(false)
    }
}
