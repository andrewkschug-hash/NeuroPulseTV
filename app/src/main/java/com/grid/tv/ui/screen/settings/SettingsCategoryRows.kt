package com.grid.tv.ui.screen.settings

import com.grid.tv.domain.model.AppSettings
import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.domain.model.AspectRatioSetting
import com.grid.tv.domain.model.BufferSize
import com.grid.tv.domain.model.ClockDisplay
import com.grid.tv.domain.model.DpadSensitivity
import com.grid.tv.domain.model.EpgRowHeight
import com.grid.tv.domain.model.MaxContentRating
import com.grid.tv.domain.model.Playlist
import com.grid.tv.domain.model.PlaylistType
import com.grid.tv.domain.model.ScannerRuntimeState
import com.grid.tv.domain.model.StreamQuality
import com.grid.tv.domain.model.SubtitleFontSize
import com.grid.tv.domain.model.SubtitlePosition
import com.grid.tv.domain.model.UserProfile
import com.grid.tv.domain.model.XtreamAccountInfo
import com.grid.tv.feature.recording.StorageOption
import com.grid.tv.player.ExternalPlayerId
import com.grid.tv.ui.viewmodel.ManualUpdateUiState
import com.grid.tv.ui.viewmodel.PlaybackHealthSummary
import com.grid.tv.util.ProfileAvatarColors
import com.grid.tv.util.colorToHex
import com.grid.tv.util.sanitizeProfileAvatarColorHex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SettingsRowsContext(
    val settings: AppSettings,
    val activeProfile: UserProfile?,
    val profiles: List<UserProfile>,
    val includeUntaggedVodContent: Boolean,
    val playlists: List<Playlist>,
    val xtreamAccounts: List<XtreamAccountInfo>,
    val showConnectionForm: Boolean,
    val editingPlaylistId: Long?,
    val connectionName: String,
    val connectionUrl: String,
    val connectionEpgUrl: String,
    val connectionRefreshHours: String,
    val connectionPlaylistType: PlaylistType,
    val connectionXtreamServer: String,
    val connectionXtreamUser: String,
    val connectionXtreamPass: String,
    val connectionProgress: String,
    val isConnecting: Boolean,
    val scannerRuntime: ScannerRuntimeState,
    val now: Long,
    val channelGroupSummary: String,
    val usbStorageReady: Boolean,
    val usbStorageStatusLine: String?,
    val currentStorageLabel: String?,
    val storageOptions: List<StorageOption>,
    val externalPlayer: ExternalPlayerId,
    val nextUpAutoPlay: Boolean,
    val vodSyncIntervalHours: Int,
    val appVersion: String,
    val updateUiState: ManualUpdateUiState,
    val lowEndDeviceModeActive: Boolean,
    val lowEndDeviceSummary: String?,
    val importSummary: String?,
    val cacheMessage: String?,
    val playbackHealthSummary: PlaybackHealthSummary,
    val isSignedIn: Boolean,
    val signedInEmail: String?,
    val googleSignInAvailable: Boolean,
    val onManageProfiles: () -> Unit,
    val onSelectProfile: (Long) -> Unit,
    val onToggleIncludeUntaggedVodContent: () -> Unit,
    val onAvatarColorChange: (String) -> Unit,
    val onStartNewConnection: () -> Unit,
    val onSelectPlaylist: (Long) -> Unit,
    val onDeletePlaylist: (Long) -> Unit,
    val onCancelConnectionForm: () -> Unit,
    val onSaveConnection: () -> Unit,
    val onPickLocalFile: () -> Unit,
    val onPickTiviMateZip: () -> Unit,
    val onConnectionNameChange: (String) -> Unit,
    val onConnectionUrlChange: (String) -> Unit,
    val onConnectionEpgUrlChange: (String) -> Unit,
    val onConnectionRefreshHoursChange: (String) -> Unit,
    val onConnectionPlaylistTypeChange: (PlaylistType) -> Unit,
    val onConnectionXtreamServerChange: (String) -> Unit,
    val onConnectionXtreamUserChange: (String) -> Unit,
    val onConnectionXtreamPassChange: (String) -> Unit,
    val onConnectionTimeout: (Int) -> Unit,
    val onToggleUseProxy: () -> Unit,
    val onProxyUrlChange: (String) -> Unit,
    val onRefreshEpg: () -> Unit,
    val onOpenEpgResolver: () -> Unit,
    val onEditChannelGroups: () -> Unit,
    val onRowHeight: (EpgRowHeight) -> Unit,
    val onToggleAutoScan: (Boolean) -> Unit,
    val onScanInterval: (Int) -> Unit,
    val onConcurrentChecks: (Int) -> Unit,
    val onToggleScanOnMetered: (Boolean) -> Unit,
    val onScanNow: () -> Unit,
    val onToggleShowEpgSidebar: () -> Unit,
    val onToggleCatchupFromBeginning: () -> Unit,
    val onRetries: (Int) -> Unit,
    val onDefaultQuality: (StreamQuality) -> Unit,
    val onBufferSize: (BufferSize) -> Unit,
    val onToggleAutoReconnect: () -> Unit,
    val onToggleHardwareDecoding: () -> Unit,
    val onExternalPlayerChange: (ExternalPlayerId) -> Unit,
    val onNextUpAutoPlayChange: (Boolean) -> Unit,
    val onSyncIntervalChange: (Int) -> Unit,
    val onAspectRatio: (AspectRatioSetting) -> Unit,
    val onToggleSubtitles: () -> Unit,
    val onSubtitleLanguage: (String) -> Unit,
    val onSubtitleFontSize: (SubtitleFontSize) -> Unit,
    val onSubtitlePosition: (SubtitlePosition) -> Unit,
    val onSubtitleDelayMs: (Long) -> Unit,
    val onToggleDeinterlacing: () -> Unit,
    val onAudioLanguage: (String) -> Unit,
    val onSleepTimer: (Int) -> Unit,
    val onToggleSleepTimerAuto: () -> Unit,
    val onSelectStorage: (String) -> Unit,
    val onSidebarAutoHide: (Int) -> Unit,
    val onToggleShowChannelNumbers: () -> Unit,
    val onDpadSensitivity: (DpadSensitivity) -> Unit,
    val onClockDisplay: (ClockDisplay) -> Unit,
    val onTheme: (AppThemeId) -> Unit,
    val onToggleHideAdult: () -> Unit,
    val onToggleParentalPinLock: () -> Unit,
    val onMaxContentRating: (MaxContentRating) -> Unit,
    val onChangePin: () -> Unit,
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
    val onExportBackup: () -> Unit,
    val onClearCache: () -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onDownloadUpdate: () -> Unit,
    val onRefreshPlaybackHealth: () -> Unit,
    val onLogPlaybackHealth: () -> Unit,
    val onResetSettings: () -> Unit,
    val onResetApp: () -> Unit,
)

