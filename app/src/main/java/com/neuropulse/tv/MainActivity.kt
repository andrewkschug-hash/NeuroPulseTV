package com.neuropulse.tv

import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.neuropulse.tv.domain.repository.IptvRepository
import com.neuropulse.tv.feature.search.MicSearchTrigger
import com.neuropulse.tv.feature.search.VoiceSearchKeys
import com.neuropulse.tv.player.PictureInPictureController
import com.neuropulse.tv.ui.navigation.AppRoot
import com.neuropulse.tv.ui.theme.GridTheme
import com.neuropulse.tv.ui.theme.ThemeManager
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var micSearchTrigger: MicSearchTrigger
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var pipController: PictureInPictureController
    @Inject lateinit var repository: IptvRepository

    private val settingsViewModel: SettingsViewModel by viewModels()
    private var pickerMode: PickerMode = PickerMode.M3U

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
        setTitle(getString(R.string.app_name))

        lifecycleScope.launch {
            themeManager.loadFromSettings()
            pipController.pictureInPictureEnabled = repository.loadSettings().pictureInPictureEnabled
        }

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && VoiceSearchKeys.isMicKey(event.keyCode)) {
            micSearchTrigger.trigger()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            isInPictureInPictureMode.not() &&
            pipController.canEnterPictureInPicture()
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            pipController.setPlaybackActive(pipController.playbackActive)
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
