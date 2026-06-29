package com.grid.tv.feature.startup

/**
 * Summary of what [MemoryPressureHandler] released during a trim pass.
 * Item counts approximate retained catalog wall items; heap delta is JVM heap only.
 */
data class MemoryTrimResult(
    val trimLevel: Int,
    val coilMemoryCacheBytesCleared: Int,
    val catalogWallItemsDropped: Int,
    val catalogBrowseRowsDropped: Int,
    val hubWasActive: Boolean,
    val activeContentFilter: String,
    val heapUsedBytesBefore: Long,
    val heapUsedBytesAfter: Long,
) {
    val heapFreedKb: Long
        get() = ((heapUsedBytesBefore - heapUsedBytesAfter).coerceAtLeast(0L)) / 1024

    val coilFreedKb: Int get() = coilMemoryCacheBytesCleared / 1024
}
