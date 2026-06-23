package com.grid.tv.data.update

/** GitHub release asset naming rules for sideload/update download URLs. */
object ReleaseDownloadAssets {

    /** True when the asset is a raw APK or a zip wrapping an APK (e.g. `app.apk.zip`). */
    fun isInstallableAsset(fileName: String): Boolean {
        val lower = fileName.trim().lowercase()
        return lower.endsWith(".apk") || lower.endsWith(".apk.zip")
    }
}
