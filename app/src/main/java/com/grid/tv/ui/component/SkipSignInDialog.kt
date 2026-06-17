package com.grid.tv.ui.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun SkipSignInDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val goBackFocusRequester = remember { FocusRequester() }
    val gotItFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        goBackFocusRequester.requestFocusSafelyAfterLayout()
    }

    GridModal(
        onDismiss = onDismiss,
        showCloseButton = false,
        width = 520.dp
    ) {
        Text(
            text = "Continue without an account?",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Creating an account lets you sync watch progress, settings, and profiles across your devices.",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "You can create an account at any time from the Settings menu.",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GridOutlinedButton(
                text = "Go back",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(goBackFocusRequester)
            )
            GridPrimaryButton(
                text = "Got it",
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(gotItFocusRequester)
            )
        }
    }
}
