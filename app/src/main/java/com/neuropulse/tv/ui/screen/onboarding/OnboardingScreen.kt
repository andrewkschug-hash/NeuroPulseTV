package com.neuropulse.tv.ui.screen.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuropulse.tv.ui.component.GridWordmark
import com.neuropulse.tv.ui.component.TvBackButton
import com.neuropulse.tv.ui.component.TvFocusChain
import com.neuropulse.tv.ui.component.TvTextLink
import com.neuropulse.tv.ui.component.rememberTvFocusChain
import com.neuropulse.tv.ui.component.tvVerticalDpadNavigation
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.viewmodel.OnboardingConnectState
import com.neuropulse.tv.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.delay

private enum class OnboardingStep {
    MethodPicker,
    Xtream,
    M3u,
    Stalker,
    Success
}

private enum class OnboardingMethod { Xtream, M3u, Stalker }

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit = onComplete,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()
    val connectResult by viewModel.connectResult.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(OnboardingStep.MethodPicker) }
    var successAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(connectState, connectResult) {
        if (connectState == OnboardingConnectState.Success && connectResult != null) {
            step = OnboardingStep.Success
            delay(1500)
            successAlpha = 0f
            delay(400)
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingBg)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState == OnboardingStep.MethodPicker) {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                } else if (initialState == OnboardingStep.MethodPicker) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "onboardingStep"
        ) { current ->
            when (current) {
                OnboardingStep.MethodPicker -> MethodPickerScreen(
                    onSelect = { method ->
                        viewModel.resetConnectState()
                        step = when (method) {
                            OnboardingMethod.Xtream -> OnboardingStep.Xtream
                            OnboardingMethod.M3u -> OnboardingStep.M3u
                            OnboardingMethod.Stalker -> OnboardingStep.Stalker
                        }
                    },
                    onSkip = onSkip
                )
                OnboardingStep.Xtream -> XtreamEntryScreen(
                    loading = connectState == OnboardingConnectState.Connecting,
                    errorMessage = errorMessage,
                    onBack = {
                        viewModel.resetConnectState()
                        step = OnboardingStep.MethodPicker
                    },
                    onConnect = { name, server, user, pass ->
                        viewModel.connectXtream(name, server, user, pass)
                    }
                )
                OnboardingStep.M3u -> M3uEntryScreen(
                    loading = connectState == OnboardingConnectState.Connecting,
                    errorMessage = errorMessage,
                    onBack = {
                        viewModel.resetConnectState()
                        step = OnboardingStep.MethodPicker
                    },
                    onConnect = { name, url ->
                        viewModel.connectM3u(name, url)
                    }
                )
                OnboardingStep.Stalker -> StalkerEntryScreen(
                    loading = connectState == OnboardingConnectState.Connecting,
                    errorMessage = errorMessage,
                    deviceMac = viewModel.deviceMac,
                    onBack = {
                        viewModel.resetConnectState()
                        step = OnboardingStep.MethodPicker
                    },
                    onConnect = { name, portal, mac ->
                        viewModel.connectStalker(name, portal, mac)
                    }
                )
                OnboardingStep.Success -> SuccessScreen(
                    playlistName = connectResult?.playlistName.orEmpty(),
                    channelCount = connectResult?.channelCount ?: 0,
                    modifier = Modifier.graphicsLayer { alpha = successAlpha }
                )
            }
        }

    }
}

