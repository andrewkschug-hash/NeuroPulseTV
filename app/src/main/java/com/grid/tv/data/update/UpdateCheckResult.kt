package com.grid.tv.data.update

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    data class NoReleasePublished(val httpCode: Int) : UpdateCheckResult()
    data class Failed(val reason: String, val httpCode: Int? = null) : UpdateCheckResult()
}
