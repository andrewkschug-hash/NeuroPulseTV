package com.grid.tv.data.update

data class AppUpdateInfo(
    val versionName: String,
    val releaseNotes: String?,
    val downloadUrl: String
)
