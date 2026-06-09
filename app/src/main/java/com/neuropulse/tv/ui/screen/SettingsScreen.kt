package com.neuropulse.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.EpgRowHeight
import com.neuropulse.tv.domain.model.PlaylistType
import com.neuropulse.tv.domain.model.UserProfile
import com.neuropulse.tv.ui.component.EpgNavTab
import com.neuropulse.tv.ui.component.EpgTopBar
import com.neuropulse.tv.ui.component.GridNavTabs
import com.neuropulse.tv.ui.component.ProfileAvatarBadge
import com.neuropulse.tv.ui.component.PinEntryDialog
import com.neuropulse.tv.ui.component.SettingsChip
import com.neuropulse.tv.ui.component.SettingsListRow
import com.neuropulse.tv.ui.component.SettingsNavItem
import com.neuropulse.tv.ui.component.SettingsPanel
import com.neuropulse.tv.ui.component.SettingsSidebar
import com.neuropulse.tv.ui.component.SettingsTextField
import com.neuropulse.tv.ui.component.SettingsToggleRow
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.ProfileViewModel
import com.neuropulse.tv.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class SettingsSection(val title: String, val subtitle: String) {
    Profile("Profile", "Who's watching & parental"),
    Connections("Connections", "Playlists & IPTV login"),
    Guide("Guide & EPG", "Refresh & channel mapping"),
    Playback("Playback", "Player, audio & sleep"),
    Recordings("Recordings", "Storage & save location"),
    About("About", "Version, backup & info")
}

private enum class SettingsFocusZone { TOP_BAR, SIDEBAR, CONTENT }

private val TopBarProfileIndex get() = GridNavTabs.size

