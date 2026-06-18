package com.grid.tv.ui.component

import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.util.TvImeKeyDispatcher
import com.grid.tv.util.TvRemoteKeyboard
import com.grid.tv.util.TvTextInputSession
import kotlinx.coroutines.launch

private enum class TvInputDialogFocus { Field, Confirm }

/**
 * Full-screen TV text entry overlay. Only the input and Confirm are focusable so the system
 * keyboard receives D-pad events without competing form fields behind the dialog.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvTextInputDialog(
    label: String,
    value: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    confirmLabel: String = "Confirm"
) {
    var draft by remember(value) { mutableStateOf(value) }
    var focusZone by remember { mutableStateOf(TvInputDialogFocus.Field) }
    val fieldFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    val imm = view.context.getSystemService(InputMethodManager::class.java)

    fun isKeyboardVisible(): Boolean =
        imm?.isAcceptingText == true || TvTextInputSession.isActive

    fun openKeyboard() {
        scope.launch { showTextFieldKeyboard(keyboard, view, fieldFocusRequester) }
    }

    fun confirm() {
        keyboard?.hide()
        TvRemoteKeyboard.dismissKeyboard(view)
        onConfirm(draft)
    }

    fun dismiss() {
        keyboard?.hide()
        TvRemoteKeyboard.dismissKeyboard(view)
        onDismiss()
    }

    DisposableEffect(Unit) {
        TvTextInputSession.begin()
        onDispose {
            TvTextInputSession.end()
            keyboard?.hide()
            TvRemoteKeyboard.dismissKeyboard(view)
        }
    }

    LaunchedEffect(Unit) {
        focusZone = TvInputDialogFocus.Field
        fieldFocusRequester.requestFocusSafelyAfterLayout()
        openKeyboard()
    }

    LaunchedEffect(focusZone) {
        when (focusZone) {
            TvInputDialogFocus.Field -> {
                fieldFocusRequester.requestFocusSafelyAfterLayout()
                openKeyboard()
            }
            TvInputDialogFocus.Confirm -> {
                keyboard?.hide()
                TvRemoteKeyboard.dismissKeyboard(view)
                confirmFocusRequester.requestFocusSafelyAfterLayout()
            }
        }
    }

    fun handleDialogKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.Back, Key.Escape -> {
                dismiss()
                true
            }
            Key.DirectionDown -> {
                when (focusZone) {
                    TvInputDialogFocus.Field -> {
                        if (isKeyboardVisible()) return false
                        focusZone = TvInputDialogFocus.Confirm
                        true
                    }
                    TvInputDialogFocus.Confirm -> false
                }
            }
            Key.DirectionUp -> {
                if (focusZone == TvInputDialogFocus.Confirm) {
                    focusZone = TvInputDialogFocus.Field
                    true
                } else {
                    false
                }
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (focusZone) {
                    // Enter/OK on the on-screen keyboard must reach the IME, not the dialog.
                    TvInputDialogFocus.Field -> false
                    TvInputDialogFocus.Confirm -> {
                        confirm()
                        true
                    }
                }
            }
            Key.DirectionLeft, Key.DirectionRight -> {
                if (focusZone == TvInputDialogFocus.Field && isKeyboardVisible()) false else false
            }
            else -> false
        }
    }

    BackHandler(onBack = ::dismiss)

    Dialog(
        onDismissRequest = {
            // Only Back should close; ignore platform dismiss from Enter/OK.
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0101018))
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (focusZone == TvInputDialogFocus.Field &&
                        TvImeKeyDispatcher.isImeNavigationKey(event.key)
                    ) {
                        return@onPreviewKeyEvent false
                    }
                    handleDialogKey(event)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = Color(0xFFF2F2F5),
                    fontFamily = DmSansFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(20.dp))
                val fieldShape = RoundedCornerShape(8.dp)
                val borderColor = if (fieldFocused) TvFocusAccent else Color(0xFF3A3A4A)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.None
                    ),
                    keyboardActions = KeyboardActions.Default,
                    visualTransformation = if (isPassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    cursorBrush = SolidColor(TvFocusAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(2.dp, borderColor, fieldShape)
                        .background(Color(0xFF252836), fieldShape)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .focusRequester(fieldFocusRequester)
                        .focusable(interactionSource = fieldInteraction)
                        .focusProperties {
                            down = confirmFocusRequester
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    dismiss()
                                    true
                                }
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter,
                                Key.DirectionUp, Key.DirectionDown,
                                Key.DirectionLeft, Key.DirectionRight -> false
                                else -> false
                            }
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = Color(0xFFB8BEC8),
                                    fontFamily = DmSansFamily,
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(28.dp))
                GridPrimaryButton(
                    text = confirmLabel,
                    onClick = ::confirm,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .focusRequester(confirmFocusRequester)
                        .focusProperties {
                            up = fieldFocusRequester
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    confirm()
                                    true
                                }
                                Key.Back, Key.Escape -> {
                                    dismiss()
                                    true
                                }
                                else -> false
                            }
                        },
                    contentDescription = confirmLabel
                )
            }
        }
    }
}
