package com.grid.tv.ui.screen

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.model.AspectRatioSetting
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.domain.model.ClockDisplay
import com.grid.tv.domain.model.DpadSensitivity
import com.grid.tv.domain.model.EpgRowHeight
import com.grid.tv.domain.model.MaxContentRating
import com.grid.tv.domain.model.StreamQuality
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.ScannerRuntimeState
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.PlaylistType
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.ProfileAvatarBadge
import com.grid.tv.ui.component.ProfileColorPicker
import com.grid.tv.ui.screen.ProfileAvatarColors
import com.grid.tv.ui.component.PinEntryDialog
import com.grid.tv.ui.component.ConnectionResultDialog
import com.grid.tv.ui.component.FactoryResetConfirmDialog
import com.grid.tv.ui.component.TvScrollContainer
import com.grid.tv.ui.component.SettingsChip
import com.grid.tv.ui.component.SettingsContentFocus
import com.grid.tv.ui.component.SettingsFocusButton
import com.grid.tv.ui.component.SettingsFocusPanel
import com.grid.tv.ui.component.SettingsFocusToggleRow
import com.grid.tv.ui.component.SettingsListRow
import com.grid.tv.ui.component.SettingsNavItem
import com.grid.tv.ui.component.SettingsPanel
import com.grid.tv.ui.component.SettingsSectionKind
import com.grid.tv.ui.component.SettingsSidebar
import com.grid.tv.ui.component.SettingsTextField
import com.grid.tv.ui.component.SettingsFocusTextField
import com.grid.tv.ui.component.SettingsFocusPillGroup
import com.grid.tv.ui.component.connectionsAddFocusCount
import com.grid.tv.ui.component.connectionsListFocusCount
import com.grid.tv.ui.component.aboutFocusCount
import com.grid.tv.ui.component.guideFocusCount
import com.grid.tv.ui.component.interfaceFocusCount
import com.grid.tv.ui.component.playbackFocusCount
import com.grid.tv.ui.component.handleSettingsHorizontalKey
import com.grid.tv.ui.component.buildSettingsSectionCards
import com.grid.tv.ui.component.SettingsSectionCard
import com.grid.tv.ui.component.moveProfileSwatchFocus
import com.grid.tv.ui.component.profileContentFocusCount
import com.grid.tv.ui.component.moveSettingsVerticalFocus
import com.grid.tv.ui.component.rememberSettingsFocusChain
import com.grid.tv.ui.component.settingsVerticalFocusRows
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthViewModel
import com.grid.tv.ui.viewmodel.ProfileViewModel
import com.grid.tv.ui.viewmodel.SettingsViewModel
import com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR
import com.grid.tv.util.profileInitials
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameMillis

private enum class SettingsSection(val title: String, val subtitle: String) {
    Profile("Profile", "Who's watching & parental"),
    Connections("Connections", "Playlists & IPTV login"),
    Guide("Guide & EPG", "Refresh & channel mapping"),
    Playback("Playback", "Player, audio & sleep"),
    Interface("Interface", "Layout, sidebar & display"),
    Recordings("Recordings", "Storage & save location"),
    About("About", "Version, backup & info")
}

private val TopBarProfileIndex get() = GridNavTabs.size
private const val PROFILE_SWATCH_START = 1
private const val PROFILE_SWATCH_COUNT = 8