@Composable
private fun MethodPickerScreen(
    onSelect: (OnboardingMethod) -> Unit,
    onSkip: () -> Unit
) {
    val focusChain = rememberTvFocusChain(count = 4, startIndex = 0)
    var focusedMethod by remember { mutableStateOf(OnboardingMethod.Xtream) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .tvVerticalDpadNavigation(
                chain = focusChain,
                onBack = onSkip,
                isEditing = { false },
                onDismissEditing = {}
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.fillMaxHeight(0.12f))
        GridWordmark(fontSize = 28.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Connect your IPTV service",
            color = OnboardingTextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Choose how your provider gave you access",
            color = OnboardingTextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MethodCard(
                icon = "((o))",
                iconColor = OnboardingAccent,
                title = "Xtream Codes",
                subtitle = "Server URL + username + password",
                badge = "Most Common",
                selected = focusedMethod == OnboardingMethod.Xtream,
                onClick = { onSelect(OnboardingMethod.Xtream) },
                onFocused = {
                    focusedMethod = OnboardingMethod.Xtream
                    focusChain.onItemFocused(0)
                },
                modifier = Modifier.focusRequester(focusChain.requesters[0])
            )
            MethodCard(
                icon = "∞",
                iconColor = Color(0xFFB8C0D8),
                title = "M3U URL",
                subtitle = "A single playlist link",
                selected = focusedMethod == OnboardingMethod.M3u,
                onClick = { onSelect(OnboardingMethod.M3u) },
                onFocused = {
                    focusedMethod = OnboardingMethod.M3u
                    focusChain.onItemFocused(1)
                },
                modifier = Modifier.focusRequester(focusChain.requesters[1])
            )
            MethodCard(
                icon = "▣",
                iconColor = Color(0xFFB8C0D8),
                title = "MAC / Stalker Portal",
                subtitle = "Portal URL + device MAC address",
                selected = focusedMethod == OnboardingMethod.Stalker,
                onClick = { onSelect(OnboardingMethod.Stalker) },
                onFocused = {
                    focusedMethod = OnboardingMethod.Stalker
                    focusChain.onItemFocused(2)
                },
                modifier = Modifier.focusRequester(focusChain.requesters[2])
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        OnboardingSkipLink(
            onClick = onSkip,
            modifier = Modifier
                .focusRequester(focusChain.requesters[3])
                .onFocusChanged { if (it.isFocused) focusChain.onItemFocused(3) }
        )
    }
}

@Composable
private fun XtreamEntryScreen(
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onConnect: (name: String, server: String, user: String, pass: String) -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }
    var showNameField by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val focusChain = rememberTvFocusChain(count = 6, startIndex = 0)

    EntryScaffold(
        title = "Xtream Codes",
        subtitle = "Enter the details your IPTV provider gave you",
        onBack = onBack,
        focusChain = focusChain,
        isEditing = isEditing,
        onDismissEditing = { isEditing = false }
    ) {
        if (errorMessage != null) {
            OnboardingErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }
        OnboardingTextField(
            label = "Server URL",
            value = serverUrl,
            onValueChange = { serverUrl = it },
            placeholder = "http://provider.com:8080",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            focusRequester = focusChain.requesters[1],
            chainIndex = 1,
            chain = focusChain,
            onEditingChanged = { isEditing = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OnboardingTextField(
            label = "Username",
            value = username,
            onValueChange = { username = it },
            placeholder = "your_username",
            focusRequester = focusChain.requesters[2],
            chainIndex = 2,
            chain = focusChain,
            onEditingChanged = { isEditing = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OnboardingTextField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "your_password",
            isPassword = true,
            focusRequester = focusChain.requesters[3],
            chainIndex = 3,
            chain = focusChain,
            onEditingChanged = { isEditing = it }
        )
        Spacer(modifier = Modifier.height(24.dp))
        ConnectButton(
            loading = loading,
            onClick = { onConnect(playlistName, serverUrl, username, password) },
            focusRequester = focusChain.requesters[4],
            chainIndex = 4,
            chain = focusChain
        )
        if (!showNameField) {
            Spacer(modifier = Modifier.height(12.dp))
            TvTextLink(
                text = "+ Add a name for this playlist",
                onClick = { showNameField = true },
                focusRequester = focusChain.requesters[5],
                chainIndex = 5,
                chain = focusChain
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingTextField(
                label = "Playlist name",
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = "Main TV",
                focusRequester = focusChain.requesters[5],
                chainIndex = 5,
                chain = focusChain,
                onEditingChanged = { isEditing = it }
            )
        }
    }
}

@Composable
private fun M3uEntryScreen(
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onConnect: (name: String, url: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }
    var showNameField by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val focusChain = rememberTvFocusChain(count = 4, startIndex = 0)

    EntryScaffold(
        title = "M3U URL",
        subtitle = "Paste the playlist link from your provider",
        onBack = onBack,
        focusChain = focusChain,
        isEditing = isEditing,
        onDismissEditing = { isEditing = false }
    ) {
        if (errorMessage != null) {
            OnboardingErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }
        OnboardingTextField(
            label = "M3U URL",
            value = url,
            onValueChange = { url = it },
            placeholder = "http://provider.com/playlist.m3u",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            focusRequester = focusChain.requesters[1],
            chainIndex = 1,
            chain = focusChain,
            onEditingChanged = { isEditing = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your M3U link may look like:\nhttp://server.com:port/get.php?username=X&password=Y&type=m3u",
            color = OnboardingTextMuted,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        ConnectButton(
            loading = loading,
            onClick = { onConnect(playlistName, url) },
            focusRequester = focusChain.requesters[2],
            chainIndex = 2,
            chain = focusChain
        )
        if (!showNameField) {
            Spacer(modifier = Modifier.height(12.dp))
            TvTextLink(
                text = "+ Add a name for this playlist",
                onClick = { showNameField = true },
                focusRequester = focusChain.requesters[3],
                chainIndex = 3,
                chain = focusChain
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingTextField(
                label = "Playlist name",
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = "Main TV",
                focusRequester = focusChain.requesters[3],
                chainIndex = 3,
                chain = focusChain,
                onEditingChanged = { isEditing = it }
            )
        }
    }
}

@Composable
private fun StalkerEntryScreen(
    loading: Boolean,
    errorMessage: String?,
    deviceMac: String?,
    onBack: () -> Unit,
    onConnect: (name: String, portal: String, mac: String) -> Unit
) {
    var portalUrl by remember { mutableStateOf("") }
    var macAddress by remember(deviceMac) { mutableStateOf(deviceMac.orEmpty()) }
    var useDeviceMac by remember { mutableStateOf(deviceMac != null) }
    var playlistName by remember { mutableStateOf("") }
    var showNameField by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val focusChain = rememberTvFocusChain(count = 5, startIndex = 0)

    EntryScaffold(
        title = "MAC / Stalker Portal",
        subtitle = "Enter your portal URL and device MAC address",
        onBack = onBack,
        focusChain = focusChain,
        isEditing = isEditing,
        onDismissEditing = { isEditing = false }
    ) {
        if (errorMessage != null) {
            OnboardingErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }
        OnboardingTextField(
            label = "Portal URL",
            value = portalUrl,
            onValueChange = { portalUrl = it },
            placeholder = "http://provider.com/stalker_portal",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            focusRequester = focusChain.requesters[1],
            chainIndex = 1,
            chain = focusChain,
            onEditingChanged = { isEditing = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OnboardingTextField(
            label = "MAC Address",
            value = if (useDeviceMac) deviceMac.orEmpty() else macAddress,
            onValueChange = {
                useDeviceMac = false
                macAddress = it
            },
            placeholder = "00:1A:79:XX:XX:XX",
            focusRequester = focusChain.requesters[2],
            chainIndex = 2,
            chain = focusChain,
            onEditingChanged = { isEditing = it }
        )
        if (deviceMac != null) {
            Text(
                text = "Your device MAC: $deviceMac",
                color = OnboardingTextMuted,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TvTextLink(
                text = if (useDeviceMac) "✓ Use device MAC automatically" else "Use device MAC automatically",
                onClick = {
                    useDeviceMac = !useDeviceMac
                    if (useDeviceMac) macAddress = deviceMac
                }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        ConnectButton(
            loading = loading,
            onClick = {
                val mac = if (useDeviceMac) deviceMac.orEmpty() else macAddress
                onConnect(playlistName, portalUrl, mac)
            },
            focusRequester = focusChain.requesters[3],
            chainIndex = 3,
            chain = focusChain
        )
        if (!showNameField) {
            Spacer(modifier = Modifier.height(12.dp))
            TvTextLink(
                text = "+ Add a name for this playlist",
                onClick = { showNameField = true },
                focusRequester = focusChain.requesters[4],
                chainIndex = 4,
                chain = focusChain
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingTextField(
                label = "Playlist name",
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = "Main TV",
                focusRequester = focusChain.requesters[4],
                chainIndex = 4,
                chain = focusChain,
                onEditingChanged = { isEditing = it }
            )
        }
    }
}

@Composable
private fun EntryScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    focusChain: TvFocusChain,
    isEditing: Boolean,
    onDismissEditing: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 24.dp)
            .tvVerticalDpadNavigation(
                chain = focusChain,
                onBack = onBack,
                isEditing = { isEditing },
                onDismissEditing = onDismissEditing
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TvBackButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
                focusRequester = focusChain.requesters[0],
                chainIndex = 0,
                chain = focusChain
            )
            GridWordmark(
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            color = OnboardingTextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            color = OnboardingTextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Column(modifier = Modifier.width(480.dp)) {
            content()
        }
    }
}

@Composable
private fun SuccessScreen(
    playlistName: String,
    channelCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedCheckmark()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "You're all set!",
            color = OnboardingTextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "$playlistName connected",
            color = OnboardingTextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (channelCount > 0) {
            Text(
                text = "$channelCount channels loaded",
                color = OnboardingAccent,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
