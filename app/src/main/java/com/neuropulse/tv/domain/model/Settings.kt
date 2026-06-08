package com.neuropulse.tv.domain.model

enum class EpgRowHeight { COMPACT, NORMAL, LARGE }

data class AppSettings(
    val streamRetries: Int = 3,
    val preferredAudioLanguage: String = "en",
    val epgRowHeight: EpgRowHeight = EpgRowHeight.NORMAL,
    val miniPlayerAudioEnabled: Boolean = false,
    val pinProtectedGroups: Set<String> = emptySet(),
    val sleepTimerMinutes: Int = 30
)
