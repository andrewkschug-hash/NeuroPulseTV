package com.grid.tv.feature.recording

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ActiveRecordingSession @Inject constructor() {
    private val _health = MutableStateFlow(RecordingHealth.IDLE)
    val health: StateFlow<RecordingHealth> = _health.asStateFlow()

    fun setHealth(health: RecordingHealth) {
        _health.value = health
    }

    fun clear() {
        _health.value = RecordingHealth.IDLE
    }
}
