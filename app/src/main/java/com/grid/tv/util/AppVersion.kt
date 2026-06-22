package com.grid.tv.util

/**
 * Version helpers shared by runtime and release tooling.
 * [semverToVersionCode] must stay in sync with the copy in app/build.gradle.kts.
 */
object AppVersion {

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
