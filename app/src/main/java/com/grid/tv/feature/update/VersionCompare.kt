package com.grid.tv.feature.update

/**
 * Compares dotted version strings (e.g. 2.1.0 vs v2.2.0). Non-numeric suffixes are stripped per segment.
 */
object VersionCompare {

    fun normalize(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")

    fun isRemoteNewer(remoteTag: String, localVersion: String): Boolean {
        val remote = parseParts(normalize(remoteTag))
        val local = parseParts(normalize(localVersion))
        val max = maxOf(remote.size, local.size)
        for (i in 0 until max) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun parseParts(version: String): List<Int> =
        version.split('.')
            .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
