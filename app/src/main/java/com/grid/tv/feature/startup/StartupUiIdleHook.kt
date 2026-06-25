package com.grid.tv.feature.startup

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import java.util.concurrent.atomic.AtomicBoolean

/** Signals UI ready, recomposition, scroll, and focus changes for [UiIdleMonitor]. */
@Composable
fun StartupUiIdleHook(startupSafety: StartupSafety) {
    val readyMarked = remember { AtomicBoolean(false) }
    SideEffect {
        if (readyMarked.compareAndSet(false, true)) {
            startupSafety.markUiReady()
        } else {
            startupSafety.signalUiActivity("recomposition")
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val scrollListener = View.OnScrollChangeListener { _, _, _, _, _ ->
            startupSafety.signalUiActivity("scroll")
        }
        view.setOnScrollChangeListener(scrollListener)

        val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
            startupSafety.signalUiActivity("focus")
        }
        view.viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)

        onDispose {
            view.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
            view.viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
        }
    }
}
