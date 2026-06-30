package com.grid.tv.domain.model

enum class EpgRowHeight { COMPACT, NORMAL, LARGE }

enum class MaxContentRating { ALL_AGES, PG, TEEN_14, ADULT_18 }

enum class StreamQuality { AUTO, P1080, P720, P480 }

enum class BufferSize { LOW, MEDIUM, HIGH }

enum class AspectRatioSetting { AUTO, RATIO_16_9, RATIO_4_3, STRETCH }

enum class SubtitleFontSize { SMALL, MEDIUM, LARGE }

enum class SubtitlePosition { BOTTOM, MIDDLE, TOP }

enum class DpadSensitivity { INSTANT, NORMAL, SLOW }

enum class ClockDisplay { OFF, HOUR_12, HOUR_24 }

enum class RecordQuality { ORIGINAL, P720, P480 }

data class AppSettings(
    val streamRetries: Int = 3,
    val preferredAudioLanguage: String = "en",
    val epgRowHeight: EpgRowHeight = EpgRowHeight.NORMAL,
    val miniPlayerAudioEnabled: Boolean = true,
    val pinProtectedGroups: Set<String> = emptySet(),
    val sleepTimerMinutes: Int = 30,
    val hideAdultContent: Boolean = true,
    val sleepTimerAutoEnabled: Boolean = false,
    val autoScanEnabled: Boolean = true,
    val scanIntervalMinutes: Int = 5,
    val concurrentChecks: Int = 10,
    val scanOnMetered: Boolean = false,
    val preferredSearchInput: SearchInputMode = SearchInputMode.KEYBOARD,
    val parentalPinLockEnabled: Boolean = false,
    val maxContentRating: MaxContentRating = MaxContentRating.ALL_AGES,
    val connectionTimeoutSeconds: Int = 300,
    val useProxy: Boolean = false,
    val proxyUrl: String = "",
    val showEpgProgramInfoOnSidebar: Boolean = true,
    val startChannelFromBeginningOnCatchup: Boolean = false,
    val defaultStreamQuality: StreamQuality = StreamQuality.AUTO,
    val bufferSize: BufferSize = BufferSize.MEDIUM,
    val autoReconnectOnDrop: Boolean = true,
    val preferHardwareDecoding: Boolean = true,
    val aspectRatio: AspectRatioSetting = AspectRatioSetting.AUTO,
    val subtitlesEnabled: Boolean = true,
    val subtitleLanguage: String = "en",
    val subtitleFontSize: SubtitleFontSize = SubtitleFontSize.MEDIUM,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
    val subtitleDelayMs: Long = 0L,
    val deinterlacingEnabled: Boolean = false,
    val miniPlayerEnabled: Boolean = true,
    val sidebarAutoHideSeconds: Int = 5,
    val showChannelNumbers: Boolean = true,
    val dpadSidebarSensitivity: DpadSensitivity = DpadSensitivity.NORMAL,
    val clockDisplay: ClockDisplay = ClockDisplay.OFF,
    val recordQuality: RecordQuality = RecordQuality.ORIGINAL,
    val recordedPlaybackSpeed: Float = 1f,
    val themeId: AppThemeId = AppThemeId.NEURO_BLUE,
    val pictureInPictureEnabled: Boolean = true,
    /** Xtream/provider live groups shown in the guide; empty = all channels. */
    val guideChannelGroups: Set<String> = emptySet(),
    val guideFiltersConfigured: Boolean = false,
    val channelGroupNavigationMode: ChannelGroupNavigationMode = ChannelGroupNavigationMode.SMART,
)
