package com.grid.tv.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.model.AspectRatioSetting
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.domain.model.ClockDisplay
import com.grid.tv.domain.model.DpadSensitivity
import com.grid.tv.domain.model.EpgRowHeight
import com.grid.tv.domain.model.MaxContentRating
import com.grid.tv.domain.model.StreamQuality
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.domain.model.ScannerRuntimeState
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.PlaylistType
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.ui.component.VodPlaybackSettingsSection
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.component.SettingsFocusButton
import com.grid.tv.ui.component.SettingsFocusProfileRow
import com.grid.tv.ui.component.SettingsFocusToggleRow
import com.grid.tv.ui.screen.ProfileAvatarColors
import com.grid.tv.ui.component.PinEntryDialog
import com.grid.tv.ui.component.ConnectionLoadingOverlay
import com.grid.tv.ui.component.ConnectionResultDialog
import com.grid.tv.ui.component.DestructiveConfirmDialog
import com.grid.tv.ui.component.FactoryResetConfirmDialog
import com.grid.tv.ui.component.GuideGroupPickerDialog
import com.grid.tv.ui.component.TvScrollContainer
import com.grid.tv.ui.component.SettingsChip
import com.grid.tv.ui.component.SettingsActiveProfileRow
import com.grid.tv.ui.component.SettingsConnectionRow
import com.grid.tv.ui.component.SettingsFocusPanel
import com.grid.tv.ui.component.SettingsFocusButton
import com.grid.tv.ui.component.SettingsFocusProfileRow
import com.grid.tv.ui.component.SettingsListRow
import com.grid.tv.ui.component.SettingsNavItem
import com.grid.tv.ui.component.SettingsPanel
import com.grid.tv.ui.component.ProfileColorPicker
import com.grid.tv.ui.component.SettingsSidebar
import com.grid.tv.ui.component.SettingsTextField
import com.grid.tv.ui.component.SettingsFocusToggleRow
import com.grid.tv.ui.component.SettingsFocusTextField
import com.grid.tv.ui.component.SettingsFocusPillGroup
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.di.SupabaseEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.grid.tv.ui.component.GoogleSignInBlock
import com.grid.tv.ui.component.SettingsGoogleSignInButton
import com.grid.tv.ui.viewmodel.AuthUiState
import com.grid.tv.ui.viewmodel.AuthViewModel
import com.grid.tv.ui.viewmodel.ManualUpdateUiState
import com.grid.tv.ui.viewmodel.ProfileViewModel
import com.grid.tv.ui.viewmodel.SettingsViewModel
import com.grid.tv.ui.viewmodel.UpdateViewModel
import com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.profileInitials
import com.grid.tv.util.quitAppToHome
import com.grid.tv.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String, val subtitle: String) {
    Profile("Profile", "Who's watching & parental"),
    Connections("Connections", "Playlists & IPTV login"),
    Guide("Guide & EPG", "Refresh & channel mapping"),
    Playback("Playback", "Player, audio & sleep"),
    Interface("Interface", "Layout, sidebar & display"),
    Recordings("Recordings", "Storage & save location"),
    About("About", "Version, backup & info")
}

private enum class ChangePinStep { VERIFY_CURRENT, ENTER_NEW, CONFIRM_NEW }