@Composable
fun SettingsScreen(
    profileInitials: String = "?",
    onNavigateHome: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onOpenEpgResolver: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageOptions by viewModel.storageOptions.collectAsStateWithLifecycle()
    val currentStorageLabel by viewModel.currentStorageLabel.collectAsStateWithLifecycle()
    val progress by viewModel.m3uProgress.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val xtreamAccounts by viewModel.xtreamAccounts.collectAsStateWithLifecycle()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by profileViewModel.activeProfile.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showHideAdultPinDialog by remember { mutableStateOf(false) }
    var pendingHideAdultValue by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val sections = SettingsSection.entries
    val navItems = sections.map { SettingsNavItem(it.title, it.subtitle) }

    var selectedSection by remember { mutableIntStateOf(0) }
    var focusZone by remember { mutableStateOf(SettingsFocusZone.SIDEBAR) }
    var sidebarFocusIndex by remember { mutableIntStateOf(0) }
    var topBarFocusIndex by remember { mutableIntStateOf(3) }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var refreshHours by remember { mutableStateOf("24") }
    var playlistType by remember { mutableStateOf(PlaylistType.M3U) }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    val sidebarFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val topNavFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusZone) {
        when (focusZone) {
            SettingsFocusZone.TOP_BAR -> topNavFocusRequester.requestFocus()
            SettingsFocusZone.SIDEBAR -> sidebarFocusRequester.requestFocus()
            SettingsFocusZone.CONTENT -> contentFocusRequester.requestFocus()
        }
    }

    fun activateNavTab(tab: EpgNavTab) {
        when (tab) {
            EpgNavTab.Home -> onNavigateHome()
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> onOpenFavorites()
            EpgNavTab.Search -> onNavigateHome()
            EpgNavTab.Settings -> Unit
        }
    }

    fun handleTopBarKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (profileMenuOpen) {
            return when (event.key) {
                Key.DirectionUp -> {
                    profileMenuFocusIndex = (profileMenuFocusIndex - 1).coerceAtLeast(0)
                    true
                }
                Key.DirectionDown -> {
                    profileMenuFocusIndex = (profileMenuFocusIndex + 1).coerceAtMost(1)
                    true
                }
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    profileMenuOpen = false
                    when (profileMenuFocusIndex) {
                        0 -> onSwitchProfile()
                        1 -> Unit
                    }
                    true
                }
                else -> false
            }
        }
        return when (event.key) {
            Key.DirectionLeft -> {
                topBarFocusIndex = (topBarFocusIndex - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionRight -> {
                topBarFocusIndex = (topBarFocusIndex + 1).coerceAtMost(TopBarProfileIndex)
                true
            }
            Key.DirectionDown -> {
                focusZone = SettingsFocusZone.SIDEBAR
                sidebarFocusIndex = selectedSection
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                when (topBarFocusIndex) {
                    in GridNavTabs.indices -> activateNavTab(GridNavTabs[topBarFocusIndex])
                    TopBarProfileIndex -> {
                        profileMenuOpen = true
                        profileMenuFocusIndex = 0
                    }
                }
                true
            }
            else -> false
        }
    }

    fun handleSidebarKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionUp -> {
                if (sidebarFocusIndex > 0) sidebarFocusIndex -= 1 else {
                    focusZone = SettingsFocusZone.TOP_BAR
                }
                true
            }
            Key.DirectionDown -> {
                if (sidebarFocusIndex < sections.lastIndex) sidebarFocusIndex += 1
                true
            }
            Key.DirectionRight -> {
                focusZone = SettingsFocusZone.CONTENT
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                selectedSection = sidebarFocusIndex
                focusZone = SettingsFocusZone.CONTENT
                true
            }
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
                selectedTab = EpgNavTab.Settings,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusZone == SettingsFocusZone.TOP_BAR &&
                    topBarFocusIndex <= GridNavTabs.lastIndex,
                profileFocused = focusZone == SettingsFocusZone.TOP_BAR &&
                    topBarFocusIndex == TopBarProfileIndex,
                profileInitials = profileInitials,
                profileMenuExpanded = profileMenuOpen,
                profileMenuFocusIndex = profileMenuFocusIndex,
                onProfileClick = {
                    profileMenuOpen = true
                    profileMenuFocusIndex = 0
                },
                onSwitchAccounts = onSwitchProfile,
                onOpenSettings = { profileMenuOpen = false },
                onTabSelected = { tab ->
                    topBarFocusIndex = GridNavTabs.indexOf(tab)
                    activateNavTab(tab)
                },
                miniPlayer = {},
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusZone == SettingsFocusZone.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .focusRequester(sidebarFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent {
                            if (focusZone == SettingsFocusZone.SIDEBAR) handleSidebarKey(it) else false
                        }
                ) {
                    SettingsSidebar(
                        items = navItems,
                        selectedIndex = selectedSection,
                        focusedIndex = sidebarFocusIndex,
                        sidebarFocused = focusZone == SettingsFocusZone.SIDEBAR
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(contentFocusRequester)
                        .focusable()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (sections[selectedSection]) {
                        SettingsSection.Profile -> ProfileSettingsContent(
                            activeProfile = activeProfile,
                            profiles = profiles,
                            settings = settings,
                            onSwitchProfile = onSwitchProfile,
                            onSelectProfile = { profileViewModel.switchProfile(it) },
                            onToggleMiniAudio = { viewModel.updateMiniPlayerAudio(!settings.miniPlayerAudioEnabled) },
                            onToggleHideAdult = {
                                val next = !settings.hideAdultContent
                                if (next && activeProfile?.hasPin == true) {
                                    pendingHideAdultValue = true
                                    showHideAdultPinDialog = true
                                } else {
                                    viewModel.updateHideAdultContent(next)
                                }
                            }
                        )
                        SettingsSection.Connections -> ConnectionsSettingsContent(
                            name = name,
                            onNameChange = { name = it },
                            url = url,
                            onUrlChange = { url = it },
                            epgUrl = epgUrl,
                            onEpgUrlChange = { epgUrl = it },
                            refreshHours = refreshHours,
                            onRefreshHoursChange = { refreshHours = it },
                            playlistType = playlistType,
                            onPlaylistTypeChange = { playlistType = it },
                            xtreamServer = xtreamServer,
                            onXtreamServerChange = { xtreamServer = it },
                            xtreamUser = xtreamUser,
                            onXtreamUserChange = { xtreamUser = it },
                            xtreamPass = xtreamPass,
                            onXtreamPassChange = { xtreamPass = it },
                            progress = progress,
                            playlists = playlists,
                            xtreamAccounts = xtreamAccounts,
                            onAddPlaylist = {
                                if (playlistType == PlaylistType.XTREAM) {
                                    viewModel.addXtreamPlaylist(
                                        name, xtreamServer, xtreamUser, xtreamPass,
                                        epgUrl.ifBlank { null }, refreshHours.toIntOrNull() ?: 24
                                    )
                                } else {
                                    viewModel.addPlaylistFromUrl(
                                        name, url, epgUrl.ifBlank { null }, refreshHours.toIntOrNull() ?: 24
                                    )
                                }
                            },
                            onPickLocalFile = onPickLocalFile,
                            onPickTiviMateZip = onPickTiviMateZip,
                            onDeletePlaylist = { viewModel.deletePlaylist(it) }
                        )
                        SettingsSection.Guide -> GuideSettingsContent(
                            settings = settings,
                            onRefreshEpg = { viewModel.refreshEpg() },
                            onOpenEpgResolver = onOpenEpgResolver,
                            onRowHeight = { viewModel.updateRowHeight(it) }
                        )
                        SettingsSection.Playback -> PlaybackSettingsContent(
                            settings = settings,
                            onRetries = { viewModel.updateRetries(it) },
                            onAudioLanguage = { viewModel.updateAudioLanguage(it) },
                            onSleepTimer = { viewModel.updateSleepTimerMinutes(it) },
                            onToggleMiniAudio = { viewModel.updateMiniPlayerAudio(!settings.miniPlayerAudioEnabled) },
                            onToggleSleepTimerAuto = { viewModel.updateSleepTimerAutoEnabled(!settings.sleepTimerAutoEnabled) }
                        )
                        SettingsSection.Recordings -> RecordingsSettingsContent(
                            currentStorageLabel = currentStorageLabel,
                            storageOptions = storageOptions,
                            onSelectStorage = { viewModel.setRecordingStorage(it) }
                        )
                        SettingsSection.About -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            AboutSettingsContent(
                                importSummary = importSummary,
                                onExportBackup = { viewModel.exportBackup(context.cacheDir) },
                                onResetApp = { showResetConfirm = true }
                            )
                        }
                    }
                }
            }
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Reset app?") },
                text = {
                    Text(
                        "This clears all playlists, channels, guide data, favorites, watch history, and recordings. Your profiles will be kept.",
                        fontFamily = DmSansFamily
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showResetConfirm = false
                        viewModel.resetApp { onNavigateHome() }
                    }) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    Button(onClick = { showResetConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showHideAdultPinDialog && activeProfile != null) {
            PinEntryDialog(
                profileName = activeProfile.name,
                title = "Enter PIN",
                subtitle = "PIN required to hide adult content",
                onVerified = {
                    showHideAdultPinDialog = false
                    pendingHideAdultValue?.let { viewModel.updateHideAdultContent(it) }
                    pendingHideAdultValue = null
                },
                onDismiss = {
                    showHideAdultPinDialog = false
                    pendingHideAdultValue = null
                },
                verifyPin = { pin -> profileViewModel.verifyPin(activeProfile.id, pin) }
            )
        }
    }
}