internal fun buildSettingsOptionRows(
    category: SettingsCategory,
    ctx: SettingsRowsContext,
): List<SettingsRowModel> = when (category) {
    SettingsCategory.Account -> buildAccountRows(ctx)
    SettingsCategory.Guide -> buildGuideRows(ctx)
    SettingsCategory.Playback -> buildPlaybackRows(ctx)
    SettingsCategory.Appearance -> buildAppearanceRows(ctx)
    SettingsCategory.Parental -> buildParentalRows(ctx)
    SettingsCategory.About -> buildAboutRows(ctx)
}

private fun buildAccountRows(ctx: SettingsRowsContext): List<SettingsRowModel> = buildList {
    val profile = ctx.activeProfile
    add(
        SettingsRowModel.Info(
            id = "account.active",
            label = "Active profile",
            value = profile?.name ?: "No profile",
        )
    )
    add(
        SettingsRowModel.Action(
            id = "account.manage",
            label = "Manage profiles",
            subtitle = "Create, rename, or delete profiles",
            onClick = ctx.onManageProfiles,
        )
    )
    if (profile != null) {
        val colorLabels = ProfileAvatarColors.mapIndexed { index, _ -> "Color ${index + 1}" }
        val profileHex = sanitizeProfileAvatarColorHex(profile.avatarColor).uppercase()
        val selectedColorIndex = ProfileAvatarColors.indexOfFirst {
            colorToHex(it).uppercase() == profileHex
        }.coerceAtLeast(0)
        add(
            SettingsRowModel.Selection(
                id = "account.avatarColor",
                label = "Profile color",
                options = colorLabels,
                selectedIndex = selectedColorIndex.coerceIn(0, ProfileAvatarColors.lastIndex),
                onSelect = { index ->
                    ctx.onAvatarColorChange(colorToHex(ProfileAvatarColors[index]))
                },
            )
        )
    }
    ctx.profiles.forEach { p ->
        val active = p.id == profile?.id
        add(
            SettingsRowModel.Action(
                id = "account.profile.${p.id}",
                label = p.name,
                subtitle = when {
                    active -> "Current device profile"
                    p.isParental -> "Parental profile"
                    else -> "Switch profile"
                },
                value = if (active) "Active" else null,
                enabled = !active,
                onClick = { ctx.onSelectProfile(p.id) },
            )
        )
    }
    add(
        SettingsRowModel.Toggle(
            id = "account.untaggedVod",
            label = "Include untagged VOD",
            subtitle = "Show titles without a detectable language tag",
            checked = ctx.includeUntaggedVodContent,
            onToggle = ctx.onToggleIncludeUntaggedVodContent,
        )
    )
    addDividerInfo("Connections")
    if (ctx.showConnectionForm) {
        addAll(buildConnectionFormRows(ctx))
    } else {
        add(
            SettingsRowModel.Action(
                id = "account.addConnection",
                label = "Add connection",
                onClick = ctx.onStartNewConnection,
            )
        )
        if (ctx.playlists.isEmpty()) {
            add(
                SettingsRowModel.Info(
                    id = "account.noConnections",
                    label = "No connections yet",
                    value = "Add your first IPTV provider",
                )
            )
        }
        ctx.playlists.forEach { playlist ->
            add(
                SettingsRowModel.Action(
                    id = "account.connection.${playlist.id}",
                    label = playlist.name,
                    subtitle = playlistConnectionSubtitle(playlist),
                    value = "Edit",
                    onClick = { ctx.onSelectPlaylist(playlist.id) },
                )
            )
        }
        ctx.xtreamAccounts.forEach { account ->
            val exp = account.expiryDateEpochSec?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it * 1000L))
            } ?: "N/A"
            add(
                SettingsRowModel.Info(
                    id = "account.xtream.${account.playlistName}",
                    label = account.playlistName,
                    value = "${account.status} · expires $exp",
                )
            )
        }
    }
}

