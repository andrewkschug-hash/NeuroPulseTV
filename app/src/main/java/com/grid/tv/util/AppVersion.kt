package com.grid.tv.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.grid.tv.BuildConfig

/**
 * Version helpers shared by runtime and release tooling.
 * [semverToVersionCode] must stay in sync with the copy in app/build.gradle.kts.
 */
object AppVersion {

    /** Version string from the installed APK (matches Settings → About after an update). */
    fun installedVersionName(context: Context): String =
        runCatching {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            info.versionName?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull() ?: BuildConfig.VERSION_NAME

    /**
     * Maps semver to a monotonic Android versionCode.
     * Uses weighted parts (major×10000 + minor×100 + patch), not naive concatenation,
     * so 2.10.0 (21000) correctly exceeds 2.2.0 (20200).
     */
    fun semverToVersionCode(versionName: String): Int {
        val normalized = versionName.trim().removePrefix("v").removePrefix("V")
        val parts = normalized.split(".", "-", "_")
        val major = parts.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        return major * 10_000 + minor * 100 + patch
    }
}