@Composable
private fun ProfileSettingsContent(
    activeProfile: UserProfile?,
    profiles: List<UserProfile>,
    settings: com.neuropulse.tv.domain.model.AppSettings,
    onSwitchProfile: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onToggleMiniAudio: () -> Unit,
    onToggleHideAdult: () -> Unit
) {
    SettingsPanel(
        title = "Active profile",
        description = "Profiles keep separate favorites, watch history, and parental rules."
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileAvatarBadge(
                initials = activeProfile?.name?.take(2)?.uppercase() ?: "?",
                colorHex = activeProfile?.avatarColor ?: "#3B8FFF"
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeProfile?.name ?: "No profile",
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val parental = if (activeProfile?.isParental == true) "Parental controls on" else "Standard profile"
                Text(
                    text = parental,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Button(onClick = onSwitchProfile) { Text("Manage profiles") }
        }
    }

    SettingsPanel(title = "All profiles", description = "Select a profile for this device.") {
        profiles.forEach { profile ->
            val isActive = profile.id == activeProfile?.id
            SettingsListRow(
                title = profile.name,
                subtitle = buildString {
                    if (profile.isParental) append("Parental · ")
                    if (profile.hasPin) append("PIN protected · ")
                    append(if (isActive) "Active" else "Tap to switch")
                },
                isFocused = false,
                modifier = Modifier.padding(vertical = 4.dp),
                trailing = {
                    if (isActive) {
                        Text("●", color = EpgColors.Accent, fontSize = 12.sp)
                    } else {
                        Button(onClick = { onSelectProfile(profile.id) }) { Text("Use") }
                    }
                }
            )
        }
        if (profiles.isEmpty()) {
            Text(
                "No profiles yet. Open Manage profiles to create one.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
        }
    }

    SettingsPanel(
        title = "Parental controls",
        description = "Filter adult channel groups from the guide and search."
    ) {
        SettingsToggleRow(
            label = "Hide adult content",
            description = if (activeProfile?.hasPin == true) {
                "PIN required to turn on"
            } else {
                "Blocks channels in adult groups (18+, XXX, etc.)"
            },
            enabled = settings.hideAdultContent,
            onToggle = onToggleHideAdult
        )
    }

    SettingsPanel(title = "Profile preferences") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsChip(
                label = if (settings.miniPlayerAudioEnabled) "Mini player audio ON" else "Mini player audio OFF",
                selected = settings.miniPlayerAudioEnabled
            )
            Button(onClick = onToggleMiniAudio) {
                Text(if (settings.miniPlayerAudioEnabled) "Turn off" else "Turn on")
            }
        }
    }
}

