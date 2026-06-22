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
    return ProgrammeIndex.build(listOf(channel), programs).programsFor(channel.id)
}
