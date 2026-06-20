package com.grid.tv.domain.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program

/** Keys used to join playlist channels to XMLTV programme rows. */
fun Channel.programmeLookupKeys(): List<String> = buildList {
    epgId?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    val name = name.trim()
    if (name.isNotEmpty() && !contains(name)) add(name)
}

fun programmesForChannel(channel: Channel, programs: List<Program>): List<Program> {
    val keys = channel.programmeLookupKeys()
    if (keys.isEmpty()) return emptyList()
    val normalizedKeys = keys.map { EpgIdNormalizer.normalize(it) }.filter { it.isNotEmpty() }.toSet()
    return programs
        .filter { prog ->
            keys.any { key -> prog.channelEpgId.equals(key, ignoreCase = true) } ||
                normalizedKeys.any { norm -> EpgIdNormalizer.normalize(prog.channelEpgId) == norm }
        }
        .sortedBy { it.startTime }
}
