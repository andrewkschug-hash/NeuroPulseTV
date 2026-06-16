package com.neuropulse.tv.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Handles the system back button once per press.
 *
 * NavHost registers its own [BackHandler] that pops the back stack. Screens must not
 * also call [onNavigateBack] from [Key.Back] key handlers or the stack is popped twice
 * and the activity exits to the TV launcher.
 */
@Composable
fun ScreenBackHandler(
    onNavigateBack: () -> Unit,
    onBackPressed: () -> Boolean = { false }
) {
    BackHandler {
        if (!onBackPressed()) {
            onNavigateBack()
        }
    }
}
