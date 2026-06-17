package com.grid.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.grid.tv.di.SupabaseEntryPoint
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.github.jan.supabase.auth.handleDeeplinks
import com.grid.tv.feature.search.MicSearchTrigger
import com.grid.tv.feature.search.VoiceSearchKeys
import com.grid.tv.ui.navigation.AppRoot
import com.grid.tv.ui.theme.GridTheme
import com.grid.tv.ui.theme.ThemeManager
import com.grid.tv.ui.viewmodel.SettingsViewModel
import com.grid.tv.util.isTelevision
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var micSearchTrigger: MicSearchTrigger
    @Inject lateinit var themeManager: ThemeManager

    private val settingsViewModel: SettingsViewModel by viewModels()
    private var pickerMode: PickerMode = PickerMode.M3U

    private var authDeepLinkHandler: (() -> Unit)? = null
    private var pendingOAuthSession = false

    private val pickM3u = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        when (pickerMode) {
            PickerMode.M3U -> settingsViewModel.addPlaylistFromLocal(
                name = "Local Playlist",
                content = readText(uri),
                epg = null,
                refreshHours = 24
            )
            PickerMode.TIVIMATE -> settingsViewModel.importTiviMate(contentResolver, uri, cacheDir)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (isTelevision()) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        setTitle(getString(R.string.app_name))

        lifecycleScope.launch {
            themeManager.loadFromSettings()
        }

        handleAuthDeepLink(intent)

        setContent {
            val themeId by themeManager.themeId.collectAsStateWithLifecycle()
            GridTheme(themeId = themeId) {
                AppRoot(
                    onPickLocalFile = {
                        pickerMode = PickerMode.M3U
                        pickM3u.launch(arrayOf("*/*"))
                    },
                    onPickTiviMateZip = {
                        pickerMode = PickerMode.TIVIMATE
                        pickM3u.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    fun registerAuthDeepLinkHandler(handler: () -> Unit) {
        authDeepLinkHandler = handler
        if (pendingOAuthSession) {
            pendingOAuthSession = false
            handler()
        }
    }

    fun clearAuthDeepLinkHandler() {
        authDeepLinkHandler = null
    }

    private fun notifyAuthSessionEstablished() {
        val handler = authDeepLinkHandler
        if (handler != null) {
            handler()
        } else {
            pendingOAuthSession = true
        }
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "com.grid.tv" || data.host != "auth") return
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SupabaseEntryPoint::class.java
        )
        if (!entryPoint.supabaseClientProvider().isConfigured) return
        val client = entryPoint.supabaseClientProvider().clientOrNull() ?: return
        runCatching {
            client.handleDeeplinks(intent) {
                notifyAuthSessionEstablished()
            }
        }
        intent.data = null
        setIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && VoiceSearchKeys.isMicKey(event.keyCode)) {
            micSearchTrigger.trigger()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun readText(uri: Uri): String {
        contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) return ""
            return BufferedReader(InputStreamReader(stream)).readText()
        }
    }

    private enum class PickerMode { M3U, TIVIMATE }
}
