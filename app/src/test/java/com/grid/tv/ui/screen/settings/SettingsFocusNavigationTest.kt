package com.grid.tv.ui.screen.settings

import com.grid.tv.ui.component.EpgNavTab
import com.grid.tv.ui.component.GridNavTabs
import com.grid.tv.ui.component.TopBarProfileIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFocusNavigationTest {

  private fun sampleRows() = listOf(
        SettingsRowModel.Toggle("a", "Toggle A", checked = false, onToggle = {}),
        SettingsRowModel.Selection("b", "Selection B", options = listOf("One", "Two"), selectedIndex = 0, onSelect = {}),
        SettingsRowModel.Action("c", "Action C", onClick = {}),
    )

    private fun setupController(
        categoryCount: Int = SettingsCategory.entries.size,
        rows: List<SettingsRowModel> = sampleRows(),
        initialZone: SettingsFocusZone = SettingsFocusZone.CATEGORIES,
    ): Pair<SettingsFocusUiState, SettingsFocusController> {
        val ui = SettingsFocusUiState()
        ui.focusZone = initialZone
        val controller = SettingsFocusController(ui)
        var backCalled = false
        controller.bind(
            SettingsFocusDeps(
                categoryCount = { categoryCount },
                optionRows = { rows },
                modalBlockingFocus = { false },
                profileMenuOpen = { false },
                onDismissProfileMenu = {},
                onBack = { backCalled = true },
                onNavigateTab = {},
                onOpenProfileMenu = {},
                handleScreenBack = { false },
            ),
        )
        return ui to controller
    }

    @Test
    fun transitionToZone_categoriesToOptions() {
        val (ui, controller) = setupController()
        controller.transitionToZone(SettingsFocusZone.OPTIONS, "test")
        assertEquals(SettingsFocusZone.OPTIONS, ui.focusZone)
        assertEquals(0, ui.optionIndex)
    }

    @Test
    fun selectCategory_resetsOptionIndex() {
        val (ui, controller) = setupController()
        ui.optionIndex = 2
        controller.selectCategory(3)
        assertEquals(3, ui.categoryIndex)
        assertEquals(0, ui.optionIndex)
    }

    @Test
    fun focusableOptionIndices_skipsInfoRows() {
        val rows = listOf(
            SettingsRowModel.Info("info", "Label", "Value"),
            SettingsRowModel.Action("action", "Do thing", onClick = {}),
        )
        assertEquals(listOf(1), focusableOptionIndices(rows))
    }

    @Test
    fun clampOptionIndex_snapsToFirstFocusableRow() {
        val ui = SettingsFocusUiState()
        ui.optionIndex = 5
        val rows = sampleRows()
        ui.clampOptionIndex(rows)
        assertEquals(0, ui.optionIndex)
    }

    @Test
    fun activateFocusedOption_togglesToggleRow() {
        val (ui, controller) = setupController(initialZone = SettingsFocusZone.OPTIONS)
        var toggled = false
        val rows = listOf(
            SettingsRowModel.Toggle("t", "Test", checked = false, onToggle = { toggled = true }),
        )
        controller.bind(
            SettingsFocusDeps(
                categoryCount = { SettingsCategory.entries.size },
                optionRows = { rows },
                modalBlockingFocus = { false },
                profileMenuOpen = { false },
                onDismissProfileMenu = {},
                onBack = {},
                onNavigateTab = {},
                onOpenProfileMenu = {},
                handleScreenBack = { false },
            ),
        )
        ui.optionIndex = 0
        controller.activateFocusedOption()
        assertTrue(toggled)
    }

    @Test
    fun topBarFocusIndex_withinNavTabs() {
        val ui = SettingsFocusUiState()
        ui.topBarFocusIndex = GridNavTabs.lastIndex
        assertTrue(ui.topBarFocusIndex <= TopBarProfileIndex)
    }

    @Test
    fun categoryCount_matchesEnum() {
        assertEquals(6, SettingsCategory.entries.size)
    }

    @Test
    fun buildAccountRows_includesManageProfilesAction() {
        val rows = buildSettingsOptionRows(
            SettingsCategory.Account,
            minimalRowsContext(),
        )
        assertTrue(rows.any { it is SettingsRowModel.Action && it.id == "account.manage" })
    }

    private fun minimalRowsContext(): SettingsRowsContext {
        val settings = com.grid.tv.domain.model.AppSettings()
        return SettingsRowsContext(
            settings = settings,
            activeProfile = null,
            profiles = emptyList(),
            includeUntaggedVodContent = false,
            playlists = emptyList(),
            xtreamAccounts = emptyList(),
            showConnectionForm = false,
            editingPlaylistId = null,
            connectionName = "",
            connectionUrl = "",
            connectionEpgUrl = "",
            connectionRefreshHours = "24",
            connectionPlaylistType = com.grid.tv.domain.model.PlaylistType.M3U,
            connectionXtreamServer = "",
            connectionXtreamUser = "",
            connectionXtreamPass = "",
            connectionProgress = "",
            isConnecting = false,
            scannerRuntime = com.grid.tv.domain.model.ScannerRuntimeState(),
            now = 0L,
            channelGroupSummary = "All channel groups",
            usbStorageReady = false,
            usbStorageStatusLine = null,
            currentStorageLabel = null,
            storageOptions = emptyList(),
            externalPlayer = com.grid.tv.player.ExternalPlayerId.NONE,
            nextUpAutoPlay = false,
            vodSyncIntervalHours = 24,
            appVersion = "1.0",
            updateUiState = com.grid.tv.ui.viewmodel.ManualUpdateUiState.Idle,
            lowEndDeviceModeActive = false,
            lowEndDeviceSummary = null,
            importSummary = null,
            cacheMessage = null,
            playbackHealthSummary = com.grid.tv.ui.viewmodel.PlaybackHealthSummary(),
            isSignedIn = false,
            signedInEmail = null,
            googleSignInAvailable = false,
            onManageProfiles = {},
            onSelectProfile = {},
            onToggleIncludeUntaggedVodContent = {},
            onAvatarColorChange = {},
            onStartNewConnection = {},
            onSelectPlaylist = {},
            onDeletePlaylist = {},
            onCancelConnectionForm = {},
            onSaveConnection = {},
            onPickLocalFile = {},
            onPickTiviMateZip = {},
            onConnectionNameChange = {},
            onConnectionUrlChange = {},
            onConnectionEpgUrlChange = {},
            onConnectionRefreshHoursChange = {},
            onConnectionPlaylistTypeChange = {},
            onConnectionXtreamServerChange = {},
            onConnectionXtreamUserChange = {},
            onConnectionXtreamPassChange = {},
            onConnectionTimeout = {},
            onToggleUseProxy = {},
            onProxyUrlChange = {},
            onRefreshEpg = {},
            onOpenEpgResolver = {},
            onEditChannelGroups = {},
            onChannelGroupNavigationMode = {},
            onRowHeight = {},
            onToggleAutoScan = {},
            onScanInterval = {},
            onConcurrentChecks = {},
            onToggleScanOnMetered = {},
            onScanNow = {},
            onToggleShowEpgSidebar = {},
            onToggleCatchupFromBeginning = {},
            onRetries = {},
            onDefaultQuality = {},
            onBufferSize = {},
            onToggleAutoReconnect = {},
            onToggleHardwareDecoding = {},
            onExternalPlayerChange = {},
            onNextUpAutoPlayChange = {},
            onSyncIntervalChange = {},
            onAspectRatio = {},
            onToggleSubtitles = {},
            onSubtitleLanguage = {},
            onSubtitleFontSize = {},
            onSubtitlePosition = {},
            onSubtitleDelayMs = {},
            onToggleDeinterlacing = {},
            onAudioLanguage = {},
            onSleepTimer = {},
            onToggleSleepTimerAuto = {},
            onSelectStorage = {},
            onSidebarAutoHide = {},
            onToggleShowChannelNumbers = {},
            onDpadSensitivity = {},
            onClockDisplay = {},
            onTheme = {},
            onToggleHideAdult = {},
            onToggleParentalPinLock = {},
            onMaxContentRating = {},
            onChangePin = {},
            onSignIn = {},
            onSignOut = {},
            onExportBackup = {},
            onClearCache = {},
            onCheckForUpdates = {},
            onDownloadUpdate = {},
            onRefreshPlaybackHealth = {},
            onLogPlaybackHealth = {},
            onResetSettings = {},
            onResetApp = {},
        )
    }
}
