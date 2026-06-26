package com.grid.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.Choreographer
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
import com.grid.tv.feature.startup.StartupSafety
import com.grid.tv.feature.startup.StartupTiming
import com.grid.tv.feature.startup.StartupUiIdleHook
import com.grid.tv.feature.search.MicSearchTrigger
import com.grid.tv.feature.search.VoiceSearchKeys
import com.grid.tv.ui.theme.GridTheme
import com.grid.tv.ui.theme.ThemeManager
import com.grid.tv.ui.navigation.AppRoot
import com.grid.tv.ui.viewmodel.SettingsViewModel
import android.view.inputmethod.InputMethodManager
import com.grid.tv.player.AppPlayerLifecycleCoordinator
import com.grid.tv.util.TvImeKeyDispatcher
import com.grid.tv.util.TvRemoteKeyboard
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.isTelevision
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var micSearchTrigger: MicSearchTrigger
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var appPlayerLifecycle: AppPlayerLifecycleCoordinator
    @Inject lateinit var startupSafety: StartupSafety

    init {
        StartupTiming.log("MainActivity constructor")
    }

    private val settingsViewModel: SettingsViewModel by viewModels()
    private var pickerMode: PickerMode = PickerMode.M3U

    private var authDeepLinkHandler: (() -> Unit)? = null
    private var pendingOAuthSession = false

    private val pickM3u = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            when (pickerMode) {
                PickerMode.M3U -> {
                    val content = readText(uri)
                    withContext(Dispatchers.Main) {
                        settingsViewModel.addPlaylistFromLocal(
                            name = "Local Playlist",
                            content = content,
                            epg = null,
                            refreshHours = 24
                        )
                    }
                }
                PickerMode.TIVIMATE -> withContext(Dispatchers.Main) {
                    settingsViewModel.importTiviMate(contentResolver, uri, cacheDir)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTiming.log("MainActivity.onCreate() entry")
        StartupTiming.log("MainActivity.before super.onCreate() (Hilt field injection runs inside super)")
        StartupTiming.trace("MainActivity.super.onCreate (Hilt Activity inject)") {
            super.onCreate(savedInstanceState)
        }
        StartupTiming.log("MainActivity.after super.onCreate() (Hilt injection complete)")
        requestedOrientation = if (isTelevision()) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        setTitle(getString(R.string.app_name))

        lifecycleScope.launch(Dispatchers.IO) {
            startupSafety.awaitInputSafe()
            themeManager.loadFromSettings()
        }

        handleAuthDeepLink(intent)

        StartupTiming.log("MainActivity.before setContent()")
        setContent {
            StartupUiIdleHook(startupSafety)
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
        StartupTiming.log("MainActivity.after setContent()")
        scheduleFirstFrameTiming()
    }

    private fun scheduleFirstFrameTiming() {
        window.decorView.post {
            StartupTiming.log("MainActivity.decorView.post (composition scheduled)")
            Choreographer.getInstance().postFrameCallback {
                StartupTiming.log("MainActivity.first frame drawn (Choreographer callback)")
                StartupTiming.dumpReportOnce("first_frame")
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

    override fun onStart() {
        super.onStart()
        appPlayerLifecycle.onActivityStarted(this)
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            appPlayerLifecycle.onActivityStopped(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            appPlayerLifecycle.onActivityDestroyFinishing(applicationContext)
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            startupSafety.signalUiActivity("key")
            val imm = getSystemService(InputMethodManager::class.java)
            val imeActive = imm?.isAcceptingText == true || TvTextInputSession.isActive
            if (imeActive && TvImeKeyDispatcher.isImeNavigationKeyCode(event.keyCode)) {
                return dispatchKeyEventSafely(event)
            }
            if (VoiceSearchKeys.isMicKey(event.keyCode)) {
                micSearchTrigger.trigger()
                return true
            }
            if (TvRemoteKeyboard.isActivateKey(event.keyCode)) {
                if (TvRemoteKeyboard.activateFocusedTextInput(window.decorView)) {
                    return true
                }
            }
        }
        if (TvTextInputSession.shouldSuppressPostImeFocusSearch()) {
            return true
        }
        return dispatchKeyEventSafely(event)
    }

    private fun dispatchKeyEventSafely(event: KeyEvent): Boolean {
        return try {
            super.dispatchKeyEvent(event)
        } catch (error: IllegalStateException) {
            if (error.message?.contains("ActiveParent must have a focusedChild") == true) {
                TvTextInputSession.beginClosingGracePeriod()
                true
            } else {
                throw error
            }
        }
    }

    private fun readText(uri: Uri): String {
        contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) return ""
            return BufferedReader(InputStreamReader(stream)).readText()
        }
    }

    private enum class PickerMode { M3U, TIVIMATE }
}
