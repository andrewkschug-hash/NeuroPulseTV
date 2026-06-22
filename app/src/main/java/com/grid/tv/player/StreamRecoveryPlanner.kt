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
        maxAttempts: Int,
        blockedUrls: Set<String> = emptySet(),
        urlAvailable: (String) -> Boolean = { it !in blockedUrls }
    ): List<Step> {
        if (streamUrls.isEmpty() || maxAttempts <= 0) return emptyList()

        val steps = mutableListOf<Step>()
        val active = activeUrl?.trim().orEmpty()

        if (active.isNotBlank() && urlAvailable(active)) {
            steps += Step.Reconnect
        }

        streamUrls
            .map { it.trim() }
            .filter { it.isNotBlank() && it != active && urlAvailable(it) }
            .forEach { url -> steps += Step.SwitchUrl(url) }

        return steps.take(maxAttempts)
    }

    fun reorderWithActiveFirst(streamUrls: List<String>, activeUrl: String): List<String> {
        val active = activeUrl.trim()
        if (active.isBlank()) return streamUrls
        return buildList {
            add(active)
            streamUrls.forEach { url ->
                val trimmed = url.trim()
                if (trimmed.isNotBlank() && trimmed != active) add(trimmed)
            }
        }
    }
}