private fun MutableList<SettingsRowModel>.addDividerInfo(label: String) {
    add(SettingsRowModel.Info(id = "divider.$label", label = label, value = ""))
}

private fun buildConnectionFormRows(ctx: SettingsRowsContext): List<SettingsRowModel> = buildList {
    add(
        SettingsRowModel.TextInput(
            id = "connection.name",
            label = "Connection name",
            value = ctx.connectionName,
            onValueChange = ctx.onConnectionNameChange,
            placeholder = "e.g. Home IPTV",
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "connection.type",
            label = "Provider type",
            options = listOf("M3U", "Xtream"),
            selectedIndex = if (ctx.connectionPlaylistType == PlaylistType.XTREAM) 1 else 0,
            onSelect = { index ->
                ctx.onConnectionPlaylistTypeChange(
                    if (index == 0) PlaylistType.M3U else PlaylistType.XTREAM,
                )
            },
        )
    )
    if (ctx.connectionPlaylistType == PlaylistType.M3U) {
        add(
            SettingsRowModel.TextInput(
                id = "connection.m3uUrl",
                label = "M3U URL",
                value = ctx.connectionUrl,
                onValueChange = ctx.onConnectionUrlChange,
                placeholder = "https://provider.com/playlist.m3u",
            )
        )
    } else {
        add(
            SettingsRowModel.TextInput(
                id = "connection.xtreamServer",
                label = "Server URL",
                value = ctx.connectionXtreamServer,
                onValueChange = ctx.onConnectionXtreamServerChange,
                placeholder = "http://server:port",
            )
        )
        add(
            SettingsRowModel.TextInput(
                id = "connection.xtreamUser",
                label = "Username",
                value = ctx.connectionXtreamUser,
                onValueChange = ctx.onConnectionXtreamUserChange,
            )
        )
        add(
            SettingsRowModel.TextInput(
                id = "connection.xtreamPass",
                label = "Password",
                value = ctx.connectionXtreamPass,
                onValueChange = ctx.onConnectionXtreamPassChange,
                isPassword = true,
            )
        )
    }
    add(
        SettingsRowModel.TextInput(
            id = "connection.epgUrl",
            label = "EPG URL (optional)",
            value = ctx.connectionEpgUrl,
            onValueChange = ctx.onConnectionEpgUrlChange,
        )
    )
    add(
        SettingsRowModel.TextInput(
            id = "connection.refreshHours",
            label = "Refresh every (hours)",
            value = ctx.connectionRefreshHours,
            onValueChange = ctx.onConnectionRefreshHoursChange,
            placeholder = "24",
        )
    )
    val timeoutOptions = listOf(60, 120, 300, 600)
    val timeoutLabels = timeoutOptions.map { seconds ->
        when {
            seconds < 60 -> "${seconds}s"
            seconds % 60 == 0 -> "${seconds / 60}m"
            else -> "${seconds}s"
        }
    }
    add(
        SettingsRowModel.Selection(
            id = "connection.timeout",
            label = "Connection timeout",
            options = timeoutLabels,
            selectedIndex = timeoutOptions.indexOf(ctx.settings.connectionTimeoutSeconds).coerceAtLeast(0),
            onSelect = { index -> ctx.onConnectionTimeout(timeoutOptions[index]) },
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "connection.proxy",
            label = "Use proxy",
            subtitle = "Route playlist and stream requests through a proxy",
            checked = ctx.settings.useProxy,
            onToggle = ctx.onToggleUseProxy,
        )
    )
    if (ctx.settings.useProxy) {
        add(
            SettingsRowModel.TextInput(
                id = "connection.proxyUrl",
                label = "Proxy URL",
                value = ctx.settings.proxyUrl,
                onValueChange = ctx.onProxyUrlChange,
                placeholder = "http://proxy:8080",
            )
        )
    }
    val saveLabel = if (ctx.editingPlaylistId != null) "Save connection" else "Connect"
    add(
        SettingsRowModel.Action(
            id = "connection.save",
            label = saveLabel,
            enabled = !ctx.isConnecting,
            onClick = ctx.onSaveConnection,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "connection.localFile",
            label = "Local M3U file",
            onClick = ctx.onPickLocalFile,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "connection.tivimate",
            label = "Import TiviMate backup",
            onClick = ctx.onPickTiviMateZip,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "connection.cancel",
            label = "Cancel",
            onClick = ctx.onCancelConnectionForm,
        )
    )
    if (ctx.editingPlaylistId != null) {
        add(
            SettingsRowModel.Action(
                id = "connection.remove",
                label = "Remove connection",
                destructive = true,
                onClick = { ctx.onDeletePlaylist(ctx.editingPlaylistId) },
            )
        )
    }
    add(
        SettingsRowModel.Info(
            id = "connection.status",
            label = "Status",
            value = ctx.connectionProgress,
        )
    )
}

