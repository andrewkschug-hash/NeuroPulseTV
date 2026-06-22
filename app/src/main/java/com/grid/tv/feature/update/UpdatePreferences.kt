package com.grid.tv.feature.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun dismissedVersion(): String? = prefs.getString(KEY_DISMISSED_VERSION, null)

    fun dismissVersion(version: String) {
        prefs.edit().putString(KEY_DISMISSED_VERSION, version).apply()
    }

    private companion object {
        const val PREFS_NAME = "app_update"
        const val KEY_DISMISSED_VERSION = "dismissed_version"
    }
}
