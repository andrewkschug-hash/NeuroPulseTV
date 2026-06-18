package com.grid.tv.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuestSessionPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGuestSession(): Boolean = prefs.getBoolean(KEY_GUEST_SESSION, false)

    fun guestProfileId(): Long = prefs.getLong(KEY_GUEST_PROFILE_ID, -1L)

    fun startGuestSession(profileId: Long) {
        prefs.edit()
            .putBoolean(KEY_GUEST_SESSION, true)
            .putLong(KEY_GUEST_PROFILE_ID, profileId)
            .apply()
    }

    fun clearGuestSession() {
        prefs.edit()
            .remove(KEY_GUEST_SESSION)
            .remove(KEY_GUEST_PROFILE_ID)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "grid_guest_session"
        private const val KEY_GUEST_SESSION = "guest_session"
        private const val KEY_GUEST_PROFILE_ID = "guest_profile_id"
    }
}