private fun buildGuideRows(ctx: SettingsRowsContext): List<SettingsRowModel> = buildList {
    add(
        SettingsRowModel.Action(
            id = "guide.refreshEpg",
            label = "Refresh EPG now",
            onClick = ctx.onRefreshEpg,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "guide.fixMissing",
            label = "Fix missing guide data",
            onClick = ctx.onOpenEpgResolver,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "guide.channelGroups",
            label = "Channel groups",
            subtitle = ctx.channelGroupSummary,
            value = "Edit",
            onClick = ctx.onEditChannelGroups,
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "guide.rowHeight",
            label = "Guide row height",
            options = EpgRowHeight.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selectedIndex = EpgRowHeight.entries.indexOf(ctx.settings.epgRowHeight).coerceAtLeast(0),
            onSelect = { index -> ctx.onRowHeight(EpgRowHeight.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "guide.autoScan",
            label = "Channel auto-scan",
            options = listOf("On", "Off"),
            selectedIndex = if (ctx.settings.autoScanEnabled) 0 else 1,
            onSelect = { index -> ctx.onToggleAutoScan(index == 0) },
        )
    )
    val scanIntervals = listOf(1, 2, 5, 10, 30)
    add(
        SettingsRowModel.Selection(
            id = "guide.scanInterval",
            label = "Scan interval",
            options = scanIntervals.map { "${it}m" },
            selectedIndex = scanIntervals.indexOf(ctx.settings.scanIntervalMinutes).coerceAtLeast(0),
            onSelect = { index -> ctx.onScanInterval(scanIntervals[index]) },
        )
    )
    val concurrentCounts = listOf(5, 10, 20, 50)
    add(
        SettingsRowModel.Selection(
            id = "guide.concurrentChecks",
            label = "Concurrent checks",
            options = concurrentCounts.map { it.toString() },
            selectedIndex = concurrentCounts.indexOf(ctx.settings.concurrentChecks).coerceAtLeast(0),
            onSelect = { index -> ctx.onConcurrentChecks(concurrentCounts[index]) },
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "guide.scanMetered",
            label = "Scan on metered connections",
            checked = ctx.settings.scanOnMetered,
            onToggle = { ctx.onToggleScanOnMetered(!ctx.settings.scanOnMetered) },
        )
    )
    add(
        SettingsRowModel.Action(
            id = "guide.scanNow",
            label = if (ctx.scannerRuntime.isScanning) "Scanning channels…" else "Scan channels now",
            enabled = !ctx.scannerRuntime.isScanning,
            onClick = ctx.onScanNow,
        )
    )
    val lastFullScanLabel = ctx.scannerRuntime.lastFullScanAt?.let { at ->
        val mins = ((ctx.now - at) / 60_000L).coerceAtLeast(0)
        when (mins) {
            0L -> "just now"
            1L -> "1 min ago"
            else -> "$mins mins ago"
        }
    } ?: "never"
    add(
        SettingsRowModel.Info(
            id = "guide.scanStatus",
            label = "Scanner status",
            value = "${ctx.scannerRuntime.liveCount}/${ctx.scannerRuntime.totalCount} live · last scan $lastFullScanLabel",
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "guide.sidebarEpg",
            label = "EPG info on sidebar",
            checked = ctx.settings.showEpgProgramInfoOnSidebar,
            onToggle = ctx.onToggleShowEpgSidebar,
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "guide.catchupStart",
            label = "Catch-up from beginning",
            checked = ctx.settings.startChannelFromBeginningOnCatchup,
            onToggle = ctx.onToggleCatchupFromBeginning,
        )
    )
}

private fun buildPlaybackRows(ctx: SettingsRowsContext): List<SettingsRowModel> = buildList {
    val retryOptions = listOf(2, 3, 5)
    add(
        SettingsRowModel.Selection(
            id = "playback.retries",
            label = "Stream retries",
            options = retryOptions.map { it.toString() },
            selectedIndex = retryOptions.indexOf(ctx.settings.streamRetries).coerceAtLeast(0),
            onSelect = { index -> ctx.onRetries(retryOptions[index]) },
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "playback.quality",
            label = "Default stream quality",
            options = listOf("Auto", "1080p", "720p", "480p"),
            selectedIndex = StreamQuality.entries.indexOf(ctx.settings.defaultStreamQuality).coerceAtLeast(0),
            onSelect = { index -> ctx.onDefaultQuality(StreamQuality.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "playback.buffer",
            label = "Buffer size",
            options = listOf("Low", "Medium", "High"),
            selectedIndex = BufferSize.entries.indexOf(ctx.settings.bufferSize).coerceAtLeast(0),
            onSelect = { index -> ctx.onBufferSize(BufferSize.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "playback.autoReconnect",
            label = "Auto-reconnect on drop",
            checked = ctx.settings.autoReconnectOnDrop,
            onToggle = ctx.onToggleAutoReconnect,
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "playback.hardwareDecode",
            label = "Prefer hardware decoding",
            checked = ctx.settings.preferHardwareDecoding,
            onToggle = ctx.onToggleHardwareDecoding,
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "playback.externalPlayer",
            label = "External player",
            options = ExternalPlayerId.entries.map { it.label },
            selectedIndex = ExternalPlayerId.entries.indexOf(ctx.externalPlayer).coerceAtLeast(0),
            onSelect = { index -> ctx.onExternalPlayerChange(ExternalPlayerId.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "playback.nextUp",
            label = "Auto-play next episode",
            checked = ctx.nextUpAutoPlay,
            onToggle = { ctx.onNextUpAutoPlayChange(!ctx.nextUpAutoPlay) },
        )
    )
    val syncOptions = listOf(6, 24, 72, 168)
    val syncLabels = syncOptions.map { hours ->
        when (hours) {
            6 -> "Every 6 hours"
            24 -> "Daily"
            72 -> "Every 3 days"
            168 -> "Weekly"
            else -> "Every ${hours}h"
        }
    }
    add(
        SettingsRowModel.Selection(
            id = "playback.vodSync",
            label = "VOD catalog sync",
            options = syncLabels,
            selectedIndex = syncOptions.indexOf(ctx.vodSyncIntervalHours).coerceAtLeast(0),
            onSelect = { index -> ctx.onSyncIntervalChange(syncOptions[index]) },
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "playback.aspectRatio",
            label = "Aspect ratio",
            options = listOf("Auto", "16:9", "4:3", "Stretch"),
            selectedIndex = AspectRatioSetting.entries.indexOf(ctx.settings.aspectRatio).coerceAtLeast(0),
            onSelect = { index -> ctx.onAspectRatio(AspectRatioSetting.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "playback.subtitles",
            label = "Subtitles",
            checked = ctx.settings.subtitlesEnabled,
            onToggle = ctx.onToggleSubtitles,
        )
    )
    add(
        SettingsRowModel.TextInput(
            id = "playback.subtitleLanguage",
            label = "Subtitle language",
            value = ctx.settings.subtitleLanguage,
            onValueChange = ctx.onSubtitleLanguage,
            placeholder = "en",
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "playback.subtitleSize",
            label = "Subtitle font size",
            options = listOf("Small", "Medium", "Large"),
            selectedIndex = SubtitleFontSize.entries.indexOf(ctx.settings.subtitleFontSize).coerceAtLeast(0),
            onSelect = { index -> ctx.onSubtitleFontSize(SubtitleFontSize.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "playback.subtitlePosition",
            label = "Subtitle position",
            options = listOf("Bottom", "Middle", "Top"),
            selectedIndex = SubtitlePosition.entries.indexOf(ctx.settings.subtitlePosition).coerceAtLeast(0),
            onSelect = { index -> ctx.onSubtitlePosition(SubtitlePosition.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Action(
            id = "playback.subtitleDelayDown",
            label = "Subtitle delay",
            value = "${ctx.settings.subtitleDelayMs}ms",
            subtitle = "Press OK to add 500ms",
            onClick = { ctx.onSubtitleDelayMs(ctx.settings.subtitleDelayMs + 500L) },
        )
    )
    add(
        SettingsRowModel.Action(
            id = "playback.subtitleDelayUp",
            label = "Reduce subtitle delay",
            value = "-500ms",
            onClick = { ctx.onSubtitleDelayMs(ctx.settings.subtitleDelayMs - 500L) },
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "playback.deinterlace",
            label = "Deinterlacing",
            checked = ctx.settings.deinterlacingEnabled,
            onToggle = ctx.onToggleDeinterlacing,
        )
    )
    add(
        SettingsRowModel.TextInput(
            id = "playback.audioLanguage",
            label = "Preferred audio language",
            value = ctx.settings.preferredAudioLanguage,
            onValueChange = ctx.onAudioLanguage,
            placeholder = "en",
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "playback.sleepAuto",
            label = "Auto sleep timer",
            subtitle = if (ctx.settings.sleepTimerAutoEnabled) {
                "Starts a ${ctx.settings.sleepTimerMinutes} min timer when you open the player"
            } else {
                "Off — set manually from the player menu"
            },
            checked = ctx.settings.sleepTimerAutoEnabled,
            onToggle = ctx.onToggleSleepTimerAuto,
        )
    )
    val sleepTimerOptions = listOf(15, 30, 45, 60, 90)
    add(
        SettingsRowModel.Selection(
            id = "playback.sleepDuration",
            label = "Sleep timer duration",
            options = sleepTimerOptions.map { "${it}m" },
            selectedIndex = sleepTimerOptions.indexOf(ctx.settings.sleepTimerMinutes).coerceAtLeast(0),
            onSelect = { index -> ctx.onSleepTimer(sleepTimerOptions[index]) },
        )
    )
    addDividerInfo("Recordings")
    if (ctx.usbStorageReady) {
        add(
            SettingsRowModel.Info(
                id = "playback.storageStatus",
                label = "Recording storage",
                value = ctx.usbStorageStatusLine ?: ctx.currentStorageLabel ?: "USB drive",
            )
        )
    } else {
        add(
            SettingsRowModel.Info(
                id = "playback.storageRequired",
                label = "Recording storage",
                value = "USB drive required",
            )
        )
    }
    ctx.storageOptions.forEach { option ->
        add(
            SettingsRowModel.Action(
                id = "playback.storage.${option.id}",
                label = option.label,
                subtitle = option.displayLine(),
                value = "Use",
                enabled = ctx.usbStorageReady,
                onClick = { ctx.onSelectStorage(option.id) },
            )
        )
    }
}

private fun buildAppearanceRows(ctx: SettingsRowsContext): List<SettingsRowModel> = buildList {
    val hideOptions = listOf(3, 5, 10, -1)
    val hideLabels = listOf("3s", "5s", "10s", "Never")
    add(
        SettingsRowModel.Selection(
            id = "appearance.sidebarHide",
            label = "Sidebar auto-hide",
            options = hideLabels,
            selectedIndex = hideOptions.indexOf(ctx.settings.sidebarAutoHideSeconds).coerceAtLeast(0),
            onSelect = { index -> ctx.onSidebarAutoHide(hideOptions[index]) },
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "appearance.channelNumbers",
            label = "Show channel numbers",
            checked = ctx.settings.showChannelNumbers,
            onToggle = ctx.onToggleShowChannelNumbers,
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "appearance.dpad",
            label = "D-pad sidebar sensitivity",
            options = listOf("Instant", "Normal", "Slow"),
            selectedIndex = DpadSensitivity.entries.indexOf(ctx.settings.dpadSidebarSensitivity).coerceAtLeast(0),
            onSelect = { index -> ctx.onDpadSensitivity(DpadSensitivity.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "appearance.clock",
            label = "Clock display",
            options = listOf("Off", "12-hour", "24-hour"),
            selectedIndex = ClockDisplay.entries.indexOf(ctx.settings.clockDisplay).coerceAtLeast(0),
            onSelect = { index -> ctx.onClockDisplay(ClockDisplay.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "appearance.theme",
            label = "Theme",
            options = AppThemeId.entries.map { it.displayName },
            selectedIndex = AppThemeId.entries.indexOf(ctx.settings.themeId).coerceAtLeast(0),
            onSelect = { index -> ctx.onTheme(AppThemeId.entries[index]) },
        )
    )
}

private fun buildParentalRows(ctx: SettingsRowsContext): List<SettingsRowModel> = buildList {
    add(
        SettingsRowModel.Toggle(
            id = "parental.hideAdult",
            label = "Hide adult categories",
            subtitle = if (ctx.activeProfile?.hasPin == true) {
                "PIN required to turn on"
            } else {
                "Blocks channels in adult groups"
            },
            checked = ctx.settings.hideAdultContent,
            onToggle = ctx.onToggleHideAdult,
        )
    )
    add(
        SettingsRowModel.Toggle(
            id = "parental.pinLock",
            label = "Parental PIN lock",
            subtitle = "Require PIN to switch profiles or open restricted content",
            checked = ctx.settings.parentalPinLockEnabled,
            onToggle = ctx.onToggleParentalPinLock,
        )
    )
    add(
        SettingsRowModel.Selection(
            id = "parental.maxRating",
            label = "Max content rating",
            options = listOf("All ages", "PG", "14+", "18+"),
            selectedIndex = MaxContentRating.entries.indexOf(ctx.settings.maxContentRating).coerceAtLeast(0),
            onSelect = { index -> ctx.onMaxContentRating(MaxContentRating.entries[index]) },
        )
    )
    add(
        SettingsRowModel.Action(
            id = "parental.changePin",
            label = if (ctx.activeProfile?.hasPin == true) "Change PIN" else "Set PIN",
            onClick = ctx.onChangePin,
        )
    )
}

private fun buildAboutRows(ctx: SettingsRowsContext): List<SettingsRowModel> = buildList {
    if (ctx.isSignedIn) {
        ctx.signedInEmail?.let { email ->
            add(SettingsRowModel.Info(id = "about.signedIn", label = "Signed in as", value = email))
        }
        add(
            SettingsRowModel.Action(
                id = "about.signOut",
                label = "Sign out",
                destructive = true,
                onClick = ctx.onSignOut,
            )
        )
    } else if (ctx.googleSignInAvailable) {
        add(
            SettingsRowModel.Action(
                id = "about.signIn",
                label = "Sign in with Google",
                subtitle = "Sync watch progress and settings across devices",
                onClick = ctx.onSignIn,
            )
        )
    } else {
        add(
            SettingsRowModel.Info(
                id = "about.cloudUnavailable",
                label = "Cloud sync",
                value = "Not configured for this build",
            )
        )
    }
    add(SettingsRowModel.Info(id = "about.version", label = "Version", value = ctx.appVersion))
    if (ctx.lowEndDeviceModeActive) {
        add(
            SettingsRowModel.Info(
                id = "about.lowEnd",
                label = "Low-end device mode",
                value = ctx.lowEndDeviceSummary ?: "Active",
            )
        )
    }
    add(
        SettingsRowModel.Action(
            id = "about.export",
            label = "Export .grid backup",
            onClick = ctx.onExportBackup,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "about.clearCache",
            label = "Clear cache",
            onClick = ctx.onClearCache,
        )
    )
    ctx.cacheMessage?.let {
        add(SettingsRowModel.Info(id = "about.cacheMessage", label = "Cache", value = it))
    }
    ctx.importSummary?.let {
        add(SettingsRowModel.Info(id = "about.import", label = "Import", value = it))
    }
    val updateStatus = when (ctx.updateUiState) {
        ManualUpdateUiState.Idle -> null
        ManualUpdateUiState.Checking -> "Checking for updates…"
        ManualUpdateUiState.UpToDate -> "Up to date"
        is ManualUpdateUiState.UpdateAvailable -> "Update: ${ctx.updateUiState.info.versionName}"
        is ManualUpdateUiState.Downloading -> "Downloading ${ctx.updateUiState.percent}%"
        is ManualUpdateUiState.Error -> ctx.updateUiState.message
    }
    updateStatus?.let {
        add(SettingsRowModel.Info(id = "about.updateStatus", label = "Updates", value = it))
    }
    add(
        SettingsRowModel.Action(
            id = "about.checkUpdates",
            label = if (ctx.updateUiState is ManualUpdateUiState.Checking) "Checking…" else "Check for updates",
            enabled = ctx.updateUiState !is ManualUpdateUiState.Checking &&
                ctx.updateUiState !is ManualUpdateUiState.Downloading,
            onClick = ctx.onCheckForUpdates,
        )
    )
    if (ctx.updateUiState is ManualUpdateUiState.UpdateAvailable) {
        add(
            SettingsRowModel.Action(
                id = "about.downloadUpdate",
                label = "Update now",
                onClick = ctx.onDownloadUpdate,
            )
        )
    }
    ctx.playbackHealthSummary.diagnosticsMessage?.let {
        add(SettingsRowModel.Info(id = "about.playbackHealth", label = "Playback health", value = it))
    }
    if (ctx.playbackHealthSummary.problemChannels.isNotEmpty()) {
        add(
            SettingsRowModel.Info(
                id = "about.unstableChannels",
                label = "Unstable channels",
                value = ctx.playbackHealthSummary.problemChannels.joinToString(),
            )
        )
    }
    add(
        SettingsRowModel.Action(
            id = "about.refreshHealth",
            label = "Refresh playback health",
            onClick = ctx.onRefreshPlaybackHealth,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "about.logHealth",
            label = "Log diagnostics to logcat",
            onClick = ctx.onLogPlaybackHealth,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "about.resetSettings",
            label = "Reset all settings",
            subtitle = "Keeps profiles and connections",
            destructive = true,
            onClick = ctx.onResetSettings,
        )
    )
    add(
        SettingsRowModel.Action(
            id = "about.resetApp",
            label = "Reset everything",
            subtitle = "Deletes all data and restarts fresh",
            destructive = true,
            onClick = ctx.onResetApp,
        )
    )
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

internal fun channelGroupSummary(settings: AppSettings): String = when {
    settings.guideChannelGroups.isEmpty() -> "All channel groups"
    settings.guideChannelGroups.size == 1 -> settings.guideChannelGroups.first()
    else -> "${settings.guideChannelGroups.size} groups selected"
}
