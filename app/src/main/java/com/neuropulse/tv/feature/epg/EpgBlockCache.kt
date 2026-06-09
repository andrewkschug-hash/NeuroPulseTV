package com.neuropulse.tv.feature.epg

import com.neuropulse.tv.domain.model.Program
import java.util.LinkedHashMap

class EpgBlockCache(private val maxBlocks: Int = 6) {
    private val map = object : LinkedHashMap<String, List<Program>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Program>>?): Boolean {
            return size > maxBlocks
        }
    }

    fun get(key: String): List<Program>? = map[key]

    fun put(key: String, programs: List<Program>) {
        map[key] = programs
    }

    fun clear() {
        map.clear()
    }
}
