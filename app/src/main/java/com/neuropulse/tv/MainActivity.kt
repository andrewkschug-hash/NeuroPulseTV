package com.neuropulse.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.neuropulse.tv.ui.navigation.AppNavHost
import com.neuropulse.tv.ui.theme.StreamFlowTheme
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var localFileText: ((String) -> Unit)? = null
    private var tiviImport: ((Uri) -> Unit)? = null
    private var pickerMode: PickerMode = PickerMode.M3U

    private val pickM3u = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        when (pickerMode) {
            PickerMode.M3U -> localFileText?.invoke(readText(uri))
            PickerMode.TIVIMATE -> tiviImport?.invoke(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val name = remember { mutableStateOf("Local Playlist") }
            val epg = remember { mutableStateOf<String?>(null) }
            val interval = remember { mutableStateOf(24) }

            localFileText = { content ->
                settingsViewModel.addPlaylistFromLocal(name.value, content, epg.value, interval.value)
            }

            tiviImport = { uri ->
                settingsViewModel.importTiviMate(contentResolver, uri, cacheDir)
            }

            StreamFlowTheme {
                AppNavHost(
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

    private fun readText(uri: Uri): String {
        contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) return ""
            return BufferedReader(InputStreamReader(stream)).readText()
        }
    }

    private enum class PickerMode { M3U, TIVIMATE }
}
