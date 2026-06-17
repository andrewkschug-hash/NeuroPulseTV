package com.grid.tv.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSkippedSignIn(): Boolean = prefs.getBoolean(KEY_SKIPPED_SIGN_IN, false)

    fun setSkippedSignIn(skipped: Boolean) {
        prefs.edit().putBoolean(KEY_SKIPPED_SIGN_IN, skipped).apply()
    }

    companion object {
        private const val PREFS_NAME = "grid_auth"
        private const val KEY_SKIPPED_SIGN_IN = "skipped_sign_in"
    }
}