@Composable
fun SettingsScreen(
    profileInitials: String = "?",
    onNavigateHome: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateVod: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onBack: () -> Unit = {},
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onOpenEpgResolver: () -> Unit = {},
    onRestartToOnboarding: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageOptions by viewModel.storageOptions.collectAsStateWithLifecycle()
    val currentStorageLabel by viewModel.currentStorageLabel.collectAsStateWithLifecycle()
    val usbStorageReady by viewModel.usbStorageReady.collectAsStateWithLifecycle()
    val usbStorageStatusLine by viewModel.usbStorageStatusLine.collectAsStateWithLifecycle()
    val progress by viewModel.m3uProgress.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val connectionDialog by viewModel.connectionDialog.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val xtreamAccounts by viewModel.xtreamAccounts.collectAsStateWithLifecycle()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by profileViewModel.activeProfile.collectAsStateWithLifecycle()
    val scannerRuntime by viewModel.scannerRuntime.collectAsStateWithLifecycle()
    val channelGroups by viewModel.channelGroups.collectAsStateWithLifecycle()
    val groupChannelCounts by viewModel.groupChannelCounts.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val signedInAccount by authViewModel.signedInAccount.collectAsStateWithLifecycle()
    val includeUntaggedVodContent by viewModel.includeUntaggedVodContent.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetSettingsConfirm by remember { mutableStateOf(false) }
    var pendingDeletePlaylistId by remember { mutableStateOf<Long?>(null) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showHideAdultPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showManageProfilesOverlay by remember { mutableStateOf(false) }
    var changePinStep by remember { mutableStateOf(ChangePinStep.VERIFY_CURRENT) }
    var pendingNewPin by remember { mutableStateOf("") }
    var pendingHideAdultValue by remember { mutableStateOf<Boolean?>(null) }
    val cacheMessage by viewModel.cacheMessage.collectAsStateWithLifecycle()
    val playbackHealthSummary by viewModel.playbackHealthSummary.collectAsStateWithLifecycle()
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val sections = SettingsSection.entries
    val navItems = sections.map { SettingsNavItem(it.title, it.subtitle) }

    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var focusPanel by rememberSaveable { mutableStateOf(SettingsFocusPanel.LEFT) }
    var sidebarFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastActiveSidebarIndex by rememberSaveable { mutableIntStateOf(0) }
    var topBarFocusIndex by remember { mutableIntStateOf(3) }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var refreshHours by remember { mutableStateOf("24") }
    var playlistType by remember { mutableStateOf(PlaylistType.M3U) }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }
    var editingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showConnectionForm by remember { mutableStateOf(false) }
    var showGuideGroupPicker by remember { mutableStateOf(false) }

    fun dismissConnectionForm() {
        showConnectionForm = false
        editingPlaylistId = null
        name = ""
        url = ""
        epgUrl = ""
        refreshHours = "24"
        playlistType = PlaylistType.M3U
        xtreamServer = ""
        xtreamUser = ""
        xtreamPass = ""
    }

    val sidebarItemFocusRequesters = remember(sections.size) {
        List(sections.size) { FocusRequester() }
    }
    val sectionEntryFocusRequesters = remember(sections.size) {
        List(sections.size) { FocusRequester() }
    }
    val topNavFocusRequester = remember { FocusRequester() }
    val contentFocusScope = rememberCoroutineScope()

    LaunchedEffect(focusPanel, sidebarFocusIndex, showGuideGroupPicker) {
        if (showGuideGroupPicker) return@LaunchedEffect
        when (focusPanel) {
            SettingsFocusPanel.TOP_BAR -> topNavFocusRequester.requestFocusSafelyAfterLayout()
            SettingsFocusPanel.LEFT ->
                sidebarItemFocusRequesters.getOrNull(sidebarFocusIndex)?.requestFocusSafelyAfterLayout()
            SettingsFocusPanel.RIGHT -> Unit
        }
    }

    LaunchedEffect(editingPlaylistId) {
        val id = editingPlaylistId ?: return@LaunchedEffect
        val playlist = playlists.find { it.id == id } ?: return@LaunchedEffect
        val form = viewModel.connectionFormFor(playlist)
        name = form.name
        playlistType = form.playlistType
        url = form.m3uUrl
        epgUrl = form.epgUrl
        refreshHours = form.refreshHours
        xtreamServer = form.xtreamServer
        xtreamUser = form.xtreamUser
        xtreamPass = form.xtreamPassword
    }

    fun selectSidebarSection(index: Int) {
        val clamped = index.coerceIn(0, sections.lastIndex)
        if (sections[selectedSection] == SettingsSection.Connections &&
            sections[clamped] != SettingsSection.Connections
        ) {
            dismissConnectionForm()
        }
        sidebarFocusIndex = clamped
        selectedSection = clamped
    }

    fun enterContentFromSidebar() {
        val clamped = sidebarFocusIndex.coerceIn(0, sections.lastIndex)
        if (sections[selectedSection] == SettingsSection.Connections &&
            sections[clamped] != SettingsSection.Connections
        ) {
            dismissConnectionForm()
        }
        lastActiveSidebarIndex = clamped
        selectedSection = clamped
        sidebarFocusIndex = clamped
        focusPanel = SettingsFocusPanel.RIGHT
        contentFocusScope.launch {
            withFrameMillis { }
            sectionEntryFocusRequesters.getOrNull(selectedSection)?.requestFocusSafelyAfterLayout()
        }
    }

    fun handleBackKey(): Boolean {
        when {
            showConnectionForm && sections[selectedSection] == SettingsSection.Connections -> {
                dismissConnectionForm()
            }
            connectionDialog != null -> viewModel.dismissConnectionDialog()
            showHideAdultPinDialog -> {
                showHideAdultPinDialog = false
                pendingHideAdultValue = null
            }
            showResetConfirm -> showResetConfirm = false
            showResetSettingsConfirm -> showResetSettingsConfirm = false
            pendingDeletePlaylistId != null -> pendingDeletePlaylistId = null
            showSignOutConfirm -> showSignOutConfirm = false
            showClearCacheConfirm -> showClearCacheConfirm = false
            showGuideGroupPicker -> showGuideGroupPicker = false
            showManageProfilesOverlay -> showManageProfilesOverlay = false
            showChangePinDialog -> showChangePinDialog = false
            profileMenuOpen -> profileMenuOpen = false
            focusPanel == SettingsFocusPanel.RIGHT -> {
                sidebarFocusIndex = lastActiveSidebarIndex
                focusPanel = SettingsFocusPanel.LEFT
            }
            else -> return false
        }
        return true
    }

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::handleBackKey
    )

    fun activateNavTab(tab: EpgNavTab) {
        when (tab) {
            EpgNavTab.Guide, EpgNavTab.Home -> onNavigateHome()
            EpgNavTab.Vod -> onNavigateVod(0)
            EpgNavTab.Movies -> onNavigateVod(0)
            EpgNavTab.Series -> onNavigateVod(1)
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> onOpenFavorites()
            EpgNavTab.Search -> onNavigateHome()
            EpgNavTab.Settings -> Unit
        }
    }

    fun handleTopBarKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (event.type != KeyEventType.KeyDown) return false
        if (profileMenuOpen) {
            return when (event.key) {
                Key.Back, Key.Escape -> {
                    profileMenuOpen = false
                    true
                }
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                    profileMenuOpen = false
                    false
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
                focusPanel = SettingsFocusPanel.LEFT
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
        if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionUp -> {
                if (sidebarFocusIndex > 0) {
                    selectSidebarSection(sidebarFocusIndex - 1)
                } else {
                    focusPanel = SettingsFocusPanel.TOP_BAR
                }
                true
            }
            Key.DirectionDown -> {
                if (sidebarFocusIndex < sections.lastIndex) {
                    selectSidebarSection(sidebarFocusIndex + 1)
                }
                true
            }
            Key.DirectionLeft -> {
                onBack()
                true
            }
            Key.DirectionRight -> {
                enterContentFromSidebar()
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                enterContentFromSidebar()
                true
            }
            Key.Back, Key.Escape -> handleBackKey()
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showManageProfilesOverlay) {
                        Modifier.focusProperties { canFocus = false }
                    } else {
                        Modifier
                    }
                )
        ) {
            EpgTopBar(
                selectedTab = EpgNavTab.Settings,
                focusedNavTabIndex = topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                navFocused = focusPanel == SettingsFocusPanel.TOP_BAR &&
                    topBarFocusIndex <= GridNavTabs.lastIndex,
                profileFocused = focusPanel == SettingsFocusPanel.TOP_BAR &&
                    topBarFocusIndex == TopBarProfileIndex,
                profileInitials = activeProfile?.let { profileInitials(it.name) } ?: profileInitials,
                profileAvatarColor = activeProfile?.avatarColor ?: DEFAULT_PROFILE_AVATAR_COLOR,
                profileMenuExpanded = profileMenuOpen,
                profileMenuFocusIndex = profileMenuFocusIndex,
                onProfileClick = {
                    profileMenuOpen = true
                    profileMenuFocusIndex = 0
                },
                onSwitchAccounts = {
                    profileMenuOpen = false
                    onSwitchProfile()
                },
                onOpenSettings = { profileMenuOpen = false },
                onQuitApp = { context.quitAppToHome() },
                onProfileMenuDismiss = { profileMenuOpen = false },
                onTabSelected = { tab ->
                    topBarFocusIndex = GridNavTabs.indexOf(tab)
                    activateNavTab(tab)
                },
                miniPlayer = {},
                modifier = Modifier
                    .focusRequester(topNavFocusRequester)
                    .onFocusChanged { if (it.hasFocus) focusPanel = SettingsFocusPanel.TOP_BAR }
                    .focusable()
                    .onPreviewKeyEvent {
                        if (focusPanel == SettingsFocusPanel.TOP_BAR) handleTopBarKey(it) else false
                    }
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EpgColors.Background)
            ) {
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .clip(RectangleShape)
                        .background(EpgColors.ChannelColumnBg)
                        .onFocusChanged { if (it.hasFocus) focusPanel = SettingsFocusPanel.LEFT }
                        .onPreviewKeyEvent {
                            if (focusPanel == SettingsFocusPanel.LEFT) {
                                handleSidebarKey(it)
                            } else {
                                false
                            }
                        }
                ) {
                    SettingsSidebar(
                        items = navItems,
                        selectedIndex = selectedSection,
                        focusedIndex = sidebarFocusIndex,
                        sidebarFocused = focusPanel == SettingsFocusPanel.LEFT,
                        itemFocusRequesters = sidebarItemFocusRequesters,
                        sectionEntryFocusRequesters = sectionEntryFocusRequesters,
                        onItemFocused = { index ->
                            sidebarFocusIndex = index
                            lastActiveSidebarIndex = index
                        },
                        onSectionSelected = { index ->
                            selectSidebarSection(index)
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(EpgColors.GridBg)
                        .clip(RectangleShape)
                        .onFocusChanged { if (it.hasFocus) focusPanel = SettingsFocusPanel.RIGHT }
                        .focusProperties {
                            left = sidebarItemFocusRequesters.getOrElse(lastActiveSidebarIndex) {
                                sidebarItemFocusRequesters.firstOrNull() ?: FocusRequester.Default
                            }
                            up = topNavFocusRequester
                        }
                        .focusGroup()
                ) {
                    TvScrollContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(EpgColors.GridBg)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        key(selectedSection) {
                        when (sections[selectedSection]) {
                        SettingsSection.Profile -> ProfileSettingsContent(
                            activeProfile = activeProfile,
                            profiles = profiles,
                            settings = settings,
                            includeUntaggedVodContent = includeUntaggedVodContent,
                            sectionEntryFocusRequester = sectionEntryFocusRequesters[SettingsSection.Profile.ordinal],
                            onManageProfiles = {
                                Log.d("SettingsScreen", "Manage profiles clicked — opening inline editor")
                                showManageProfilesOverlay = true
                            },
                            onSelectProfile = { profileViewModel.switchProfile(it) },
                            onToggleIncludeUntaggedVodContent = {
                                viewModel.updateIncludeUntaggedVodContent(!includeUntaggedVodContent)
                            },
                            onToggleHideAdult = {
                                val next = !settings.hideAdultContent
                                if (next && activeProfile?.hasPin == true) {
                                    pendingHideAdultValue = true
                                    showHideAdultPinDialog = true
                                } else {
                                    viewModel.updateHideAdultContent(next)
                                }
                            },
                            onToggleParentalPinLock = {
                                viewModel.updateParentalPinLock(!settings.parentalPinLockEnabled)
                            },
                            onMaxContentRating = { viewModel.updateMaxContentRating(it) },
                            onChangePin = {
                                changePinStep = if (activeProfile?.hasPin == true) {
                                    ChangePinStep.VERIFY_CURRENT
                                } else {
                                    ChangePinStep.ENTER_NEW
                                }
                                pendingNewPin = ""
                                showChangePinDialog = true
                            },
                            onAvatarColorChange = { color ->
                                activeProfile?.id?.let { profileViewModel.updateAvatarColor(it, color) }
                            }
                        )
                        SettingsSection.Connections -> ConnectionsSettingsContent(
                            sectionEntryFocusRequester = sectionEntryFocusRequesters[SettingsSection.Connections.ordinal],
                            settings = settings,
                            showConnectionForm = showConnectionForm,
                            editingPlaylistId = editingPlaylistId,
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
                            onConnectionTimeout = { viewModel.updateConnectionTimeout(it) },
                            onToggleUseProxy = { viewModel.updateUseProxy(!settings.useProxy) },
                            onProxyUrlChange = { viewModel.updateProxyUrl(it) },
                            progress = progress,
                            isConnecting = isConnecting,
                            playlists = playlists,
                            xtreamAccounts = xtreamAccounts,
                            onSelectPlaylist = {
                                editingPlaylistId = it
                                showConnectionForm = true
                            },
                            onStartNewConnection = {
                                editingPlaylistId = null
                                showConnectionForm = true
                                name = ""
                                url = ""
                                epgUrl = ""
                                refreshHours = "24"
                                playlistType = PlaylistType.M3U
                                xtreamServer = ""
                                xtreamUser = ""
                                xtreamPass = ""
                            },
                            onCancelConnectionForm = { dismissConnectionForm() },
                            onSaveConnection = {
                                viewModel.saveConnection(
                                    editingPlaylistId = editingPlaylistId,
                                    name = name,
                                    url = url,
                                    playlistType = playlistType,
                                    xtreamServer = xtreamServer,
                                    xtreamUser = xtreamUser,
                                    xtreamPass = xtreamPass,
                                    epgUrl = epgUrl.ifBlank { null },
                                    refreshHours = refreshHours.toIntOrNull() ?: 24
                                )
                            },
                            onPickLocalFile = onPickLocalFile,
                            onPickTiviMateZip = onPickTiviMateZip,
                            onDeletePlaylist = { id -> pendingDeletePlaylistId = id }
                        )
                        SettingsSection.Guide -> GuideSettingsContent(
                            settings = settings,
                            channelGroups = channelGroups,
                            scannerRuntime = scannerRuntime,
                            now = now,
                            sectionEntryFocusRequester = sectionEntryFocusRequesters[SettingsSection.Guide.ordinal],
                            onRefreshEpg = { viewModel.refreshEpg() },
                            onOpenEpgResolver = onOpenEpgResolver,
                            onEditChannelGroups = { showGuideGroupPicker = true },
                            onRowHeight = { viewModel.updateRowHeight(it) },
                            onToggleAutoScan = { viewModel.updateAutoScanEnabled(it) },
                            onScanInterval = { viewModel.updateScanIntervalMinutes(it) },
                            onConcurrentChecks = { viewModel.updateConcurrentChecks(it) },
                            onToggleScanOnMetered = { viewModel.updateScanOnMetered(it) },
                            onScanNow = { viewModel.scanChannelsNow() },
                            onToggleShowEpgSidebar = {
                                viewModel.updateShowEpgProgramInfoOnSidebar(!settings.showEpgProgramInfoOnSidebar)
                            },
                            onToggleCatchupFromBeginning = {
                                viewModel.updateStartChannelFromBeginningOnCatchup(!settings.startChannelFromBeginningOnCatchup)
                            }
                        )
                        SettingsSection.Playback -> PlaybackSettingsContent(
                            settings = settings,
                            sectionEntryFocusRequester = sectionEntryFocusRequesters[SettingsSection.Playback.ordinal],
                            externalPlayer = viewModel.externalPlayer,
                            nextUpAutoPlay = viewModel.nextUpAutoPlay,
                            vodSyncIntervalHours = viewModel.vodSyncIntervalHours,
                            onExternalPlayerChange = viewModel::setExternalPlayer,
                            onNextUpAutoPlayChange = viewModel::setNextUpAutoPlay,
                            onSyncIntervalChange = viewModel::setVodSyncIntervalHours,
                            onRetries = { viewModel.updateRetries(it) },
                            onDefaultQuality = { viewModel.updateDefaultStreamQuality(it) },
                            onBufferSize = { viewModel.updateBufferSize(it) },
                            onToggleAutoReconnect = {
                                viewModel.updateAutoReconnectOnDrop(!settings.autoReconnectOnDrop)
                            },
                            onToggleHardwareDecoding = {
                                viewModel.updatePreferHardwareDecoding(!settings.preferHardwareDecoding)
                            },
                            onAspectRatio = { viewModel.updateAspectRatio(it) },
                            onToggleSubtitles = { viewModel.updateSubtitlesEnabled(!settings.subtitlesEnabled) },
                            onSubtitleLanguage = { viewModel.updateSubtitleLanguage(it) },
                            onSubtitleFontSize = { viewModel.updateSubtitleFontSize(it) },
                            onSubtitlePosition = { viewModel.updateSubtitlePosition(it) },
                            onSubtitleDelayMs = { viewModel.updateSubtitleDelayMs(it) },
                            onToggleDeinterlacing = {
                                viewModel.updateDeinterlacingEnabled(!settings.deinterlacingEnabled)
                            },
                            onAudioLanguage = { viewModel.updateAudioLanguage(it) },
                            onSleepTimer = { viewModel.updateSleepTimerMinutes(it) },
                            onToggleSleepTimerAuto = { viewModel.updateSleepTimerAutoEnabled(!settings.sleepTimerAutoEnabled) }
                        )
                        SettingsSection.Interface -> InterfaceSettingsContent(
                            settings = settings,
                            sectionEntryFocusRequester = sectionEntryFocusRequesters[SettingsSection.Interface.ordinal],
                            onSidebarAutoHide = { viewModel.updateSidebarAutoHideSeconds(it) },
                            onToggleShowChannelNumbers = {
                                viewModel.updateShowChannelNumbers(!settings.showChannelNumbers)
                            },
                            onDpadSensitivity = { viewModel.updateDpadSidebarSensitivity(it) },
                            onClockDisplay = { viewModel.updateClockDisplay(it) },
                            onTheme = { viewModel.updateTheme(it) }
                        )
                        SettingsSection.Recordings -> RecordingsSettingsContent(
                            usbStorageReady = usbStorageReady,
                            usbStorageStatusLine = usbStorageStatusLine,
                            currentStorageLabel = currentStorageLabel,
                            storageOptions = storageOptions,
                            sectionEntryFocusRequester = sectionEntryFocusRequesters[SettingsSection.Recordings.ordinal],
                            onSelectStorage = { viewModel.setRecordingStorage(it) },
                            onRefreshStorage = { viewModel.refreshStorageSettings() }
                        )
                        SettingsSection.About -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val supabaseClient = EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                SupabaseEntryPoint::class.java
                            ).supabaseClientProvider().clientOrNull()
                            AboutSettingsContent(
                                appVersion = viewModel.appVersion,
                                updateUiState = updateUiState,
                                onCheckForUpdates = updateViewModel::checkForUpdate,
                                onDownloadUpdate = updateViewModel::downloadAndInstall,
                                lowEndDeviceModeActive = LowEndDeviceMode.isActive(context),
                                lowEndDeviceSummary = LowEndDeviceMode.profile(context).let { profile ->
                                    if (!profile.active) null
                                    else "RAM ${profile.totalRamMb} MB · max ${profile.maxPaneCount} panes · " +
                                        "buffer cap ${profile.maxBufferMsCap / 1000}s"
                                },
                                importSummary = importSummary,
                                cacheMessage = cacheMessage,
                                playbackHealthSummary = playbackHealthSummary,
                                sectionEntryFocusRequester = sectionEntryFocusRequesters[SettingsSection.About.ordinal],
                                isSignedIn = authUiState is AuthUiState.Authenticated,
                                signedInEmail = signedInAccount?.email ?: signedInAccount?.displayName,
                                supabaseClient = supabaseClient,
                                authViewModel = authViewModel,
                                onSignOut = { showSignOutConfirm = true },
                                onExportBackup = { viewModel.exportBackup(context.cacheDir) },
                                onClearCache = { showClearCacheConfirm = true },
                                onResetSettings = { showResetSettingsConfirm = true },
                                onResetApp = { showResetConfirm = true },
                                onRefreshPlaybackHealth = { viewModel.refreshPlaybackHealthSummary() },
                                onLogPlaybackHealth = { viewModel.logPlaybackHealthDiagnostics() }
                            )
                        }
                        }
                    }
                }
            }
        }
        }

        connectionDialog?.let { dialogState ->
            ConnectionResultDialog(
                state = dialogState,
                onDismiss = { viewModel.dismissConnectionDialog() },
                onGoToGuide = {
                    viewModel.dismissConnectionDialog()
                    onNavigateHome()
                }
            )
        }

        if (isConnecting &&
            showConnectionForm &&
            sections.getOrNull(selectedSection) == SettingsSection.Connections
        ) {
            ConnectionLoadingOverlay(
                message = if (editingPlaylistId != null) {
                    "Saving connection…"
                } else {
                    "Connecting to your provider…"
                }
            )
        }

        if (showGuideGroupPicker && channelGroups.isNotEmpty()) {
            GuideGroupPickerDialog(
                channelGroups = channelGroups,
                initialSelection = settings.guideChannelGroups,
                groupChannelCounts = groupChannelCounts,
                title = "Edit channel groups",
                subtitle = "Choose which provider groups appear in your live guide.",
                onDismiss = { showGuideGroupPicker = false },
                onConfirm = { groups ->
                    showGuideGroupPicker = false
                    viewModel.updateGuideChannelGroups(groups)
                }
            )
        }

        if (showResetConfirm) {
            FactoryResetConfirmDialog(
                onDismiss = { showResetConfirm = false },
                onConfirm = {
                    showResetConfirm = false
                    viewModel.resetAllData(onRestartToOnboarding)
                }
            )
        }

        if (showResetSettingsConfirm) {
            FactoryResetConfirmDialog(
                title = "Reset all settings?",
                message = "Restore default settings for playback, guide, interface, and parental controls. Profiles and connections are kept.",
                confirmLabel = "Reset settings",
                onDismiss = { showResetSettingsConfirm = false },
                onConfirm = {
                    showResetSettingsConfirm = false
                    viewModel.resetSettingsToDefaults()
                }
            )
        }

        pendingDeletePlaylistId?.let { playlistId ->
            val playlistName = playlists.find { it.id == playlistId }?.name ?: "this connection"
            DestructiveConfirmDialog(
                title = "Remove connection?",
                message = "Remove \"$playlistName\" and all of its channels from this device? This cannot be undone.",
                confirmLabel = "Remove",
                onDismiss = { pendingDeletePlaylistId = null },
                onConfirm = {
                    viewModel.deletePlaylist(playlistId)
                    if (editingPlaylistId == playlistId) {
                        dismissConnectionForm()
                    }
                    pendingDeletePlaylistId = null
                }
            )
        }

        if (showSignOutConfirm) {
            DestructiveConfirmDialog(
                title = "Sign out?",
                message = "You will be signed out of your GRID account on this device. Local profiles and connections are kept.",
                confirmLabel = "Sign out",
                onDismiss = { showSignOutConfirm = false },
                onConfirm = {
                    showSignOutConfirm = false
                    authViewModel.signOut(onComplete = onSignOut)
                }
            )
        }

        if (showClearCacheConfirm) {
            DestructiveConfirmDialog(
                title = "Clear cache?",
                message = "Clear temporary EPG and catalog cache. Your connections, profiles, and settings are not affected.",
                confirmLabel = "Clear cache",
                onDismiss = { showClearCacheConfirm = false },
                onConfirm = {
                    showClearCacheConfirm = false
                    viewModel.clearCache()
                }
            )
        }

        if (showManageProfilesOverlay) {
            ManageProfilesOverlay(
                profiles = profiles,
                activeProfileId = activeProfile?.id,
                onDismiss = { showManageProfilesOverlay = false },
                onCreateProfile = { name ->
                    val colorIndex = profiles.size % ProfileAvatarColors.size
                    val color = ProfileAvatarColors[colorIndex]
                    val hex = String.format(
                        "#%02X%02X%02X",
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    )
                    profileViewModel.createProfile(name, hex, pin = null, parental = false)
                },
                onRenameProfile = { profileId, name ->
                    profileViewModel.updateProfileName(profileId, name)
                },
                onDeleteProfile = { profileId ->
                    profileViewModel.deleteProfile(profileId)
                }
            )
        }

        if (showChangePinDialog) {
            val profile = activeProfile
            if (profile != null) {
                key(changePinStep) {
                val pinTitle = when (changePinStep) {
                    ChangePinStep.VERIFY_CURRENT -> "Enter current PIN"
                    ChangePinStep.ENTER_NEW -> "Enter new PIN"
                    ChangePinStep.CONFIRM_NEW -> "Confirm new PIN"
                }
                PinEntryDialog(
                    profileName = profile.name,
                    title = pinTitle,
                    subtitle = when (changePinStep) {
                        ChangePinStep.VERIFY_CURRENT -> "Verify before changing PIN"
                        ChangePinStep.ENTER_NEW -> "Choose a 4-digit PIN"
                        ChangePinStep.CONFIRM_NEW -> "Enter the same PIN again"
                    },
                    onVerified = {
                        when (changePinStep) {
                            ChangePinStep.VERIFY_CURRENT -> changePinStep = ChangePinStep.ENTER_NEW
                            ChangePinStep.ENTER_NEW -> changePinStep = ChangePinStep.CONFIRM_NEW
                            ChangePinStep.CONFIRM_NEW -> {
                                showChangePinDialog = false
                                changePinStep = ChangePinStep.VERIFY_CURRENT
                                pendingNewPin = ""
                            }
                        }
                    },
                    onDismiss = {
                        showChangePinDialog = false
                        changePinStep = ChangePinStep.VERIFY_CURRENT
                        pendingNewPin = ""
                    },
                    verifyPin = { pin ->
                        when (changePinStep) {
                            ChangePinStep.VERIFY_CURRENT -> profileViewModel.verifyPin(profile.id, pin)
                            ChangePinStep.ENTER_NEW -> {
                                pendingNewPin = pin
                                true
                            }
                            ChangePinStep.CONFIRM_NEW -> {
                                if (pin == pendingNewPin) {
                                    profileViewModel.updateProfilePin(profile.id, pin)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    }
                )
                }
            }
        }

        if (showHideAdultPinDialog) {
            val profile = activeProfile
            if (profile != null) {
                PinEntryDialog(
                    profileName = profile.name,
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
                    verifyPin = { pin -> profileViewModel.verifyPin(profile.id, pin) }
                )
            }
        }
    }
}

@Composable
private fun ProfileSettingsContent(
    activeProfile: UserProfile?,
    profiles: List<UserProfile>,
    settings: com.grid.tv.domain.model.AppSettings,
    includeUntaggedVodContent: Boolean,
    sectionEntryFocusRequester: FocusRequester? = null,
    onManageProfiles: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onToggleIncludeUntaggedVodContent: () -> Unit,
    onToggleHideAdult: () -> Unit,
    onToggleParentalPinLock: () -> Unit,
    onMaxContentRating: (MaxContentRating) -> Unit,
    onChangePin: () -> Unit,
    onAvatarColorChange: (String) -> Unit
) {
    SettingsPanel(
        title = "Active profile",
        description = "Profiles keep separate favorites, watch history, and parental rules."
    ) {
        val parental = if (activeProfile?.isParental == true) {
            "Parental controls on"
        } else {
            "Standard profile"
        }
        SettingsActiveProfileRow(
            initials = activeProfile?.name?.take(2)?.uppercase() ?: "?",
            avatarColorHex = activeProfile?.avatarColor ?: "#3B8FFF",
            title = activeProfile?.name ?: "No profile",
            subtitle = parental,
            onClick = onManageProfiles,
            focusRequester = sectionEntryFocusRequester
        )
    }

    if (activeProfile != null) {
        SettingsPanel(
            title = "Profile picture color",
            description = "Choose a color for your profile avatar."
        ) {
            ProfileColorPicker(
                colors = ProfileAvatarColors,
                selectedHex = activeProfile.avatarColor,
                onColorSelected = onAvatarColorChange
            )
        }
    }

    SettingsPanel(
        title = "All profiles",
        description = "Select a profile for this device."
    ) {
        profiles.forEach { profile ->
            val isActive = profile.id == activeProfile?.id
            SettingsFocusProfileRow(
                title = profile.name,
                subtitle = buildString {
                    if (profile.isParental) append("Parental · ")
                    if (profile.hasPin) append("PIN protected · ")
                    append(if (isActive) "Current device profile" else "Press OK to switch")
                },
                isActive = isActive,
                onSelect = { onSelectProfile(profile.id) },
                modifier = Modifier.padding(vertical = 4.dp)
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
        title = "Content preferences",
        description = "Control how movies and series are filtered by language."
    ) {
        SettingsFocusToggleRow(
            label = "Include Untagged Content",
            description = "When enabled, titles without a detectable language tag will still appear in filtered results.",
            enabled = includeUntaggedVodContent,
            onToggle = onToggleIncludeUntaggedVodContent
        )
    }

    SettingsPanel(
        title = "Parental controls",
        description = "Filter adult channel groups and restrict access with a PIN."
    ) {
        SettingsFocusToggleRow(
            label = "Hide Adult Categories",
            description = if (activeProfile?.hasPin == true) {
                "PIN required to turn on"
            } else {
                "Blocks channels in adult groups (18+, XXX, etc.)"
            },
            enabled = settings.hideAdultContent,
            onToggle = onToggleHideAdult
        )
        SettingsFocusToggleRow(
            label = "Parental PIN lock",
            description = "Require PIN to switch profiles or open restricted content",
            enabled = settings.parentalPinLockEnabled,
            onToggle = onToggleParentalPinLock,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "Max content rating",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        val ratingLabels = listOf("All ages", "PG", "14+", "18+")
        SettingsFocusPillGroup(
            labels = ratingLabels,
            selectedIndex = MaxContentRating.entries.indexOf(settings.maxContentRating).coerceAtLeast(0),
            onSelect = { index -> onMaxContentRating(MaxContentRating.entries[index]) }
        )
        SettingsFocusButton(
            text = if (activeProfile?.hasPin == true) "Change PIN" else "Set PIN",
            onClick = onChangePin,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun ConnectionsSettingsContent(
    sectionEntryFocusRequester: FocusRequester? = null,
    settings: com.grid.tv.domain.model.AppSettings,
    showConnectionForm: Boolean,
    editingPlaylistId: Long?,
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
    onConnectionTimeout: (Int) -> Unit,
    onToggleUseProxy: () -> Unit,
    onProxyUrlChange: (String) -> Unit,
    progress: String,
    isConnecting: Boolean,
    playlists: List<com.grid.tv.domain.model.Playlist>,
    xtreamAccounts: List<com.grid.tv.domain.model.XtreamAccountInfo>,
    onSelectPlaylist: (Long) -> Unit,
    onStartNewConnection: () -> Unit,
    onCancelConnectionForm: () -> Unit,
    onSaveConnection: () -> Unit,
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    if (showConnectionForm) {
        ConnectionFormPanel(
            sectionEntryFocusRequester = sectionEntryFocusRequester,
            settings = settings,
            editingPlaylistId = editingPlaylistId,
            name = name,
            onNameChange = onNameChange,
            url = url,
            onUrlChange = onUrlChange,
            epgUrl = epgUrl,
            onEpgUrlChange = onEpgUrlChange,
            refreshHours = refreshHours,
            onRefreshHoursChange = onRefreshHoursChange,
            playlistType = playlistType,
            onPlaylistTypeChange = onPlaylistTypeChange,
            xtreamServer = xtreamServer,
            onXtreamServerChange = onXtreamServerChange,
            xtreamUser = xtreamUser,
            onXtreamUserChange = onXtreamUserChange,
            xtreamPass = xtreamPass,
            onXtreamPassChange = onXtreamPassChange,
            onConnectionTimeout = onConnectionTimeout,
            onToggleUseProxy = onToggleUseProxy,
            onProxyUrlChange = onProxyUrlChange,
            progress = progress,
            isConnecting = isConnecting,
            onCancelConnectionForm = onCancelConnectionForm,
            onSaveConnection = onSaveConnection,
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip
        )
    } else {
        ConnectionsListPanel(
            sectionEntryFocusRequester = sectionEntryFocusRequester,
            playlists = playlists,
            xtreamAccounts = xtreamAccounts,
            onSelectPlaylist = onSelectPlaylist,
            onStartNewConnection = onStartNewConnection,
            onDeletePlaylist = onDeletePlaylist
        )
    }
}

@Composable
private fun ConnectionsListPanel(
    sectionEntryFocusRequester: FocusRequester? = null,
    playlists: List<com.grid.tv.domain.model.Playlist>,
    xtreamAccounts: List<com.grid.tv.domain.model.XtreamAccountInfo>,
    onSelectPlaylist: (Long) -> Unit,
    onStartNewConnection: () -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    SettingsPanel(
        title = "Your connections",
        description = "Select a provider to edit, or add a new one."
    ) {
        SettingsFocusButton(
            text = "+ Add connection",
            onClick = onStartNewConnection,
            focusRequester = sectionEntryFocusRequester,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (playlists.isEmpty()) {
            Text(
                "No connections yet. Tap + to add your first provider.",
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp
            )
        }
        playlists.forEach { playlist ->
            SettingsConnectionRow(
                title = playlist.name,
                subtitle = playlistConnectionSubtitle(playlist),
                onEdit = { onSelectPlaylist(playlist.id) },
                onRemove = { onDeletePlaylist(playlist.id) }
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
private fun ConnectionFormPanel(
    sectionEntryFocusRequester: FocusRequester? = null,
    settings: com.grid.tv.domain.model.AppSettings,
    editingPlaylistId: Long?,
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
    onConnectionTimeout: (Int) -> Unit,
    onToggleUseProxy: () -> Unit,
    onProxyUrlChange: (String) -> Unit,
    progress: String,
    isConnecting: Boolean,
    onCancelConnectionForm: () -> Unit,
    onSaveConnection: () -> Unit,
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit
) {
    val formTitle = if (editingPlaylistId != null) "Edit connection" else "Add connection"
    val saveLabel = if (editingPlaylistId != null) "Save" else "Connect"
    SettingsPanel(
        title = formTitle,
        description = "Link your IPTV provider using M3U or Xtream Codes."
    ) {
        Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsFocusTextField(
            label = "Connection name",
            value = name,
            onValueChange = onNameChange,
            placeholder = "e.g. Home IPTV",
            focusRequester = sectionEntryFocusRequester,
            imeAction = ImeAction.Next
        )
        SettingsFocusPillGroup(
            labels = listOf("M3U", "Xtream"),
            selectedIndex = if (playlistType == PlaylistType.XTREAM) 1 else 0,
            onSelect = { index ->
                onPlaylistTypeChange(if (index == 0) PlaylistType.M3U else PlaylistType.XTREAM)
            }
        )
        if (playlistType == PlaylistType.M3U) {
            SettingsFocusTextField(
                label = "M3U URL",
                value = url,
                onValueChange = onUrlChange,
                placeholder = "https://provider.com/playlist.m3u"
            )
        } else {
            SettingsFocusTextField(
                label = "Server URL",
                value = xtreamServer,
                onValueChange = onXtreamServerChange,
                placeholder = "http://server:port"
            )
            SettingsFocusTextField(
                label = "Username",
                value = xtreamUser,
                onValueChange = onXtreamUserChange,
                placeholder = "Username"
            )
            SettingsFocusTextField(
                label = "Password",
                value = xtreamPass,
                onValueChange = onXtreamPassChange,
                placeholder = "Password",
                isPassword = true
            )
        }
        SettingsFocusTextField(
            label = "EPG URL (optional)",
            value = epgUrl,
            onValueChange = onEpgUrlChange,
            placeholder = "https://provider.com/epg.xml"
        )
        SettingsFocusTextField(
            label = "Refresh every (hours)",
            value = refreshHours,
            onValueChange = onRefreshHoursChange,
            placeholder = "24"
        )
        Text(
            text = "Connection timeout",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        val timeoutOptions = listOf(60, 120, 300, 600)
        SettingsFocusPillGroup(
            labels = timeoutOptions.map { seconds ->
                when {
                    seconds < 60 -> "${seconds}s"
                    seconds % 60 == 0 -> "${seconds / 60}m"
                    else -> "${seconds}s"
                }
            },
            selectedIndex = timeoutOptions.indexOf(settings.connectionTimeoutSeconds).coerceAtLeast(0),
            onSelect = { index -> onConnectionTimeout(timeoutOptions[index]) }
        )
        SettingsFocusToggleRow(
            label = "Use proxy",
            description = "Route playlist and stream requests through a proxy server",
            enabled = settings.useProxy,
            onToggle = onToggleUseProxy,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (settings.useProxy) {
            SettingsFocusTextField(
                label = "Proxy URL",
                value = settings.proxyUrl,
                onValueChange = onProxyUrlChange,
                placeholder = "http://proxy:8080"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsFocusButton(
                text = saveLabel,
                onClick = onSaveConnection,
                isLoading = isConnecting,
                loadingLabel = if (editingPlaylistId != null) "Saving..." else "Connecting..."
            )
            SettingsFocusButton(
                text = "Local M3U file",
                onClick = onPickLocalFile
            )
            SettingsFocusButton(
                text = "Import TiviMate",
                onClick = onPickTiviMateZip
            )
            SettingsFocusButton(
                text = "Cancel",
                onClick = onCancelConnectionForm
            )
        }
        Text(
            text = "Status: $progress",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp
        )
        }
    }
}

@Composable
private fun GuideSettingsContent(
    settings: com.grid.tv.domain.model.AppSettings,
    channelGroups: List<String>,
    scannerRuntime: ScannerRuntimeState,
    now: Long,
    sectionEntryFocusRequester: FocusRequester? = null,
    onRefreshEpg: () -> Unit,
    onOpenEpgResolver: () -> Unit,
    onEditChannelGroups: () -> Unit,
    onRowHeight: (EpgRowHeight) -> Unit,
    onToggleAutoScan: (Boolean) -> Unit,
    onScanInterval: (Int) -> Unit,
    onConcurrentChecks: (Int) -> Unit,
    onToggleScanOnMetered: (Boolean) -> Unit,
    onScanNow: () -> Unit,
    onToggleShowEpgSidebar: () -> Unit,
    onToggleCatchupFromBeginning: () -> Unit
) {
    SettingsPanel(
        title = "EPG data",
        description = "Keep the guide up to date."
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsFocusButton(
                text = "Refresh EPG now",
                onClick = onRefreshEpg,
                focusRequester = sectionEntryFocusRequester
            )
            SettingsFocusButton(text = "Fix Missing Guide Data", onClick = onOpenEpgResolver)
        }
    }
    SettingsPanel(
        title = "Channel groups",
        description = "Choose which provider groups appear in the live guide."
    ) {
        val summary = when {
            settings.guideChannelGroups.isEmpty() -> "Showing all channel groups"
            settings.guideChannelGroups.size == 1 -> settings.guideChannelGroups.first()
            else -> "${settings.guideChannelGroups.size} groups selected"
        }
        Text(
            text = summary,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SettingsFocusButton(
            text = "Edit channel groups",
            onClick = onEditChannelGroups
        )
    }
    SettingsPanel(
        title = "Guide row height"
    ) {
        val rowLabels = EpgRowHeight.entries.map { height ->
            height.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        SettingsFocusPillGroup(
            labels = rowLabels,
            selectedIndex = EpgRowHeight.entries.indexOf(settings.epgRowHeight).coerceAtLeast(0),
            onSelect = { index -> onRowHeight(EpgRowHeight.entries[index]) }
        )
    }
    SettingsPanel(
        title = "Channel Scanner",
        description = "Background checks stream URLs without playing them."
    ) {
        Text(
            text = "Enable Auto-Scan",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        SettingsFocusPillGroup(
            labels = listOf("On", "Turn off"),
            selectedIndex = if (settings.autoScanEnabled) 0 else 1,
            onSelect = { index -> onToggleAutoScan(index == 0) }
        )
        Text(
            text = "Scan interval (live channels)",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        val scanIntervals = listOf(1, 2, 5, 10, 30)
        SettingsFocusPillGroup(
            labels = scanIntervals.map { "${it}m" },
            selectedIndex = scanIntervals.indexOf(settings.scanIntervalMinutes).coerceAtLeast(0),
            onSelect = { index -> onScanInterval(scanIntervals[index]) }
        )
        Text(
            text = "Concurrent checks",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        val concurrentCounts = listOf(5, 10, 20, 50)
        SettingsFocusPillGroup(
            labels = concurrentCounts.map { it.toString() },
            selectedIndex = concurrentCounts.indexOf(settings.concurrentChecks).coerceAtLeast(0),
            onSelect = { index -> onConcurrentChecks(concurrentCounts[index]) }
        )
        SettingsFocusToggleRow(
            label = "Scan on metered / mobile connections",
            enabled = settings.scanOnMetered,
            onToggle = { onToggleScanOnMetered(!settings.scanOnMetered) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            SettingsFocusButton(
                text = if (scannerRuntime.isScanning) "Scanning…" else "Scan Now",
                onClick = onScanNow,
                enabled = !scannerRuntime.isScanning
            )
        }
        val lastFullScanLabel = scannerRuntime.lastFullScanAt?.let { at ->
            val mins = ((now - at) / 60_000L).coerceAtLeast(0)
            when (mins) {
                0L -> "just now"
                1L -> "1 min ago"
                else -> "$mins mins ago"
            }
        } ?: "never"
        Text(
            text = "${scannerRuntime.liveCount} of ${scannerRuntime.totalCount} channels live · Last full scan: $lastFullScanLabel",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    SettingsPanel(
        title = "Guide display",
        description = "Sidebar programme info and catch-up playback behavior."
    ) {
        SettingsFocusToggleRow(
            label = "Show EPG programme info on sidebar",
            description = "Display title and time for the selected programme",
            enabled = settings.showEpgProgramInfoOnSidebar,
            onToggle = onToggleShowEpgSidebar
        )
        SettingsFocusToggleRow(
            label = "Start channel from beginning when catch-up is available",
            description = "Jump to programme start instead of joining live edge",
            enabled = settings.startChannelFromBeginningOnCatchup,
            onToggle = onToggleCatchupFromBeginning,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PlaybackSettingsContent(
    settings: com.grid.tv.domain.model.AppSettings,
    sectionEntryFocusRequester: FocusRequester? = null,
    externalPlayer: com.grid.tv.player.ExternalPlayerId,
    nextUpAutoPlay: Boolean,
    vodSyncIntervalHours: Int,
    onExternalPlayerChange: (com.grid.tv.player.ExternalPlayerId) -> Unit,
    onNextUpAutoPlayChange: (Boolean) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onRetries: (Int) -> Unit,
    onDefaultQuality: (StreamQuality) -> Unit,
    onBufferSize: (BufferSize) -> Unit,
    onToggleAutoReconnect: () -> Unit,
    onToggleHardwareDecoding: () -> Unit,
    onAspectRatio: (AspectRatioSetting) -> Unit,
    onToggleSubtitles: () -> Unit,
    onSubtitleLanguage: (String) -> Unit,
    onSubtitleFontSize: (SubtitleFontSize) -> Unit,
    onSubtitlePosition: (SubtitlePosition) -> Unit,
    onSubtitleDelayMs: (Long) -> Unit,
    onToggleDeinterlacing: () -> Unit,
    onAudioLanguage: (String) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onToggleSleepTimerAuto: () -> Unit
) {
    SettingsPanel(
        title = "Stream playback"
    ) {
        Text(
            "Stream retries: ${settings.streamRetries}",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 5).forEachIndexed { index, n ->
                SettingsFocusButton(
                    text = "$n",
                    onClick = { onRetries(n) },
                    focusRequester = if (index == 0) sectionEntryFocusRequester else null
                )
            }
        }
        Text(
            text = "Default stream quality",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        SettingsFocusPillGroup(
            labels = listOf("Auto", "1080p", "720p", "480p"),
            selectedIndex = StreamQuality.entries.indexOf(settings.defaultStreamQuality).coerceAtLeast(0),
            onSelect = { index -> onDefaultQuality(StreamQuality.entries[index]) }
        )
        Text(
            text = "Buffer size",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsFocusPillGroup(
            labels = listOf("Low", "Medium", "High"),
            selectedIndex = BufferSize.entries.indexOf(settings.bufferSize).coerceAtLeast(0),
            onSelect = { index -> onBufferSize(BufferSize.entries[index]) }
        )
        Text(
            text = "Low: ~0.5 GB/hr  Medium: ~1.5 GB/hr  High: ~3 GB/hr. Buffer is stored temporarily and cleared on channel change.",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        SettingsFocusToggleRow(
            label = "Auto-reconnect on drop",
            enabled = settings.autoReconnectOnDrop,
            onToggle = onToggleAutoReconnect,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsFocusToggleRow(
            label = "Prefer hardware decoding",
            enabled = settings.preferHardwareDecoding,
            onToggle = onToggleHardwareDecoding,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    SettingsPanel(
        title = "VOD & external playback"
    ) {
        VodPlaybackSettingsSection(
            externalPlayer = externalPlayer,
            nextUpAutoPlay = nextUpAutoPlay,
            vodSyncIntervalHours = vodSyncIntervalHours,
            onExternalPlayerChange = onExternalPlayerChange,
            onNextUpAutoPlayChange = onNextUpAutoPlayChange,
            onSyncIntervalChange = onSyncIntervalChange
        )
    }
    SettingsPanel(
        title = "Video display"
    ) {
        Text(
            text = "Aspect ratio",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        SettingsFocusPillGroup(
            labels = listOf("Auto", "16:9", "4:3", "Stretch"),
            selectedIndex = AspectRatioSetting.entries.indexOf(settings.aspectRatio).coerceAtLeast(0),
            onSelect = { index -> onAspectRatio(AspectRatioSetting.entries[index]) }
        )
    }
    SettingsPanel(
        title = "Subtitles"
    ) {
        SettingsFocusToggleRow(
            label = "Subtitles",
            enabled = settings.subtitlesEnabled,
            onToggle = onToggleSubtitles
        )
        SettingsFocusTextField(
            label = "Subtitle language",
            value = settings.subtitleLanguage,
            onValueChange = onSubtitleLanguage,
            placeholder = "en"
        )
        Text(
            text = "Font size",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsFocusPillGroup(
            labels = listOf("Small", "Medium", "Large"),
            selectedIndex = SubtitleFontSize.entries.indexOf(settings.subtitleFontSize).coerceAtLeast(0),
            onSelect = { index -> onSubtitleFontSize(SubtitleFontSize.entries[index]) }
        )
        Text(
            text = "Position",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsFocusPillGroup(
            labels = listOf("Bottom", "Middle", "Top"),
            selectedIndex = SubtitlePosition.entries.indexOf(settings.subtitlePosition).coerceAtLeast(0),
            onSelect = { index -> onSubtitlePosition(SubtitlePosition.entries[index]) }
        )
        Text(
            text = "Delay: ${settings.subtitleDelayMs}ms",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsFocusButton(
                text = "-500ms",
                onClick = { onSubtitleDelayMs(settings.subtitleDelayMs - 500L) }
            )
            SettingsFocusButton(
                text = "+500ms",
                onClick = { onSubtitleDelayMs(settings.subtitleDelayMs + 500L) }
            )
        }
        SettingsFocusToggleRow(
            label = "Deinterlacing",
            description = "Convert interlaced video to progressive frames",
            enabled = settings.deinterlacingEnabled,
            onToggle = onToggleDeinterlacing,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    SettingsPanel(
        title = "Audio & sleep"
    ) {
        SettingsFocusTextField(
            label = "Preferred audio language",
            value = settings.preferredAudioLanguage,
            onValueChange = onAudioLanguage,
            placeholder = "en"
        )
        SettingsFocusToggleRow(
            label = "Auto sleep timer",
            description = if (settings.sleepTimerAutoEnabled) {
                "Starts a ${settings.sleepTimerMinutes} min timer when you open the player"
            } else {
                "Off — set a timer manually from the player menu"
            },
            enabled = settings.sleepTimerAutoEnabled,
            onToggle = onToggleSleepTimerAuto,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Default: ${settings.sleepTimerMinutes} minutes",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90).forEachIndexed { index, min ->
                SettingsFocusButton(
                    text = "$min",
                    onClick = { onSleepTimer(min) }
                )
            }
        }
    }
}

@Composable
private fun InterfaceSettingsContent(
    settings: com.grid.tv.domain.model.AppSettings,
    sectionEntryFocusRequester: FocusRequester? = null,
    onSidebarAutoHide: (Int) -> Unit,
    onToggleShowChannelNumbers: () -> Unit,
    onDpadSensitivity: (DpadSensitivity) -> Unit,
    onClockDisplay: (ClockDisplay) -> Unit,
    onTheme: (AppThemeId) -> Unit
) {
    SettingsPanel(
        title = "Sidebar",
        description = "Guide sidebar behavior."
    ) {
        Text(
            text = "Sidebar auto-hide timeout",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        val hideOptions = listOf(3, 5, 10, -1)
        val hideLabels = listOf("3s", "5s", "10s", "Never")
        SettingsFocusPillGroup(
            labels = hideLabels,
            selectedIndex = hideOptions.indexOf(settings.sidebarAutoHideSeconds).coerceAtLeast(0),
            onSelect = { index -> onSidebarAutoHide(hideOptions[index]) },
            entryFocusRequester = sectionEntryFocusRequester
        )
    }
    SettingsPanel(
        title = "Guide navigation"
    ) {
        SettingsFocusToggleRow(
            label = "Show channel numbers",
            enabled = settings.showChannelNumbers,
            onToggle = onToggleShowChannelNumbers
        )
    }
    SettingsPanel(
        title = "Remote & clock"
    ) {
        Text(
            text = "D-pad sidebar sensitivity",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        SettingsFocusPillGroup(
            labels = listOf("Instant", "Normal", "Slow"),
            selectedIndex = DpadSensitivity.entries.indexOf(settings.dpadSidebarSensitivity).coerceAtLeast(0),
            onSelect = { index -> onDpadSensitivity(DpadSensitivity.entries[index]) }
        )
        Text(
            text = "Clock display",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        SettingsFocusPillGroup(
            labels = listOf("Off", "12-hour", "24-hour"),
            selectedIndex = ClockDisplay.entries.indexOf(settings.clockDisplay).coerceAtLeast(0),
            onSelect = { index -> onClockDisplay(ClockDisplay.entries[index]) }
        )
    }
    SettingsPanel(
        title = "Theme",
        description = "Accent, focus, and card colors across the app."
    ) {
        SettingsFocusPillGroup(
            labels = AppThemeId.entries.map { it.displayName },
            selectedIndex = AppThemeId.entries.indexOf(settings.themeId).coerceAtLeast(0),
            onSelect = { index -> onTheme(AppThemeId.entries[index]) }
        )
    }
}

@Composable
private fun RecordingsSettingsContent(
    usbStorageReady: Boolean,
    usbStorageStatusLine: String?,
    currentStorageLabel: String?,
    storageOptions: List<com.grid.tv.feature.recording.StorageOption>,
    sectionEntryFocusRequester: FocusRequester? = null,
    onSelectStorage: (String) -> Unit,
    onRefreshStorage: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefreshStorage()
    }

    SettingsPanel(
        title = "Recording storage",
        description = if (usbStorageReady) {
            "Recordings are saved to your USB drive only. Internal storage is never used."
        } else {
            com.grid.tv.feature.recording.StorageUtils.USB_REQUIRED_MESSAGE
        }
    ) {
        if (usbStorageReady) {
            Text(
                text = usbStorageStatusLine ?: (currentStorageLabel ?: "USB Drive"),
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            Text(
                text = com.grid.tv.feature.recording.StorageUtils.USB_REQUIRED_MESSAGE,
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        storageOptions.forEachIndexed { index, option ->
            var useFocused by remember { mutableStateOf(false) }
            SettingsListRow(
                title = option.label,
                subtitle = option.displayLine(),
                isFocused = useFocused,
                modifier = Modifier.padding(vertical = 4.dp),
                trailing = {
                    SettingsFocusButton(
                        text = "Use",
                        onClick = { onSelectStorage(option.id) },
                        enabled = usbStorageReady,
                        focusRequester = if (index == 0) sectionEntryFocusRequester else null,
                        onFocusChanged = { useFocused = it }
                    )
                }
            )
        }
    }
}

private fun playlistConnectionSubtitle(playlist: Playlist): String {
    val endpoint = when (playlist.type) {
        PlaylistType.M3U -> {
            if (playlist.url.startsWith("local://")) {
                "Local file · ${playlist.url.removePrefix("local://")}"
            } else {
                playlist.url
            }
        }
        PlaylistType.XTREAM -> playlist.xtreamServerUrl ?: playlist.url
        PlaylistType.STALKER -> playlist.stalkerPortalUrl ?: playlist.url
    }
    return "${playlist.type} · $endpoint · refresh every ${playlist.refreshIntervalHours}h"
}

@Composable
private fun AboutSettingsContent(
    appVersion: String,
    updateUiState: ManualUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    lowEndDeviceModeActive: Boolean,
    lowEndDeviceSummary: String?,
    importSummary: String?,
    cacheMessage: String?,
    playbackHealthSummary: com.grid.tv.ui.viewmodel.PlaybackHealthSummary,
    sectionEntryFocusRequester: FocusRequester? = null,
    isSignedIn: Boolean,
    signedInEmail: String?,
    supabaseClient: io.github.jan.supabase.SupabaseClient?,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onExportBackup: () -> Unit,
    onClearCache: () -> Unit,
    onResetSettings: () -> Unit,
    onResetApp: () -> Unit,
    onRefreshPlaybackHealth: () -> Unit,
    onLogPlaybackHealth: () -> Unit
) {
    SettingsPanel(
        title = "Account",
        description = if (isSignedIn) {
            "Your GRID account syncs watch progress and settings across devices."
        } else {
            "Sign in to sync watch progress, settings, and profiles across your devices."
        }
    ) {
        if (isSignedIn) {
            signedInEmail?.let { label ->
                Text(
                    text = label,
                    color = EpgColors.TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            SettingsFocusButton(
                text = "Sign out",
                onClick = onSignOut,
                destructive = true,
                focusRequester = sectionEntryFocusRequester
            )
        } else if (supabaseClient != null) {
            SettingsGoogleSignInButton(
                supabaseClient = supabaseClient,
                viewModel = authViewModel,
                focusRequester = sectionEntryFocusRequester
            )
        } else {
            Text(
                text = "Cloud sync is not configured for this build.",
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
        }
    }
    val accountEntryConsumed = isSignedIn || supabaseClient != null
    SettingsPanel(
        title = "GRID"
    ) {
        Text("Version $appVersion", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
        Text("Live TV Guide for Android TV", color = EpgColors.TextDimmed, fontFamily = DmSansFamily, fontSize = 13.sp)
        if (lowEndDeviceModeActive) {
            Text(
                text = "Low-End Device Mode active",
                color = EpgColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            lowEndDeviceSummary?.let {
                Text(
                    text = it,
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        SettingsFocusButton(
            text = "Export .grid backup",
            onClick = onExportBackup,
            focusRequester = if (!accountEntryConsumed) sectionEntryFocusRequester else null,
            modifier = Modifier.padding(top = 12.dp)
        )
        SettingsFocusButton(
            text = "Clear cache",
            onClick = onClearCache,
            modifier = Modifier.padding(top = 8.dp)
        )
        cacheMessage?.let {
            Text(it, color = EpgColors.Accent, fontFamily = DmSansFamily, fontSize = 13.sp)
        }
        importSummary?.let {
            Text(it, color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 13.sp)
        }
    }
    SettingsPanel(
        title = "Updates",
        description = "Check GitHub for a newer sideload build. Updates are never downloaded automatically."
    ) {
        Text(
            text = "Current version: ${BuildConfig.VERSION_NAME}",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        val statusText = when (updateUiState) {
            ManualUpdateUiState.Idle -> null
            ManualUpdateUiState.Checking -> "Checking for updates…"
            ManualUpdateUiState.UpToDate -> "You are up to date"
            is ManualUpdateUiState.UpdateAvailable ->
                "Update available: ${updateUiState.info.versionName}"
            is ManualUpdateUiState.Downloading ->
                "Downloading… ${updateUiState.percent}%"
            is ManualUpdateUiState.Error -> updateUiState.message
        }
        statusText?.let {
            Text(
                text = it,
                color = when (updateUiState) {
                    is ManualUpdateUiState.Error -> EpgColors.Accent
                    is ManualUpdateUiState.UpdateAvailable -> EpgColors.Accent
                    else -> EpgColors.TextDimmed
                },
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        SettingsFocusButton(
            text = if (updateUiState is ManualUpdateUiState.Checking) {
                "Checking…"
            } else {
                "Check for updates"
            },
            onClick = onCheckForUpdates,
            enabled = updateUiState !is ManualUpdateUiState.Checking &&
                updateUiState !is ManualUpdateUiState.Downloading,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (updateUiState is ManualUpdateUiState.UpdateAvailable) {
            SettingsFocusButton(
                text = "Update now",
                onClick = onDownloadUpdate,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    SettingsPanel(
        title = "Playback health",
        description = "Aggregated startup, buffering, failover, and error metrics from live playback sessions."
    ) {
        playbackHealthSummary.diagnosticsMessage?.let {
            Text(it, color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 13.sp)
        }
        if (playbackHealthSummary.problemChannels.isNotEmpty()) {
            Text(
                text = "Unstable channels: ${playbackHealthSummary.problemChannels.joinToString()}",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        SettingsFocusButton(
            text = "Refresh health summary",
            onClick = onRefreshPlaybackHealth,
            modifier = Modifier.padding(top = 12.dp)
        )
        SettingsFocusButton(
            text = "Log diagnostics to logcat",
            onClick = onLogPlaybackHealth,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    SettingsPanel(
        title = "Reset settings",
        description = "Restore defaults for playback, guide, interface, and parental controls. Profiles and connections are kept."
    ) {
        SettingsFocusButton(
            text = "Reset all settings",
            onClick = onResetSettings,
            destructive = true
        )
    }
    SettingsPanel(
        title = "Reset app",
        description = "Delete all profiles, connections, watch history, favorites, and settings. Restarts as a fresh install."
    ) {
        SettingsFocusButton(
            text = "Reset everything",
            onClick = onResetApp,
            destructive = true
        )
    }
}
