package com.grid.tv.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grid.tv.BuildConfig
import com.grid.tv.di.SupabaseEntryPoint
import com.grid.tv.domain.model.PlaylistType
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.ui.component.ConnectionLoadingOverlay
import com.grid.tv.ui.component.ConnectionResultDialog
import com.grid.tv.ui.component.DestructiveConfirmDialog
import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.EpgTopBar
import com.grid.tv.ui.component.FactoryResetConfirmDialog
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.GuideGroupPickerDialog
import com.grid.tv.ui.screen.ManageProfilesOverlay
import com.grid.tv.ui.component.PinEntryDialog
import com.grid.tv.ui.component.ScreenBackHandler
import com.grid.tv.ui.component.TopBarProfileIndex
import com.grid.tv.ui.focus.TvScreenFocusRoot
import com.grid.tv.ui.screen.settings.SettingsCategory
import com.grid.tv.ui.screen.settings.SettingsFocusController
import com.grid.tv.ui.screen.settings.SettingsFocusDeps
import com.grid.tv.ui.screen.settings.SettingsFocusDispatcher
import com.grid.tv.ui.screen.settings.SettingsFocusUiState
import com.grid.tv.ui.screen.settings.SettingsFocusZone
import com.grid.tv.ui.screen.settings.SettingsRowsContext
import com.grid.tv.ui.screen.settings.SettingsTwoColumnLayout
import com.grid.tv.ui.screen.settings.buildSettingsOptionRows
import com.grid.tv.ui.screen.settings.channelGroupSummary
import com.grid.tv.ui.screen.settings.clampOptionIndex
import com.grid.tv.ui.screen.settings.focusableOptionIndices
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.ui.viewmodel.AuthUiState
import com.grid.tv.ui.viewmodel.AuthViewModel
import com.grid.tv.ui.viewmodel.ProfileViewModel
import com.grid.tv.ui.viewmodel.SettingsViewModel
import com.grid.tv.ui.viewmodel.UpdateViewModel
import com.grid.tv.util.DEFAULT_PROFILE_AVATAR_COLOR
import com.grid.tv.util.ProfileAvatarColors
import com.grid.tv.util.profileInitials
import com.grid.tv.util.quitAppToHome
import dagger.hilt.android.EntryPointAccessors
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import kotlinx.coroutines.delay

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
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
    val cacheMessage by viewModel.cacheMessage.collectAsStateWithLifecycle()
    val playbackHealthSummary by viewModel.playbackHealthSummary.collectAsStateWithLifecycle()
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()

    val supabaseClient = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SupabaseEntryPoint::class.java,
        ).supabaseClientProvider().clientOrNull()
    }
    var startGoogleSignIn: () -> Unit = {}
    if (supabaseClient != null) {
        val googleSignIn = supabaseClient.composeAuth.rememberSignInWithGoogle(
            onResult = { result ->
                when (result) {
                    NativeSignInResult.ClosedByUser -> authViewModel.onGoogleSignInCancelled()
                    is NativeSignInResult.Error -> authViewModel.onGoogleSignInFailed(
                        result.message ?: "Google sign-in failed. Please try again.",
                    )
                    is NativeSignInResult.NetworkError -> authViewModel.onGoogleSignInFailed(
                        "Network error during sign-in. Check your connection and try again.",
                    )
                    is NativeSignInResult.Success -> authViewModel.onGoogleSignInSuccess()
                }
            },
        )
        startGoogleSignIn = {
            authViewModel.clearError()
            authViewModel.onGoogleSignInStarted()
            googleSignIn.startFlow()
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshStorageSettings()
    }

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
    var showGuideGroupPicker by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var refreshHours by remember { mutableStateOf("24") }
    var playlistType by remember { mutableStateOf(PlaylistType.M3U) }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }
    var editingPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showConnectionForm by rememberSaveable { mutableStateOf(false) }

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

    val categories = SettingsCategory.entries
    val focusUi = remember { SettingsFocusUiState() }
    val focusController = remember { SettingsFocusController(focusUi) }

    val rowsContext = remember(
        settings,
        activeProfile,
        profiles,
        includeUntaggedVodContent,
        playlists,
        xtreamAccounts,
        showConnectionForm,
        editingPlaylistId,
        name,
        url,
        epgUrl,
        refreshHours,
        playlistType,
        xtreamServer,
        xtreamUser,
        xtreamPass,
        progress,
        isConnecting,
        scannerRuntime,
        now,
        usbStorageReady,
        usbStorageStatusLine,
        currentStorageLabel,
        storageOptions,
        viewModel.externalPlayer,
        viewModel.nextUpAutoPlay,
        viewModel.vodSyncIntervalHours,
        updateUiState,
        cacheMessage,
        importSummary,
        playbackHealthSummary,
        authUiState,
        signedInAccount,
    ) {
        SettingsRowsContext(
            settings = settings,
            activeProfile = activeProfile,
            profiles = profiles,
            includeUntaggedVodContent = includeUntaggedVodContent,
            playlists = playlists,
            xtreamAccounts = xtreamAccounts,
            showConnectionForm = showConnectionForm,
            editingPlaylistId = editingPlaylistId,
            connectionName = name,
            connectionUrl = url,
            connectionEpgUrl = epgUrl,
            connectionRefreshHours = refreshHours,
            connectionPlaylistType = playlistType,
            connectionXtreamServer = xtreamServer,
            connectionXtreamUser = xtreamUser,
            connectionXtreamPass = xtreamPass,
            connectionProgress = progress,
            isConnecting = isConnecting,
            scannerRuntime = scannerRuntime,
            now = now,
            channelGroupSummary = channelGroupSummary(settings),
            usbStorageReady = usbStorageReady,
            usbStorageStatusLine = usbStorageStatusLine,
            currentStorageLabel = currentStorageLabel,
            storageOptions = storageOptions,
            externalPlayer = viewModel.externalPlayer,
            nextUpAutoPlay = viewModel.nextUpAutoPlay,
            vodSyncIntervalHours = viewModel.vodSyncIntervalHours,
            appVersion = viewModel.appVersion,
            updateUiState = updateUiState,
            lowEndDeviceModeActive = LowEndDeviceMode.isActive(context),
            lowEndDeviceSummary = LowEndDeviceMode.profile(context).let { profile ->
                if (!profile.active) null
                else "RAM ${profile.totalRamMb} MB · max ${profile.maxPaneCount} panes"
            },
            importSummary = importSummary,
            cacheMessage = cacheMessage,
            playbackHealthSummary = playbackHealthSummary,
            isSignedIn = authUiState is AuthUiState.Authenticated,
            signedInEmail = signedInAccount?.email ?: signedInAccount?.displayName,
            googleSignInAvailable = supabaseClient != null && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
            onManageProfiles = {
                Log.d("SettingsScreen", "Manage profiles")
                showManageProfilesOverlay = true
            },
            onSelectProfile = { profileViewModel.switchProfile(it) },
            onToggleIncludeUntaggedVodContent = {
                viewModel.updateIncludeUntaggedVodContent(!includeUntaggedVodContent)
            },
            onAvatarColorChange = { color ->
                activeProfile?.id?.let { profileViewModel.updateAvatarColor(it, color) }
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
            onSelectPlaylist = {
                editingPlaylistId = it
                showConnectionForm = true
            },
            onDeletePlaylist = { pendingDeletePlaylistId = it },
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
                    refreshHours = refreshHours.toIntOrNull() ?: 24,
                )
            },
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip,
            onConnectionNameChange = { name = it },
            onConnectionUrlChange = { url = it },
            onConnectionEpgUrlChange = { epgUrl = it },
            onConnectionRefreshHoursChange = { refreshHours = it },
            onConnectionPlaylistTypeChange = { playlistType = it },
            onConnectionXtreamServerChange = { xtreamServer = it },
            onConnectionXtreamUserChange = { xtreamUser = it },
            onConnectionXtreamPassChange = { xtreamPass = it },
            onConnectionTimeout = { viewModel.updateConnectionTimeout(it) },
            onToggleUseProxy = { viewModel.updateUseProxy(!settings.useProxy) },
            onProxyUrlChange = { viewModel.updateProxyUrl(it) },
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
            },
            onRetries = { viewModel.updateRetries(it) },
            onDefaultQuality = { viewModel.updateDefaultStreamQuality(it) },
            onBufferSize = { viewModel.updateBufferSize(it) },
            onToggleAutoReconnect = { viewModel.updateAutoReconnectOnDrop(!settings.autoReconnectOnDrop) },
            onToggleHardwareDecoding = {
                viewModel.updatePreferHardwareDecoding(!settings.preferHardwareDecoding)
            },
            onExternalPlayerChange = viewModel::setExternalPlayer,
            onNextUpAutoPlayChange = viewModel::setNextUpAutoPlay,
            onSyncIntervalChange = viewModel::setVodSyncIntervalHours,
            onAspectRatio = { viewModel.updateAspectRatio(it) },
            onToggleSubtitles = { viewModel.updateSubtitlesEnabled(!settings.subtitlesEnabled) },
            onSubtitleLanguage = { viewModel.updateSubtitleLanguage(it) },
            onSubtitleFontSize = { viewModel.updateSubtitleFontSize(it) },
            onSubtitlePosition = { viewModel.updateSubtitlePosition(it) },
            onSubtitleDelayMs = { viewModel.updateSubtitleDelayMs(it) },
            onToggleDeinterlacing = { viewModel.updateDeinterlacingEnabled(!settings.deinterlacingEnabled) },
            onAudioLanguage = { viewModel.updateAudioLanguage(it) },
            onSleepTimer = { viewModel.updateSleepTimerMinutes(it) },
            onToggleSleepTimerAuto = { viewModel.updateSleepTimerAutoEnabled(!settings.sleepTimerAutoEnabled) },
            onSelectStorage = { viewModel.setRecordingStorage(it) },
            onSidebarAutoHide = { viewModel.updateSidebarAutoHideSeconds(it) },
            onToggleShowChannelNumbers = { viewModel.updateShowChannelNumbers(!settings.showChannelNumbers) },
            onDpadSensitivity = { viewModel.updateDpadSidebarSensitivity(it) },
            onClockDisplay = { viewModel.updateClockDisplay(it) },
            onTheme = { viewModel.updateTheme(it) },
            onToggleHideAdult = {
                val next = !settings.hideAdultContent
                if (next && activeProfile?.hasPin == true) {
                    pendingHideAdultValue = true
                    showHideAdultPinDialog = true
                } else {
                    viewModel.updateHideAdultContent(next)
                }
            },
            onToggleParentalPinLock = { viewModel.updateParentalPinLock(!settings.parentalPinLockEnabled) },
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
            onSignIn = startGoogleSignIn,
            onSignOut = { showSignOutConfirm = true },
            onExportBackup = { viewModel.exportBackup(context.cacheDir) },
            onClearCache = { showClearCacheConfirm = true },
            onCheckForUpdates = updateViewModel::checkForUpdate,
            onDownloadUpdate = updateViewModel::downloadAndInstall,
            onRefreshPlaybackHealth = { viewModel.refreshPlaybackHealthSummary() },
            onLogPlaybackHealth = { viewModel.logPlaybackHealthDiagnostics() },
            onResetSettings = { showResetSettingsConfirm = true },
            onResetApp = { showResetConfirm = true },
        )
    }

    val activeCategory = categories[focusUi.categoryIndex.coerceIn(0, categories.lastIndex)]
    val optionRows = remember(rowsContext, activeCategory) {
        buildSettingsOptionRows(activeCategory, rowsContext)
    }

    LaunchedEffect(focusUi.categoryIndex, optionRows.size) {
        focusUi.clampOptionIndex(optionRows)
    }

    val categoryFocusRequesters = remember(categories.size) { List(categories.size) { FocusRequester() } }
    val optionFocusRequesters = remember(optionRows.size, activeCategory) {
        List(optionRows.size) { FocusRequester() }
    }
    val topBarFocusRequester = remember { FocusRequester() }

    val modalOpen = showManageProfilesOverlay || showGuideGroupPicker ||
        showResetConfirm || showResetSettingsConfirm || pendingDeletePlaylistId != null ||
        showSignOutConfirm || showClearCacheConfirm || showChangePinDialog ||
        showHideAdultPinDialog || connectionDialog != null

    fun activateNavTab(tab: EpgNavTab) {
        when (tab) {
            EpgNavTab.Guide, EpgNavTab.Home -> onNavigateHome()
            EpgNavTab.Vod, EpgNavTab.Movies -> onNavigateVod(0)
            EpgNavTab.Series -> onNavigateVod(1)
            EpgNavTab.Recordings -> onNavigateRecordings()
            EpgNavTab.Favorites -> onOpenFavorites()
            EpgNavTab.Search -> onNavigateHome()
            EpgNavTab.Settings -> Unit
        }
    }

    fun handleBackKey(): Boolean {
        when {
            showConnectionForm && focusUi.categoryIndex == SettingsCategory.Account.ordinal -> {
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
            focusUi.focusZone == SettingsFocusZone.OPTIONS -> {
                focusController.transitionToZone(SettingsFocusZone.CATEGORIES, "backToCategories")
            }
            else -> return false
        }
        return true
    }

    focusController.bind(
        SettingsFocusDeps(
            categoryCount = { categories.size },
            optionRows = { optionRows },
            modalBlockingFocus = { modalOpen },
            profileMenuOpen = { profileMenuOpen },
            onDismissProfileMenu = { profileMenuOpen = false },
            onBack = onBack,
            onNavigateTab = ::activateNavTab,
            onOpenProfileMenu = {
                profileMenuOpen = true
                profileMenuFocusIndex = 0
            },
            handleScreenBack = ::handleBackKey,
        ),
    )

    ScreenBackHandler(
        onNavigateBack = onBack,
        onBackPressed = ::handleBackKey,
    )

    SettingsFocusDispatcher(
        ui = focusUi,
        categoryCount = categories.size,
        optionRowCount = optionRows.size,
        categoryFocusRequesters = categoryFocusRequesters,
        optionFocusRequesters = optionFocusRequesters,
        topBarFocusRequester = topBarFocusRequester,
        enabled = !modalOpen && !showManageProfilesOverlay,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background),
    ) {
        TvScreenFocusRoot(
            modifier = Modifier.fillMaxSize(),
            enabled = !modalOpen,
            onBack = ::handleBackKey,
            onKey = focusController::handleKey,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showManageProfilesOverlay) {
                            Modifier.focusProperties { canFocus = false }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                EpgTopBar(
                    selectedTab = EpgNavTab.Settings,
                    focusedNavTabIndex = focusUi.topBarFocusIndex.coerceIn(0, GridNavTabs.lastIndex),
                    navFocused = focusUi.focusZone == SettingsFocusZone.TOP_BAR &&
                        focusUi.topBarFocusIndex <= GridNavTabs.lastIndex,
                    profileFocused = focusUi.focusZone == SettingsFocusZone.TOP_BAR &&
                        focusUi.topBarFocusIndex == TopBarProfileIndex,
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
                        focusUi.topBarFocusIndex = GridNavTabs.indexOf(tab)
                        activateNavTab(tab)
                    },
                    miniPlayer = {},
                    modifier = Modifier
                        .focusRequester(topBarFocusRequester)
                        .focusable(
                            enabled = focusUi.focusZone == SettingsFocusZone.TOP_BAR && !showManageProfilesOverlay,
                        ),
                )

                SettingsTwoColumnLayout(
                    categoryIndex = focusUi.categoryIndex,
                    categoryZoneActive = focusUi.focusZone == SettingsFocusZone.CATEGORIES,
                    optionIndex = focusUi.optionIndex,
                    optionZoneActive = focusUi.focusZone == SettingsFocusZone.OPTIONS,
                    categories = categories,
                    optionRows = optionRows,
                    categoryFocusRequesters = categoryFocusRequesters,
                    optionFocusRequesters = optionFocusRequesters,
                    onCategoryFocused = { index ->
                        focusController.selectCategory(index)
                        focusController.transitionToZone(SettingsFocusZone.CATEGORIES, "categoryFocus")
                    },
                    onOptionFocused = { index ->
                        val focusable = focusableOptionIndices(optionRows)
                        if (index in focusable) {
                            focusUi.optionIndex = index
                            focusController.transitionToZone(SettingsFocusZone.OPTIONS, "optionFocus")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }

        connectionDialog?.let { dialogState ->
            ConnectionResultDialog(
                state = dialogState,
                onDismiss = { viewModel.dismissConnectionDialog() },
                onGoToGuide = {
                    viewModel.dismissConnectionDialog()
                    onNavigateHome()
                },
            )
        }

        if (isConnecting && showConnectionForm && focusUi.categoryIndex == SettingsCategory.Account.ordinal) {
            ConnectionLoadingOverlay(
                message = if (editingPlaylistId != null) "Saving connection…" else "Connecting to your provider…",
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
                },
            )
        }

        if (showResetConfirm) {
            FactoryResetConfirmDialog(
                onDismiss = { showResetConfirm = false },
                onConfirm = {
                    showResetConfirm = false
                    viewModel.resetAllData(onRestartToOnboarding)
                },
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
                },
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
                },
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
                },
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
                },
            )
        }

        if (showManageProfilesOverlay) {
            ManageProfilesOverlay(
                profiles = profiles,
                activeProfileId = activeProfile?.id,
                onDismiss = { showManageProfilesOverlay = false },
                onCreateProfile = { profileName ->
                    val colorIndex = profiles.size % ProfileAvatarColors.size
                    val color = ProfileAvatarColors[colorIndex]
                    val hex = String.format(
                        "#%02X%02X%02X",
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt(),
                    )
                    profileViewModel.createProfile(profileName, hex, pin = null, parental = false)
                },
                onRenameProfile = { profileId, profileName ->
                    profileViewModel.updateProfileName(profileId, profileName)
                },
                onDeleteProfile = { profileId ->
                    profileViewModel.deleteProfile(profileId)
                },
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
                        },
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
                    verifyPin = { pin -> profileViewModel.verifyPin(profile.id, pin) },
                )
            }
        }
    }
}
