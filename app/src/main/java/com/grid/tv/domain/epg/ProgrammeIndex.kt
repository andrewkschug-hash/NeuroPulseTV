package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.util.PerformanceAudit

/**
 * Pre-indexed EPG programmes keyed by [Channel.id].
 * Programs are matched and sorted once at index build time; lookups are O(1) map reads.
 */
class ProgrammeIndex private constructor(
    private val byChannelId: Map<Long, List<Program>>,
    val totalProgramCount: Int,
    val channelCount: Int
) {
    fun programsFor(channelId: Long): List<Program> = byChannelId[channelId] ?: emptyList()

    fun programsFor(channel: Channel): List<Program> = programsFor(channel.id)

    companion object {
        val EMPTY = ProgrammeIndex(emptyMap(), totalProgramCount = 0, channelCount = 0)

        fun build(channels: List<Channel>, programs: List<Program>): ProgrammeIndex {
            if (channels.isEmpty()) return EMPTY
            val startNs = if (PerformanceAudit.ENABLED) System.nanoTime() else 0L

            val byRawKey = HashMap<String, MutableList<Program>>()
            val byNormalizedKey = HashMap<String, MutableList<Program>>()
            for (program in programs) {
                val raw = program.channelEpgId
                if (raw.isEmpty()) continue
                byRawKey.getOrPut(raw.lowercase()) { ArrayList() }.add(program)
                val normalized = EpgIdNormalizer.normalize(raw)
                if (normalized.isNotEmpty()) {
                    byNormalizedKey.getOrPut(normalized) { ArrayList() }.add(program)
                }
            }

            val indexed = HashMap<Long, List<Program>>(channels.size)
            for (channel in channels) {
                indexed[channel.id] = programsForChannelKeys(
                    keys = channel.programmeLookupKeys(),
                    byRawKey = byRawKey,
                    byNormalizedKey = byNormalizedKey
                )
            }

            val index = ProgrammeIndex(
                byChannelId = indexed,
                totalProgramCount = programs.size,
                channelCount = channels.size
            )

            if (PerformanceAudit.ENABLED) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                PerformanceAudit.logProgrammeIndexBuild(
                    channelCount = channels.size,
                    programCount = programs.size,
                    elapsedMs = elapsedMs
                )
            }
            return index
        }

        private fun programsForChannelKeys(
            keys: List<String>,
            byRawKey: Map<String, List<Program>>,
            byNormalizedKey: Map<String, List<Program>>
        ): List<Program> {
            if (keys.isEmpty()) return emptyList()
            val normalizedKeys = keys.map { EpgIdNormalizer.normalize(it) }.filter { it.isNotEmpty() }.toSet()
            val merged = LinkedHashMap<Long, Program>()
            for (key in keys) {
                byRawKey[key.lowercase()]?.forEach { program -> merged.putIfAbsent(program.id, program) }
            }
            for (norm in normalizedKeys) {
                byNormalizedKey[norm]?.forEach { program -> merged.putIfAbsent(program.id, program) }
            }
            return merged.values.sortedBy { it.startTime }
        }
    }
}
