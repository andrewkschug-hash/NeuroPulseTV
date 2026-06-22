package com.grid.tv.util.cache

data class CacheStatistics(
    val name: String,
    val size: Int,
    val maxEntries: Int,
    val estimatedBytes: Long,
    val maxBytes: Long,
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val puts: Long
) {
    val hitRatePercent: Int
        get() {
            val total = hits + misses
            return if (total == 0L) 0 else ((hits * 100) / total).toInt()
        }

    fun toLogLine(): String =
        "CACHE name=$name size=$size/$maxEntries bytes=$estimatedBytes/$maxBytes " +
            "hits=$hits misses=$misses hitRate=${hitRatePercent}% evictions=$evictions puts=$puts"
}
