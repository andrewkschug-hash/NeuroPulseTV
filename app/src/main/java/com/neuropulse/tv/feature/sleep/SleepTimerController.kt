package com.neuropulse.tv.feature.sleep

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class SleepTimerController @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _remainingSec = MutableStateFlow(0)
    val remainingSec: StateFlow<Int> = _remainingSec.asStateFlow()

    private var tickJob: Job? = null
    var onExpired: (() -> Unit)? = null
    var onVolumeFade: ((Float) -> Unit)? = null

    fun isActive(): Boolean = _remainingSec.value > 0

    fun start(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        _remainingSec.value = minutes * 60
        startTicker()
    }

    fun cancel() {
        tickJob?.cancel()
        _remainingSec.value = 0
        onVolumeFade?.invoke(1f)
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (_remainingSec.value > 0) {
                delay(1000)
                val next = (_remainingSec.value - 1).coerceAtLeast(0)
                _remainingSec.value = next
                if (next in 1..60) {
                    onVolumeFade?.invoke(next / 60f)
                }
                if (next == 0) {
                    onExpired?.invoke()
                }
            }
        }
    }

    fun formatCountdown(): String {
        val sec = _remainingSec.value
        if (sec <= 0) return ""
        val m = sec / 60
        val s = sec % 60
        return String.format("%d:%02d", m, s)
    }
}
