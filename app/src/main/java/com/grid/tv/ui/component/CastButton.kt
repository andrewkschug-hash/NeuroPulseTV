package com.grid.tv.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.grid.tv.cast.ChromecastController
import com.grid.tv.di.PlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    chromecastController: ChromecastController? = null
) {
    val context = LocalContext.current
    val controller = chromecastController ?: rememberChromecastController()
    if (!controller.isSupported()) return
    if (controller.ensureInitialized() == null) return

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            MediaRouteButton(viewContext).apply {
                CastButtonFactory.setUpMediaRouteButton(viewContext.applicationContext, this)
            }
        }
    )
}

@Composable
private fun rememberChromecastController(): ChromecastController {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .chromecastController()
    }
}