private fun SettingsSection.toKind(): SettingsSectionKind = when (this) {
    SettingsSection.Profile -> SettingsSectionKind.Profile
    SettingsSection.Connections -> SettingsSectionKind.Connections
    SettingsSection.Guide -> SettingsSectionKind.Guide
    SettingsSection.Playback -> SettingsSectionKind.Playback
    SettingsSection.Interface -> SettingsSectionKind.Interface
    SettingsSection.Recordings -> SettingsSectionKind.Recordings
    SettingsSection.About -> SettingsSectionKind.About
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
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageOptions by viewModel.storageOptions.collectAsStateWithLifecycle()
    val currentStorageLabel by viewModel.currentStorageLabel.collectAsStateWithLifecycle()
    val progress by viewModel.m3uProgress.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val connectionDialog by viewModel.connectionDialog.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val xtreamAccounts by viewModel.xtreamAccounts.collectAsStateWithLifecycle()
    val profiles by profileViewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by profileViewModel.activeProfile.collectAsStateWithLifecycle()
    val scannerRuntime by viewModel.scannerRuntime.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var profileMenuFocusIndex by remember { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetSettingsConfirm by remember { mutableStateOf(false) }
    var showHideAdultPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var changePinStep by remember { mutableStateOf(ChangePinStep.VERIFY_CURRENT) }
    var pendingNewPin by remember { mutableStateOf("") }
    var pendingHideAdultValue by remember { mutableStateOf<Boolean?>(null) }
    val cacheMessage by viewModel.cacheMessage.collectAsStateWithLifecycle()
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

    val connectionsFormStart = if (showConnectionForm) 0 else connectionsListFocusCount(playlists.size)

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

    val contentFocusCount = when (sections[selectedSection]) {
        SettingsSection.Profile -> profileContentFocusCount(
            profileCount = profiles.size,
            activeProfileId = activeProfile?.id,
            hasActiveProfile = activeProfile != null
        )
        SettingsSection.Connections -> if (showConnectionForm) {
            connectionsAddFocusCount(playlistType)
        } else {
            connectionsListFocusCount(playlists.size)
        }
        SettingsSection.Guide -> guideFocusCount()
        SettingsSection.Playback -> playbackFocusCount()
        SettingsSection.Interface -> interfaceFocusCount()
        SettingsSection.Recordings -> storageOptions.size
        SettingsSection.About -> aboutFocusCount()
    }
    val sectionCards = remember(
        selectedSection,
        contentFocusCount,
        playlists.size,
        playlistType,
        showConnectionForm,
        profiles.size,
        activeProfile?.id,
        activeProfile != null,
        storageOptions.size
    ) {
        buildSettingsSectionCards(
            kind = sections[selectedSection].toKind(),
            contentFocusCount = contentFocusCount,
            playlistCount = playlists.size,
            connectionsPlaylistType = playlistType,
            connectionsShowForm = showConnectionForm &&
                sections[selectedSection] == SettingsSection.Connections,
            profileCount = profiles.size,
            activeProfileId = activeProfile?.id,
            hasActiveProfile = activeProfile != null,
            storageOptionCount = storageOptions.size
        )
    }
    val contentChain = rememberSettingsFocusChain(contentFocusCount, selectedSection, 0)
    val contentFocus = SettingsContentFocus(
        chain = contentChain,
        sectionCards = sectionCards
    )
    val profileUseButtonStart = 1 + if (activeProfile != null) PROFILE_SWATCH_COUNT else 0
    val profileUseButtonCount = profiles.count { it.id != activeProfile?.id }
    val profileParentalStart = (contentFocusCount - 7).coerceAtLeast(0)
    val verticalFocusRows = remember(
        selectedSection,
        contentFocusCount,
        playlists.size,
        playlistType,
        showConnectionForm,
        settings.useProxy,
        profiles.size,
        activeProfile?.id,
        activeProfile != null,
        storageOptions.size
    ) {
        settingsVerticalFocusRows(
            kind = sections[selectedSection].toKind(),
            connectionsFormStart = connectionsFormStart,
            connectionsPlaylistType = playlistType,
            connectionsShowForm = showConnectionForm &&
                sections[selectedSection] == SettingsSection.Connections,
            connectionsUseProxy = settings.useProxy,
            playlistCount = playlists.size,
            profileHasSwatches = activeProfile != null &&
                sections[selectedSection] == SettingsSection.Profile,
            profileSwatchStart = PROFILE_SWATCH_START,
            profileUseButtonStart = profileUseButtonStart,
            profileUseButtonCount = if (sections[selectedSection] == SettingsSection.Profile) {
                profileUseButtonCount
            } else {
                0
            },
            parentalStart = profileParentalStart,
            storageOptionCount = storageOptions.size
        )
    }

    val sidebarItemFocusRequesters = remember(sections.size) {
        List(sections.size) { FocusRequester() }
    }
    val topNavFocusRequester = remember { FocusRequester() }
    val contentFocusScope = rememberCoroutineScope()

    // Move focus to the sidebar item / top bar whenever the focused region or sidebar
    // selection changes via explicit navigation. Content focus is requested directly in
    // enterContentFromSidebar(); native directional focus handles movement inside content.
    LaunchedEffect(focusPanel, sidebarFocusIndex) {
        when (focusPanel) {
            SettingsFocusPanel.TOP_BAR -> topNavFocusRequester.requestFocusSafelyAfterLayout()
            SettingsFocusPanel.LEFT ->
                sidebarItemFocusRequesters.getOrNull(sidebarFocusIndex)?.requestFocusSafelyAfterLayout()
            SettingsFocusPanel.RIGHT -> Unit
        }
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
        selectedSection = clamped
        sidebarFocusIndex = clamped
        if (sectionCards.none { it.hasFocusableItems }) return
        focusPanel = SettingsFocusPanel.RIGHT
        contentChain.moveTo(0)
        contentFocusScope.launch {
            withFrameMillis { }
            contentChain.requestFocusAtCurrentIndex()
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
            showChangePinDialog -> showChangePinDialog = false
            profileMenuOpen -> profileMenuOpen = false
            focusPanel == SettingsFocusPanel.RIGHT -> {
                // Back from any control returns to the sidebar category it belongs to.
                sidebarFocusIndex = selectedSection
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
        Column(modifier = Modifier.fillMaxSize()) {
            EpgTopBar(
                now = now,
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
                            if (focusPanel == SettingsFocusPanel.LEFT) handleSidebarKey(it) else false
                        }
                ) {
                    SettingsSidebar(
                        items = navItems,
                        selectedIndex = selectedSection,
                        focusedIndex = sidebarFocusIndex,
                        sidebarFocused = focusPanel == SettingsFocusPanel.LEFT,
                        itemFocusRequesters = sidebarItemFocusRequesters,
                        onItemFocused = { index ->
                            // Keep the highlight in sync with the natively-focused item.
                            // (Section selection is driven by D-pad in handleSidebarKey so
                            // returning from content via Left doesn't switch sections.)
                            sidebarFocusIndex = index
                        },
                        onSectionSelected = { index ->
                            selectSidebarSection(index)
                            focusPanel = SettingsFocusPanel.LEFT
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
                            // Deterministic exits from the content area: Left always returns
                            // to the current section's sidebar entry, Up to the top bar.
                            left = sidebarItemFocusRequesters.getOrElse(selectedSection) {
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
                            focus = contentFocus,
                            onSwitchProfile = onSwitchProfile,
                            onSelectProfile = { profileViewModel.switchProfile(it) },
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
                            focus = contentFocus,
                            settings = settings,
                            showConnectionForm = showConnectionForm,
                            formStartIndex = 0,
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
                            onDeletePlaylist = { id ->
                                viewModel.deletePlaylist(id)
                                if (editingPlaylistId == id) {
                                    dismissConnectionForm()
                                }
                            }
                        )
                        SettingsSection.Guide -> GuideSettingsContent(
                            settings = settings,
                            scannerRuntime = scannerRuntime,
                            now = now,
                            focus = contentFocus,
                            onRefreshEpg = { viewModel.refreshEpg() },
                            onOpenEpgResolver = onOpenEpgResolver,
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
                            focus = contentFocus,
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
                            onToggleDeinterlacing = {
                                viewModel.updateDeinterlacingEnabled(!settings.deinterlacingEnabled)
                            },
                            onAudioLanguage = { viewModel.updateAudioLanguage(it) },
                            onSleepTimer = { viewModel.updateSleepTimerMinutes(it) },
                            onToggleSleepTimerAuto = { viewModel.updateSleepTimerAutoEnabled(!settings.sleepTimerAutoEnabled) }
                        )
                        SettingsSection.Interface -> InterfaceSettingsContent(
                            settings = settings,
                            focus = contentFocus,
                            onSidebarAutoHide = { viewModel.updateSidebarAutoHideSeconds(it) },
                            onToggleShowChannelNumbers = {
                                viewModel.updateShowChannelNumbers(!settings.showChannelNumbers)
                            },
                            onDpadSensitivity = { viewModel.updateDpadSidebarSensitivity(it) },
                            onClockDisplay = { viewModel.updateClockDisplay(it) },
                            onTheme = { viewModel.updateTheme(it) },
                            onTogglePictureInPicture = {
                                viewModel.updatePictureInPictureEnabled(!settings.pictureInPictureEnabled)
                            }
                        )
                        SettingsSection.Recordings -> RecordingsSettingsContent(
                            currentStorageLabel = currentStorageLabel,
                            storageOptions = storageOptions,
                            focus = contentFocus,
                            onSelectStorage = { viewModel.setRecordingStorage(it) }
                        )
                        SettingsSection.About -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            AboutSettingsContent(
                                importSummary = importSummary,
                                cacheMessage = cacheMessage,
                                focus = contentFocus,
                                onSignOut = { authViewModel.signOut(onComplete = onSignOut) },
                                onExportBackup = { viewModel.exportBackup(context.cacheDir) },
                                onClearCache = { viewModel.clearCache() },
                                onResetSettings = { showResetSettingsConfirm = true },
                                onResetApp = { showResetConfirm = true }
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
    focus: SettingsContentFocus,
    onSwitchProfile: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onToggleHideAdult: () -> Unit,
    onToggleParentalPinLock: () -> Unit,
    onMaxContentRating: (MaxContentRating) -> Unit,
    onChangePin: () -> Unit,
    onAvatarColorChange: (String) -> Unit
) {
    val swatchCount = if (activeProfile != null) PROFILE_SWATCH_COUNT else 0
    val parentalStart = focus.chain.lastIndex - 6
    var useButtonIndex = 1 + swatchCount
    var cardIdx = 0
    SettingsPanel(
        title = "Active profile",
        description = "Profiles keep separate favorites, watch history, and parental rules.",
        cardIndex = cardIdx++,
        focus = focus
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
            SettingsFocusButton(
                text = "Manage profiles",
                onClick = onSwitchProfile,
                chainIndex = 0,
                focus = focus
            )
        }
    }

    if (activeProfile != null) {
        SettingsPanel(
            title = "Profile picture color",
            description = "Choose a color for your profile avatar.",
            cardIndex = cardIdx++,
            focus = focus
        ) {
            ProfileColorPicker(
                colors = ProfileAvatarColors,
                selectedHex = activeProfile.avatarColor,
                onColorSelected = onAvatarColorChange,
                swatchStartIndex = PROFILE_SWATCH_START,
                focus = focus
            )
        }
    }

    SettingsPanel(
        title = "All profiles",
        description = "Select a profile for this device.",
        cardIndex = cardIdx++,
        focus = focus
    ) {
        profiles.forEach { profile ->
            val isActive = profile.id == activeProfile?.id
            val rowFocusIndex = if (!isActive) useButtonIndex++ else -1
            SettingsListRow(
                title = profile.name,
                subtitle = buildString {
                    if (profile.isParental) append("Parental · ")
                    if (profile.hasPin) append("PIN protected · ")
                    append(if (isActive) "Active" else "Tap to switch")
                },
                isFocused = rowFocusIndex >= 0 && focus.isFocused(rowFocusIndex),
                modifier = Modifier.padding(vertical = 4.dp),
                trailing = {
                    if (isActive) {
                        Text("●", color = EpgColors.Accent, fontSize = 12.sp)
                    } else {
                        SettingsFocusButton(
                            text = "Use",
                            onClick = { onSelectProfile(profile.id) },
                            chainIndex = rowFocusIndex,
                            focus = focus
                        )
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
        description = "Filter adult channel groups and restrict access with a PIN.",
        cardIndex = cardIdx++,
        focus = focus
    ) {
        SettingsFocusToggleRow(
            label = "Hide adult content",
            description = if (activeProfile?.hasPin == true) {
                "PIN required to turn on"
            } else {
                "Blocks channels in adult groups (18+, XXX, etc.)"
            },
            enabled = settings.hideAdultContent,
            onToggle = onToggleHideAdult,
            chainIndex = parentalStart,
            focus = focus
        )
        SettingsFocusToggleRow(
            label = "Parental PIN lock",
            description = "Require PIN to switch profiles or open restricted content",
            enabled = settings.parentalPinLockEnabled,
            onToggle = onToggleParentalPinLock,
            chainIndex = parentalStart + 1,
            focus = focus,
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
            startChainIndex = parentalStart + 2,
            focus = focus,
            onSelect = { index -> onMaxContentRating(MaxContentRating.entries[index]) }
        )
        SettingsFocusButton(
            text = if (activeProfile?.hasPin == true) "Change PIN" else "Set PIN",
            onClick = onChangePin,
            chainIndex = parentalStart + 6,
            focus = focus,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun ConnectionsSettingsContent(
    focus: SettingsContentFocus,
    settings: com.grid.tv.domain.model.AppSettings,
    showConnectionForm: Boolean,
    formStartIndex: Int,
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
            focus = focus,
            settings = settings,
            formStartIndex = formStartIndex,
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
            focus = focus,
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
    focus: SettingsContentFocus,
    playlists: List<com.grid.tv.domain.model.Playlist>,
    xtreamAccounts: List<com.grid.tv.domain.model.XtreamAccountInfo>,
    onSelectPlaylist: (Long) -> Unit,
    onStartNewConnection: () -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    SettingsPanel(
        title = "Your connections",
        description = "Select a provider to edit, or add a new one.",
        cardIndex = 0,
        focus = focus
    ) {
        SettingsFocusButton(
            text = "+ Add connection",
            onClick = onStartNewConnection,
            chainIndex = 0,
            focus = focus,
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
        playlists.forEachIndexed { index, playlist ->
            val editIndex = 1 + index * 2
            val removeIndex = editIndex + 1
            SettingsListRow(
                title = playlist.name,
                subtitle = playlistConnectionSubtitle(playlist),
                isFocused = focus.isFocused(editIndex) || focus.isFocused(removeIndex),
                modifier = Modifier.padding(vertical = 4.dp),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsFocusButton(
                            text = "Edit",
                            onClick = { onSelectPlaylist(playlist.id) },
                            chainIndex = editIndex,
                            focus = focus
                        )
                        SettingsFocusButton(
                            text = "Remove",
                            onClick = { onDeletePlaylist(playlist.id) },
                            chainIndex = removeIndex,
                            focus = focus
                        )
                    }
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
private fun ConnectionFormPanel(
    focus: SettingsContentFocus,
    settings: com.grid.tv.domain.model.AppSettings,
    formStartIndex: Int,
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
        description = "Link your IPTV provider using M3U or Xtream Codes.",
        cardIndex = 0,
        focus = focus
    ) {
        val base = formStartIndex
        SettingsFocusTextField(
            label = "Connection name",
            value = name,
            onValueChange = onNameChange,
            placeholder = "e.g. Home IPTV",
            chainIndex = base,
            focus = focus
        )
        SettingsFocusPillGroup(
            labels = listOf("M3U", "Xtream"),
            selectedIndex = if (playlistType == PlaylistType.XTREAM) 1 else 0,
            startChainIndex = base + 1,
            focus = focus,
            onSelect = { index ->
                onPlaylistTypeChange(if (index == 0) PlaylistType.M3U else PlaylistType.XTREAM)
            }
        )
        if (playlistType == PlaylistType.M3U) {
            SettingsFocusTextField(
                label = "M3U URL",
                value = url,
                onValueChange = onUrlChange,
                placeholder = "https://provider.com/playlist.m3u",
                chainIndex = base + 3,
                focus = focus
            )
        } else {
            SettingsFocusTextField(
                label = "Server URL",
                value = xtreamServer,
                onValueChange = onXtreamServerChange,
                placeholder = "http://server:port",
                chainIndex = base + 3,
                focus = focus
            )
            SettingsFocusTextField(
                label = "Username",
                value = xtreamUser,
                onValueChange = onXtreamUserChange,
                placeholder = "Username",
                chainIndex = base + 4,
                focus = focus
            )
            SettingsFocusTextField(
                label = "Password",
                value = xtreamPass,
                onValueChange = onXtreamPassChange,
                placeholder = "Password",
                chainIndex = base + 5,
                focus = focus
            )
        }
        val epgIndex = if (playlistType == PlaylistType.M3U) base + 4 else base + 6
        val refreshIndex = if (playlistType == PlaylistType.M3U) base + 5 else base + 7
        val timeoutStart = if (playlistType == PlaylistType.M3U) base + 6 else base + 8
        val proxyToggleIndex = timeoutStart + 3
        val proxyUrlIndex = proxyToggleIndex + 1
        val saveIndex = proxyUrlIndex + 1
        SettingsFocusTextField(
            label = "EPG URL (optional)",
            value = epgUrl,
            onValueChange = onEpgUrlChange,
            placeholder = "https://provider.com/epg.xml",
            chainIndex = epgIndex,
            focus = focus
        )
        SettingsFocusTextField(
            label = "Refresh every (hours)",
            value = refreshHours,
            onValueChange = onRefreshHoursChange,
            placeholder = "24",
            chainIndex = refreshIndex,
            focus = focus
        )
        Text(
            text = "Connection timeout",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        val timeoutOptions = listOf(5, 10, 20)
        SettingsFocusPillGroup(
            labels = timeoutOptions.map { "${it}s" },
            selectedIndex = timeoutOptions.indexOf(settings.connectionTimeoutSeconds).coerceAtLeast(0),
            startChainIndex = timeoutStart,
            focus = focus,
            onSelect = { index -> onConnectionTimeout(timeoutOptions[index]) }
        )
        SettingsFocusToggleRow(
            label = "Use proxy",
            description = "Route playlist and stream requests through a proxy server",
            enabled = settings.useProxy,
            onToggle = onToggleUseProxy,
            chainIndex = proxyToggleIndex,
            focus = focus,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (settings.useProxy) {
            SettingsFocusTextField(
                label = "Proxy URL",
                value = settings.proxyUrl,
                onValueChange = onProxyUrlChange,
                placeholder = "http://proxy:8080",
                chainIndex = proxyUrlIndex,
                focus = focus
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsFocusButton(
                text = saveLabel,
                onClick = onSaveConnection,
                chainIndex = saveIndex,
                focus = focus,
                isLoading = isConnecting,
                loadingLabel = if (editingPlaylistId != null) "Saving..." else "Connecting..."
            )
            SettingsFocusButton(
                text = "Local M3U file",
                onClick = onPickLocalFile,
                chainIndex = saveIndex + 1,
                focus = focus
            )
            SettingsFocusButton(
                text = "Import TiviMate",
                onClick = onPickTiviMateZip,
                chainIndex = saveIndex + 2,
                focus = focus
            )
            SettingsFocusButton(
                text = "Cancel",
                onClick = onCancelConnectionForm,
                chainIndex = saveIndex + 3,
                focus = focus
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

@Composable
private fun GuideSettingsContent(
    settings: com.grid.tv.domain.model.AppSettings,
    scannerRuntime: ScannerRuntimeState,
    now: Long,
    focus: SettingsContentFocus,
    onRefreshEpg: () -> Unit,
    onOpenEpgResolver: () -> Unit,
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
        description = "Keep the guide up to date.",
        cardIndex = 0,
        focus = focus
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsFocusButton(text = "Refresh EPG now", onClick = onRefreshEpg, chainIndex = 0, focus = focus)
            SettingsFocusButton(text = "Auto-match channels", onClick = onOpenEpgResolver, chainIndex = 1, focus = focus)
        }
    }
    SettingsPanel(
        title = "Guide row height",
        cardIndex = 1,
        focus = focus
    ) {
        val rowLabels = EpgRowHeight.entries.map { height ->
            height.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        SettingsFocusPillGroup(
            labels = rowLabels,
            selectedIndex = EpgRowHeight.entries.indexOf(settings.epgRowHeight).coerceAtLeast(0),
            startChainIndex = 2,
            focus = focus,
            onSelect = { index -> onRowHeight(EpgRowHeight.entries[index]) }
        )
    }
    SettingsPanel(
        title = "Channel Scanner",
        description = "Background checks stream URLs without playing them.",
        cardIndex = 2,
        focus = focus
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
            startChainIndex = 5,
            focus = focus,
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
            startChainIndex = 7,
            focus = focus,
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
            startChainIndex = 12,
            focus = focus,
            onSelect = { index -> onConcurrentChecks(concurrentCounts[index]) }
        )
        SettingsFocusToggleRow(
            label = "Scan on metered / mobile connections",
            enabled = settings.scanOnMetered,
            onToggle = { onToggleScanOnMetered(!settings.scanOnMetered) },
            chainIndex = 16,
            focus = focus
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            SettingsFocusButton(
                text = if (scannerRuntime.isScanning) "Scanning…" else "Scan Now",
                onClick = onScanNow,
                chainIndex = 17,
                focus = focus,
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
        description = "Sidebar programme info and catch-up playback behavior.",
        cardIndex = 3,
        focus = focus
    ) {
        SettingsFocusToggleRow(
            label = "Show EPG programme info on sidebar",
            description = "Display title and time for the selected programme",
            enabled = settings.showEpgProgramInfoOnSidebar,
            onToggle = onToggleShowEpgSidebar,
            chainIndex = 18,
            focus = focus
        )
        SettingsFocusToggleRow(
            label = "Start channel from beginning when catch-up is available",
            description = "Jump to programme start instead of joining live edge",
            enabled = settings.startChannelFromBeginningOnCatchup,
            onToggle = onToggleCatchupFromBeginning,
            chainIndex = 19,
            focus = focus,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PlaybackSettingsContent(
    settings: com.grid.tv.domain.model.AppSettings,
    focus: SettingsContentFocus,
    onRetries: (Int) -> Unit,
    onDefaultQuality: (StreamQuality) -> Unit,
    onBufferSize: (BufferSize) -> Unit,
    onToggleAutoReconnect: () -> Unit,
    onToggleHardwareDecoding: () -> Unit,
    onAspectRatio: (AspectRatioSetting) -> Unit,
    onToggleSubtitles: () -> Unit,
    onSubtitleLanguage: (String) -> Unit,
    onSubtitleFontSize: (SubtitleFontSize) -> Unit,
    onToggleDeinterlacing: () -> Unit,
    onAudioLanguage: (String) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onToggleSleepTimerAuto: () -> Unit
) {
    SettingsPanel(
        title = "Stream playback",
        cardIndex = 0,
        focus = focus
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
                    chainIndex = index,
                    focus = focus
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
            startChainIndex = 3,
            focus = focus,
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
            startChainIndex = 7,
            focus = focus,
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
            chainIndex = 10,
            focus = focus,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsFocusToggleRow(
            label = "Prefer hardware decoding",
            enabled = settings.preferHardwareDecoding,
            onToggle = onToggleHardwareDecoding,
            chainIndex = 11,
            focus = focus,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    SettingsPanel(
        title = "Video display",
        cardIndex = 1,
        focus = focus
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
            startChainIndex = 12,
            focus = focus,
            onSelect = { index -> onAspectRatio(AspectRatioSetting.entries[index]) }
        )
    }
    SettingsPanel(
        title = "Subtitles",
        cardIndex = 2,
        focus = focus
    ) {
        SettingsFocusToggleRow(
            label = "Subtitles",
            enabled = settings.subtitlesEnabled,
            onToggle = onToggleSubtitles,
            chainIndex = 16,
            focus = focus
        )
        SettingsFocusTextField(
            label = "Subtitle language",
            value = settings.subtitleLanguage,
            onValueChange = onSubtitleLanguage,
            placeholder = "en",
            chainIndex = 17,
            focus = focus
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
            startChainIndex = 18,
            focus = focus,
            onSelect = { index -> onSubtitleFontSize(SubtitleFontSize.entries[index]) }
        )
        SettingsFocusToggleRow(
            label = "Deinterlacing",
            description = "Convert interlaced video to progressive frames",
            enabled = settings.deinterlacingEnabled,
            onToggle = onToggleDeinterlacing,
            chainIndex = 21,
            focus = focus,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    SettingsPanel(
        title = "Audio & sleep",
        cardIndex = 3,
        focus = focus
    ) {
        SettingsFocusTextField(
            label = "Preferred audio language",
            value = settings.preferredAudioLanguage,
            onValueChange = onAudioLanguage,
            placeholder = "en",
            chainIndex = 22,
            focus = focus
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
            chainIndex = 23,
            focus = focus,
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
                    onClick = { onSleepTimer(min) },
                    chainIndex = 24 + index,
                    focus = focus
                )
            }
        }
    }
}

@Composable
private fun InterfaceSettingsContent(
    settings: com.grid.tv.domain.model.AppSettings,
    focus: SettingsContentFocus,
    onSidebarAutoHide: (Int) -> Unit,
    onToggleShowChannelNumbers: () -> Unit,
    onDpadSensitivity: (DpadSensitivity) -> Unit,
    onClockDisplay: (ClockDisplay) -> Unit,
    onTheme: (AppThemeId) -> Unit,
    onTogglePictureInPicture: () -> Unit
) {
    SettingsPanel(
        title = "Picture-in-Picture & sidebar",
        description = "System PiP and guide sidebar behavior.",
        cardIndex = 0,
        focus = focus
    ) {
        SettingsFocusToggleRow(
            label = "Android TV Picture-in-Picture",
            enabled = settings.pictureInPictureEnabled,
            onToggle = onTogglePictureInPicture,
            chainIndex = 0,
            focus = focus
        )
        Text(
            text = "Sidebar auto-hide timeout",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        val hideOptions = listOf(3, 5, 10, -1)
        val hideLabels = listOf("3s", "5s", "10s", "Never")
        SettingsFocusPillGroup(
            labels = hideLabels,
            selectedIndex = hideOptions.indexOf(settings.sidebarAutoHideSeconds).coerceAtLeast(0),
            startChainIndex = 1,
            focus = focus,
            onSelect = { index -> onSidebarAutoHide(hideOptions[index]) }
        )
    }
    SettingsPanel(
        title = "Guide navigation",
        cardIndex = 1,
        focus = focus
    ) {
        SettingsFocusToggleRow(
            label = "Show channel numbers",
            enabled = settings.showChannelNumbers,
            onToggle = onToggleShowChannelNumbers,
            chainIndex = 5,
            focus = focus
        )
    }
    SettingsPanel(
        title = "Remote & clock",
        cardIndex = 2,
        focus = focus
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
            startChainIndex = 6,
            focus = focus,
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
            startChainIndex = 9,
            focus = focus,
            onSelect = { index -> onClockDisplay(ClockDisplay.entries[index]) }
        )
    }
    SettingsPanel(
        title = "Theme",
        description = "Accent, focus, and card colors across the app.",
        cardIndex = 3,
        focus = focus
    ) {
        SettingsFocusPillGroup(
            labels = AppThemeId.entries.map { it.displayName },
            selectedIndex = AppThemeId.entries.indexOf(settings.themeId).coerceAtLeast(0),
            startChainIndex = 12,
            focus = focus,
            onSelect = { index -> onTheme(AppThemeId.entries[index]) }
        )
    }
}

@Composable
private fun RecordingsSettingsContent(
    currentStorageLabel: String?,
    storageOptions: List<com.grid.tv.feature.recording.StorageOption>,
    focus: SettingsContentFocus,
    onSelectStorage: (String) -> Unit
) {
    SettingsPanel(
        title = "Recording storage",
        description = "Choose where completed recordings are saved.",
        cardIndex = 0,
        focus = focus
    ) {
        Text(
            text = currentStorageLabel ?: "Not configured",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp
        )
        storageOptions.forEachIndexed { index, option ->
            SettingsListRow(
                title = option.label,
                subtitle = option.displayLine(),
                isFocused = focus.isFocused(index),
                modifier = Modifier.padding(vertical = 4.dp),
                trailing = {
                    SettingsFocusButton(
                        text = "Use",
                        onClick = { onSelectStorage(option.id) },
                        chainIndex = index,
                        focus = focus
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
    importSummary: String?,
    cacheMessage: String?,
    focus: SettingsContentFocus,
    onSignOut: () -> Unit,
    onExportBackup: () -> Unit,
    onClearCache: () -> Unit,
    onResetSettings: () -> Unit,
    onResetApp: () -> Unit
) {
    SettingsPanel(
        title = "GRID",
        cardIndex = 0,
        focus = focus
    ) {
        Text("Version ${SettingsViewModel.APP_VERSION}", color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
        Text("Live TV Guide for Android TV", color = EpgColors.TextDimmed, fontFamily = DmSansFamily, fontSize = 13.sp)
        SettingsFocusButton(
            text = "Sign out",
            onClick = onSignOut,
            chainIndex = 0,
            focus = focus,
            destructive = true,
            modifier = Modifier.padding(top = 12.dp)
        )
        SettingsFocusButton(
            text = "Export .grid backup",
            onClick = onExportBackup,
            chainIndex = 1,
            focus = focus,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsFocusButton(
            text = "Clear cache",
            onClick = onClearCache,
            chainIndex = 2,
            focus = focus,
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
        title = "Reset settings",
        description = "Restore defaults for playback, guide, interface, and parental controls. Profiles and connections are kept.",
        cardIndex = 1,
        focus = focus
    ) {
        SettingsFocusButton(
            text = "Reset all settings",
            onClick = onResetSettings,
            chainIndex = 3,
            focus = focus,
            destructive = true
        )
    }
    SettingsPanel(
        title = "Reset app",
        description = "Delete all profiles, connections, watch history, favorites, and settings. Restarts as a fresh install.",
        cardIndex = 2,
        focus = focus
    ) {
        SettingsFocusButton(
            text = "Reset everything",
            onClick = onResetApp,
            chainIndex = 4,
            focus = focus,
            destructive = true
        )
    }
}