@Composable
private fun ConnectionsSettingsContent(
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    epgUrl: String,
    onEpgUrlChange: (String) -> Unit,
    refreshHours: String,
    onRefreshHoursChange: (String) -> Unit,
    playlistType: PlaylistType,
    onPlaylistTypeChange: (PlaylistType) -> Unit,
    xtreamServer: String,
    onXtreamServerChange: (String) -> Unit,
    xtreamUser: String,
    onXtreamUserChange: (String) -> Unit,
    xtreamPass: String,
    onXtreamPassChange: (String) -> Unit,
    progress: String,
    playlists: List<com.neuropulse.tv.domain.model.Playlist>,
    xtreamAccounts: List<com.neuropulse.tv.domain.model.XtreamAccountInfo>,
    onAddPlaylist: () -> Unit,
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    SettingsPanel(
        title = "Add connection",
        description = "Link your IPTV provider using M3U or Xtream Codes."
    ) {
        SettingsTextField(
            label = "Connection name",
            value = name,
            onValueChange = onNameChange,
            placeholder = "e.g. Home IPTV"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlaylistTypeChange(PlaylistType.M3U) }) {
                Text(
                    "M3U",
                    fontWeight = if (playlistType == PlaylistType.M3U) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (playlistType == PlaylistType.M3U) EpgColors.Accent else EpgColors.TextSecondary
                )
            }
            Button(onClick = { onPlaylistTypeChange(PlaylistType.XTREAM) }) {
                Text(
                    "Xtream",
                    fontWeight = if (playlistType == PlaylistType.XTREAM) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (playlistType == PlaylistType.XTREAM) EpgColors.Accent else EpgColors.TextSecondary
                )
            }
        }
        if (playlistType == PlaylistType.M3U) {
            SettingsTextField(
                label = "M3U URL",
                value = url,
                onValueChange = onUrlChange,
                placeholder = "https://provider.com/playlist.m3u"
            )
        } else {
            SettingsTextField(
                label = "Server URL",
                value = xtreamServer,
                onValueChange = onXtreamServerChange,
                placeholder = "http://server:port"
            )
            SettingsTextField(
                label = "Username",
                value = xtreamUser,
                onValueChange = onXtreamUserChange,
                placeholder = "Username"
            )
            SettingsTextField(
                label = "Password",
                value = xtreamPass,
                onValueChange = onXtreamPassChange,
                placeholder = "Password"
            )
        }
        SettingsTextField(
            label = "EPG URL (optional)",
            value = epgUrl,
            onValueChange = onEpgUrlChange,
            placeholder = "https://provider.com/epg.xml"
        )
        SettingsTextField(
            label = "Refresh every (hours)",
            value = refreshHours,
            onValueChange = onRefreshHoursChange,
            placeholder = "24"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddPlaylist) { Text("Add connection") }
            Button(onClick = onPickLocalFile) { Text("Local M3U file") }
            Button(onClick = onPickTiviMateZip) { Text("Import TiviMate") }
        }
        Text(
            text = "Status: $progress",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp
        )
    }

    SettingsPanel(
        title = "Installed connections",
        description = "${playlists.size} playlist(s) on this device."
    ) {
        if (playlists.isEmpty()) {
            Text(
                "No connections yet. Add your M3U or Xtream details above.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
        }
        playlists.forEach { playlist ->
            SettingsListRow(
                title = playlist.name,
                subtitle = "${playlist.type} · refresh every ${playlist.refreshIntervalHours}h",
                isFocused = false,
                modifier = Modifier.padding(vertical = 4.dp),
                trailing = {
                    Button(onClick = { onDeletePlaylist(playlist.id) }) { Text("Remove") }
                }
            )
        }
        xtreamAccounts.forEach { account ->
            val exp = account.expiryDateEpochSec?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it * 1000L))
            } ?: "N/A"
            Text(
                "${account.playlistName}: ${account.status} · expires $exp",
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GuideSettingsContent(
    settings: com.neuropulse.tv.domain.model.AppSettings,
    onRefreshEpg: () -> Unit,
    onOpenEpgResolver: () -> Unit,
    onRowHeight: (EpgRowHeight) -> Unit
) {
    SettingsPanel(title = "EPG data", description = "Keep the guide up to date.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefreshEpg) { Text("Refresh EPG now") }
            Button(onClick = onOpenEpgResolver) { Text("Auto-match channels") }
        }
    }
    SettingsPanel(title = "Guide row height") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EpgRowHeight.entries.forEach { height ->
                SettingsChip(
                    label = height.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = settings.epgRowHeight == height
                )
                Button(onClick = { onRowHeight(height) }) {
                    Text(height.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}

@Composable
private fun PlaybackSettingsContent(
    settings: com.neuropulse.tv.domain.model.AppSettings,
    onRetries: (Int) -> Unit,
    onAudioLanguage: (String) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onToggleMiniAudio: () -> Unit,
    onToggleSleepTimerAuto: () -> Unit
) {
    SettingsPanel(title = "Stream playback") {
        Text(
            "Stream retries: ${settings.streamRetries}",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 5).forEach { n ->
                Button(onClick = { onRetries(n) }) {
                    Text(
                        "$n",
                        fontWeight = if (settings.streamRetries == n) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (settings.streamRetries == n) EpgColors.Accent else EpgColors.TextSecondary
                    )
                }
            }
        }
        SettingsTextField(
            label = "Preferred audio language",
            value = settings.preferredAudioLanguage,
            onValueChange = onAudioLanguage,
            placeholder = "en"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsChip(
                label = if (settings.miniPlayerAudioEnabled) "Mini player audio ON" else "Mini player audio OFF",
                selected = settings.miniPlayerAudioEnabled
            )
            Button(onClick = onToggleMiniAudio) { Text("Toggle") }
        }
    }
    SettingsPanel(
        title = "Auto sleep timer",
        description = "Automatically start a sleep timer when full-screen playback begins."
    ) {
        SettingsToggleRow(
            label = "Auto sleep timer",
            description = if (settings.sleepTimerAutoEnabled) {
                "Starts a ${settings.sleepTimerMinutes} min timer when you open the player"
            } else {
                "Off — set a timer manually from the player menu"
            },
            enabled = settings.sleepTimerAutoEnabled,
            onToggle = onToggleSleepTimerAuto
        )
    }
    SettingsPanel(title = "Sleep timer duration") {
        Text(
            "Default: ${settings.sleepTimerMinutes} minutes",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90).forEach { min ->
                Button(onClick = { onSleepTimer(min) }) {
                    Text(
                        "$min",
                        fontWeight = if (settings.sleepTimerMinutes == min) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (settings.sleepTimerMinutes == min) EpgColors.Accent else EpgColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingsSettingsContent(
    currentStorageLabel: String?,
    storageOptions: List<com.neuropulse.tv.feature.recording.StorageOption>,
    onSelectStorage: (String) -> Unit
) {
    SettingsPanel(
        title = "Recording storage",
        description = "Choose where completed recordings are saved."
    ) {
        Text(
            text = currentStorageLabel ?: "Not configured",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        storageOptions.forEach { option ->
            SettingsListRow(
                title = option.label,
                subtitle = option.displayLine(),
                isFocused = false,
                modifier = Modifier.padding(vertical = 4.dp),
                trailing = {
                    Button(onClick = { onSelectStorage(option.id) }) { Text("Use") }
                }
            )
        }
    }
}

@Composable
private fun AboutSettingsContent(
    importSummary: String?,
    onExportBackup: () -> Unit,
    onResetApp: () -> Unit
) {
    SettingsPanel(title = "GRID") {
        Text("Version ${SettingsViewModel.APP_VERSION}", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
        Text("Live TV Guide for Android TV", color = EpgColors.TextDimmed, fontFamily = DmSansFamily, fontSize = 13.sp)
        Button(onClick = onExportBackup, modifier = Modifier.padding(top = 8.dp)) {
            Text("Export .grid backup")
        }
        importSummary?.let {
            Text(it, color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 13.sp)
        }
    }
    SettingsPanel(
        title = "Reset app",
        description = "Clear all playlists, guide data, favorites, and recordings. Profiles are kept."
    ) {
        Button(onClick = onResetApp) {
            Text("Reset app", color = EpgColors.TextPrimary)
        }
    }
}
