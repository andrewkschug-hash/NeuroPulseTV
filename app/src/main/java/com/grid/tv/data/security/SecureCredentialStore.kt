package com.grid.tv.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed playlist credentials. Survives in-place APK updates on the same device.
 * Does **not** survive cloud backup restore to a new device (Android Keystore keys are device-bound);
 * [createPrefs] recreates an empty store in that case — user must re-enter connection passwords.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private fun createPrefs(context: Context): SharedPreferences {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return try {
            openEncryptedPrefs(context)
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Encrypted credential store unavailable — clearing stale prefs " +
                    "(common after cloud restore to a new device). User must re-enter passwords.",
                error
            )
            context.deleteSharedPreferences(PREFS_NAME)
            try {
                openEncryptedPrefs(context)
            } catch (retryError: Exception) {
                Log.e(TAG, "Falling back to non-encrypted credential prefs", retryError)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveXtreamPassword(playlistId: Long, password: String) {
        prefs.edit().putString(xtreamKey(playlistId), password).apply()
    }

    fun getXtreamPassword(playlistId: Long): String? =
        prefs.getString(xtreamKey(playlistId), null)

    fun saveM3uUrl(playlistId: Long, url: String) {
        prefs.edit().putString(m3uKey(playlistId), url).apply()
    }

    fun getM3uUrl(playlistId: Long): String? =
        prefs.getString(m3uKey(playlistId), null)

    fun saveStalkerCredentials(playlistId: Long, portalUrl: String, macAddress: String) {
        prefs.edit()
            .putString(stalkerPortalKey(playlistId), portalUrl)
            .putString(stalkerMacKey(playlistId), macAddress)
            .apply()
    }

    fun getStalkerPortal(playlistId: Long): String? =
        prefs.getString(stalkerPortalKey(playlistId), null)

    fun getStalkerMac(playlistId: Long): String? =
        prefs.getString(stalkerMacKey(playlistId), null)

    fun removePlaylistCredentials(playlistId: Long) {
        prefs.edit()
            .remove(xtreamKey(playlistId))
            .remove(m3uKey(playlistId))
            .remove(stalkerPortalKey(playlistId))
            .remove(stalkerMacKey(playlistId))
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun saveTmdbApiKey(apiKey: String) {
        prefs.edit().putString(KEY_TMDB_API, apiKey).apply()
    }

    fun getTmdbApiKey(): String? = prefs.getString(KEY_TMDB_API, null)

    fun saveOpenSubtitlesApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENSUBTITLES_API, apiKey).apply()
    }

    fun getOpenSubtitlesApiKey(): String? = prefs.getString(KEY_OPENSUBTITLES_API, null)

    fun saveOpenSubtitlesCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_OPENSUBTITLES_USER, username)
            .putString(KEY_OPENSUBTITLES_PASS, password)
            .apply()
    }

    fun getOpenSubtitlesUsername(): String? = prefs.getString(KEY_OPENSUBTITLES_USER, null)

    fun getOpenSubtitlesPassword(): String? = prefs.getString(KEY_OPENSUBTITLES_PASS, null)

    private fun xtreamKey(playlistId: Long) = "xtream_pass_$playlistId"
    private fun m3uKey(playlistId: Long) = "m3u_url_$playlistId"
    private fun stalkerPortalKey(playlistId: Long) = "stalker_portal_$playlistId"
    private fun stalkerMacKey(playlistId: Long) = "stalker_mac_$playlistId"

    companion object {
        private const val TAG = "SecureCredentialStore"
        /** Excluded from cloud backup — Keystore keys do not transfer to new devices. */
        const val PREFS_NAME = "grid_secure_credentials"
        private const val KEY_TMDB_API = "tmdb_api_key"
        private const val KEY_OPENSUBTITLES_API = "opensubtitles_api_key"
        private const val KEY_OPENSUBTITLES_USER = "opensubtitles_username"
        private const val KEY_OPENSUBTITLES_PASS = "opensubtitles_password"
    }
}
