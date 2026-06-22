package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.util.PerformanceAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    data class BuildResult(
        val index: ProgrammeIndex,
        val cache: Cache?,
        val elapsedMs: Long,
        val skipped: Boolean,
        val incrementalChannels: Boolean
    )

    data class Cache(
        val fingerprint: Fingerprint,
        val channelIds: List<Long>,
        val byRawKey: Map<String, List<Program>>,
        val byNormalizedKey: Map<String, List<Program>>,
        val byChannelId: Map<Long, List<Program>>
    )

    data class Fingerprint(
        val channelCount: Int,
        val channelIdHash: Long,
        val programCount: Int,
        val programIdXor: Long
    ) {
        companion object {
            fun of(channels: List<Channel>, programs: List<Program>): Fingerprint {
                var channelHash = 0L
                for (channel in channels) {
                    channelHash = channelHash * 31L + channel.id
                }
                var programXor = 0L
                for (program in programs) {
                    programXor = programXor xor program.id
                }
                return Fingerprint(
                    channelCount = channels.size,
                    channelIdHash = channelHash,
                    programCount = programs.size,
                    programIdXor = programXor
                )
            }
        }
    }

    companion object {
        val EMPTY = ProgrammeIndex(emptyMap(), totalProgramCount = 0, channelCount = 0)

        suspend fun buildAsync(channels: List<Channel>, programs: List<Program>): ProgrammeIndex =
            withContext(Dispatchers.Default) {
                buildWithCache(null, channels, programs).index
            }

        fun buildWithCache(
            previous: Cache?,
            channels: List<Channel>,
            programs: List<Program>
        ): BuildResult {
            if (channels.isEmpty()) {
                return BuildResult(EMPTY, null, 0L, skipped = false, incrementalChannels = false)
            }
            val startNs = if (PerformanceAudit.ENABLED) System.nanoTime() else 0L
            val fingerprint = Fingerprint.of(channels, programs)

            if (previous != null && previous.fingerprint == fingerprint) {
                val index = ProgrammeIndex(
                    byChannelId = previous.byChannelId,
                    totalProgramCount = programs.size,
                    channelCount = channels.size
                )
                return BuildResult(
                    index = index,
                    cache = previous,
                    elapsedMs = elapsedMsSince(startNs),
                    skipped = true,
                    incrementalChannels = false
                )
            }

            val incremental = previous?.let { cache ->
                buildIncrementalChannelAppend(cache, channels, programs, fingerprint)
            }
            if (incremental != null) {
                logBuild(channels.size, programs.size, startNs)
                return incremental
            }

            val byRawKey = HashMap<String, MutableList<Program>>()
            val byNormalizedKey = HashMap<String, MutableList<Program>>()
            indexProgramsIntoLookupMaps(programs, byRawKey, byNormalizedKey)

            val indexed = HashMap<Long, List<Program>>(channels.size)
            for (channel in channels) {
                indexed[channel.id] = programsForChannelKeys(
                    keys = channel.programmeLookupKeys(),
                    byRawKey = byRawKey,
                    byNormalizedKey = byNormalizedKey
                )
            }

            val cache = Cache(
                fingerprint = fingerprint,
                channelIds = channels.map { it.id },
                byRawKey = byRawKey,
                byNormalizedKey = byNormalizedKey,
                byChannelId = indexed
            )
            val index = ProgrammeIndex(
                byChannelId = indexed,
                totalProgramCount = programs.size,
                channelCount = channels.size
            )
            logBuild(channels.size, programs.size, startNs)
            return BuildResult(
                index = index,
                cache = cache,
                elapsedMs = elapsedMsSince(startNs),
                skipped = false,
                incrementalChannels = false
            )
        }

        fun build(channels: List<Channel>, programs: List<Program>): ProgrammeIndex =
            buildWithCache(null, channels, programs).index

        private fun buildIncrementalChannelAppend(
            previous: Cache,
            channels: List<Channel>,
            programs: List<Program>,
            fingerprint: Fingerprint
        ): BuildResult? {
            if (previous.fingerprint.programCount != fingerprint.programCount) return null
            if (previous.fingerprint.programIdXor != fingerprint.programIdXor) return null
            if (channels.size <= previous.channelIds.size) return null
            if (channels.size - previous.channelIds.size > 256) return null
            val prefixMatches = channels.take(previous.channelIds.size).map { it.id } == previous.channelIds
            if (!prefixMatches) return null

            val indexed = HashMap(previous.byChannelId)
            for (channel in channels.drop(previous.channelIds.size)) {
                indexed[channel.id] = programsForChannelKeys(
                    keys = channel.programmeLookupKeys(),
                    byRawKey = previous.byRawKey,
                    byNormalizedKey = previous.byNormalizedKey
                )
            }
            val cache = previous.copy(
                fingerprint = fingerprint,
                channelIds = channels.map { it.id },
                byChannelId = indexed
            )
            val index = ProgrammeIndex(
                byChannelId = indexed,
                totalProgramCount = programs.size,
                channelCount = channels.size
            )
            return BuildResult(
                index = index,
                cache = cache,
                elapsedMs = 0L,
                skipped = false,
                incrementalChannels = true
            )
        }

        private fun indexProgramsIntoLookupMaps(
            programs: List<Program>,
            byRawKey: HashMap<String, MutableList<Program>>,
            byNormalizedKey: HashMap<String, MutableList<Program>>
        ) {
            for (program in programs) {
                val raw = program.channelEpgId
                if (raw.isEmpty()) continue
                byRawKey.getOrPut(raw.lowercase()) { ArrayList() }.add(program)
                val normalized = EpgIdNormalizer.normalize(raw)
                if (normalized.isNotEmpty()) {
                    byNormalizedKey.getOrPut(normalized) { ArrayList() }.add(program)
                }
            }
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

        private fun logBuild(channelCount: Int, programCount: Int, startNs: Long) {
            if (!PerformanceAudit.ENABLED) return
            PerformanceAudit.logProgrammeIndexBuild(
                channelCount = channelCount,
                programCount = programCount,
                elapsedMs = elapsedMsSince(startNs)
            )
        }

        private fun elapsedMsSince(startNs: Long): Long =
            if (startNs == 0L) 0L else (System.nanoTime() - startNs) / 1_000_000
    }
}
