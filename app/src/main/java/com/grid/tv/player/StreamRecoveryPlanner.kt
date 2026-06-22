package com.grid.tv.player

/**
 * Pure recovery-step planning for stream failover. [maxAttempts] comes from [AppSettings.streamRetries].
 */
internal object StreamRecoveryPlanner {

    sealed interface Step {
        data object Reconnect : Step
        data class SwitchUrl(val url: String) : Step
    }

    fun buildSteps(
        streamUrls: List<String>,
        activeUrl: String?,
        maxAttempts: Int
    ): List<Step> {
        if (streamUrls.isEmpty() || maxAttempts <= 0) return emptyList()
        val allSteps = mutableListOf<Step>(Step.Reconnect)
        val tried = mutableSetOf<String>()
        activeUrl?.let { tried.add(it) }
        streamUrls.forEach { url ->
            if (tried.add(url)) {
                allSteps += Step.SwitchUrl(url)
            }
        }
        return allSteps.take(maxAttempts)
    }
}
