package com.neuropulse.tv.domain.model

enum class EpgRowHeight { COMPACT, NORMAL, LARGE }

data class AppSettings(
    val streamRetries: Int = 3,
    val preferredAudioLanguage: String = "en",
    val epgRowHeight: EpgRowHeight = EpgRowHeight.NORMAL,
    val miniPlayerAudioEnabled: Boolean = false,
    val pinProtectedGroups: Set<String> = emptySet(),
    val sleepTimerMinutes: Int = 30,
    val hideAdultContent: Boolean = true,
    val sleepTimerAutoEnabled: Boolean = false,
    val autoScanEnabled: Boolean = true,
    val scanIntervalMinutes: Int = 5,
    val concurrentChecks: Int = 10,
    val scanOnMetered: Boolean = false,
    val preferredSearchInput: SearchInputMode = SearchInputMode.KEYBOARD
)
