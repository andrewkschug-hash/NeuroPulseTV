package com.grid.tv.util

import android.app.Activity
import android.content.Context

/** Closes the app and returns the user to the device home screen. */
fun Context.quitAppToHome() {
    (this as? Activity)?.finishAffinity()
}
