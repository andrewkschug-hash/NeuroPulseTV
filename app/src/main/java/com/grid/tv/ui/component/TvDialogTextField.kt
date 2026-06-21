package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Read-only TV text field that opens [TvTextInputDialog] on Enter/click.
 * Use this everywhere users type text so D-pad keyboard input stays consistent.
 */
@Composable
fun TvDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    error: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    chainIndex: Int = -1,
    chain: TvFocusChain? = null,
    onEditingChanged: (Boolean) -> Unit = {},
    onHighlightChanged: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onConfirmed: (() -> Unit)? = null,
    confirmLabel: String = "Confirm",
    dialogLabel: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    nextFocusRequester: FocusRequester? = null,
    onImeNext: (() -> Unit)? = null,
    fieldHeight: Dp = 48.dp,
    backgroundColor: Color = Color(0xFF252836),
    focusedBorderColor: Color = TvFocusAccent,
    unfocusedBorderColor: Color = Color.Transparent,
    unfocusedBorderWidth: Dp = 0.dp,
    focusedBorderWidth: Dp = 2.dp,
    textColor: Color = Color.White,
    placeholderColor: Color = Color(0xFFB8BEC8),
    labelColor: Color = Color(0xFFB8BEC8),
    showFloatingLabel: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val innerFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: innerFocusRequester
    val scope = rememberCoroutineScope()
    val fieldShape = RoundedCornerShape(8.dp)

    fun openDialog() {
        if (!enabled) return
        showDialog = true
        onEditingChanged(true)
    }

    fun restoreFieldFocus(advanceToNext: Boolean = false) {
        scope.launch {
            delay(50)
            when {
                advanceToNext -> {
                    onImeNext?.invoke()
                    nextFocusRequester?.requestFocusSafely()
                }
                else -> effectiveFocusRequester.requestFocusSafely()
            }
        }
    }

    fun closeDialog(advanceToNext: Boolean = false) {
        showDialog = false
        onEditingChanged(false)
        restoreFieldFocus(advanceToNext = advanceToNext)
    }

    val displayText = when {
        value.isNotEmpty() && isPassword -> "•".repeat(value.length.coerceAtMost(24))
        value.isNotEmpty() -> value
        else -> placeholder
    }
    val displayColor = if (value.isNotEmpty()) textColor else placeholderColor
    val borderColor = when {
        error != null -> Color(0xFFFF4444)
        focused -> focusedBorderColor
        else -> unfocusedBorderColor
    }
    val borderWidth = when {
        error != null -> 2.dp
        focused -> focusedBorderWidth
        else -> unfocusedBorderWidth
    }

    // Keep label space stable so focus is not lost when an empty field is first highlighted.
    if (label != null && showFloatingLabel) {
        Text(
            text = label,
            color = if (focused || value.isNotBlank()) labelColor else Color.Transparent,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }

    GridFocusSurface(
        onClick = ::openDialog,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .focusRequester(effectiveFocusRequester)
            .tvFocusScrollIntoView(minimalScroll = true)
            .then(
                if (nextFocusRequester != null) {
                    Modifier.focusProperties { down = nextFocusRequester }
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                focused = it.isFocused
                onHighlightChanged(it.isFocused)
                if (it.isFocused) chain?.onItemFocused(chainIndex)
            }
            .onPreviewKeyEvent { event ->
                if (isTextFieldActivateKey(event)) {
                    openDialog()
                    true
                } else {
                    false
                }
            }
            .then(
                if (chain != null && chainIndex >= 0) {
                    Modifier.tvFocusChainNavigation(
                        chain = chain,
                        index = chainIndex,
                        onBack = onNavigateBack,
                        isEditing = { showDialog },
                        onDismissEditing = { closeDialog() }
                    )
                } else {
                    Modifier
                }
            ),
        shape = ClickableSurfaceDefaults.shape(fieldShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = backgroundColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(borderWidth, borderColor, fieldShape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = displayText,
                color = displayColor,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (error != null) {
        Text(
            text = error,
            color = Color(0xFFFF4444),
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    if (showDialog) {
        TvTextInputDialog(
            label = dialogLabel ?: label ?: placeholder,
            value = value,
            placeholder = placeholder,
            keyboardType = keyboardType,
            isPassword = isPassword,
            confirmLabel = confirmLabel,
            imeAction = imeAction,
            onConfirm = { confirmed ->
                onValueChange(confirmed)
                val advanceNext = imeAction == ImeAction.Next &&
                    (onImeNext != null || nextFocusRequester != null)
                closeDialog(advanceToNext = advanceNext)
                onConfirmed?.invoke()
            },
            onDismiss = { closeDialog(advanceToNext = false) }
        )
    }
}

/** Compact search bar that opens [TvTextInputDialog] instead of inline IME input. */
@Composable
fun TvDialogSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    label: String = "Search",
    confirmLabel: String = "Search",
    borderColorFocused: Color = EpgColors.Accent,
    borderColorUnfocused: Color = EpgColors.BorderSubtle,
    backgroundColorFocused: Color = EpgColors.Accent.copy(alpha = 0.14f),
    backgroundColorUnfocused: Color = Color(0xFF13131A),
    onPreviewKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false },
    onEditingChanged: (Boolean) -> Unit = {},
    onImeSubmitted: (() -> Unit)? = null,
    downFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (isFocused || showDialog) borderColorFocused else borderColorUnfocused
    val backgroundColor = if (isFocused || showDialog) backgroundColorFocused else backgroundColorUnfocused

    fun openDialog() {
        showDialog = true
        onEditingChanged(true)
    }

    fun closeDialog() {
        showDialog = false
        onEditingChanged(false)
        scope.launch {
            delay(50)
            focusRequester.requestFocusSafely()
        }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .background(backgroundColor, shape)
            .border(2.dp, borderColor, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .focusProperties {
                downFocusRequester?.let { down = it }
            }
            .onFocusChanged { state ->
                onFocusChanged(state.isFocused)
            }
            .onPreviewKeyEvent { event ->
                when {
                    isTextFieldActivateKey(event) -> {
                        openDialog()
                        true
                    }
                    else -> onPreviewKeyEvent(event)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            color = if (value.isEmpty()) EpgColors.TextDimmed else EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showDialog) {
        TvTextInputDialog(
            label = label,
            value = value,
            placeholder = placeholder,
            confirmLabel = confirmLabel,
            imeAction = ImeAction.Search,
            submitOnImeAction = true,
            onImeSubmitted = onImeSubmitted,
            onConfirm = { confirmed ->
                onValueChange(confirmed)
                closeDialog()
            },
            onDismiss = ::closeDialog
        )
    }
}
