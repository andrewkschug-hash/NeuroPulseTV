package com.grid.tv.data.cache

import com.grid.tv.data.db.entity.PlaylistEntity
import java.security.MessageDigest

/**
 * Per-playlist VOD cache metadata keyed by playlist credentials (server + username).
 * Content fingerprints are SHA-256 hashes of the raw provider catalog JSON file.
 */
data class VodPlaylistCacheManifest(
    val playlistId: Long,
    val playlistVersionKey: String,
    val savedAtMs: Long,
    val moviesCount: Int,
    val seriesCount: Int,
    val moviesContentFingerprint: String?,
    val seriesContentFingerprint: String?,
) {
    fun isFresh(nowMs: Long = System.currentTimeMillis(), ttlMs: Long): Boolean =
        savedAtMs > 0L && nowMs - savedAtMs < ttlMs
}

fun vodPlaylistCacheVersionKey(playlist: PlaylistEntity): String {
    val server = playlist.xtreamServerUrl?.trim().orEmpty().ifBlank { playlist.url.trim() }
    val user = playlist.xtreamUsername?.trim().orEmpty()
    return sha256Hex("$server|$user|${playlist.id}")
}

fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

fun sha256FileHex(file: java.io.File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
