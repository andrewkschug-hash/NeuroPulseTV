package com.grid.tv.player

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Cached stream-format hints from probes and content-type HEAD checks. */
@Singleton
class IptvStreamFormatRegistry @Inject constructor() {

    private data class Entry(
        val format: IptvStreamFormat,
        val source: Source,
        val recordedAtMs: Long = System.currentTimeMillis()
    )

    enum class Source {
        METADATA,
        CONTENT_TYPE,
        MANIFEST_SNIFF,
        URL_PATTERN
    }

    private val byUrl = ConcurrentHashMap<String, Entry>()

    fun get(url: String): IptvStreamFormat? = byUrl[normalize(url)]?.format

    fun put(url: String, format: IptvStreamFormat, source: Source) {
        if (url.isBlank()) return
        byUrl[normalize(url)] = Entry(format = format, source = source)
    }

    fun remove(url: String) {
        if (url.isBlank()) return
        byUrl.remove(normalize(url))
    }

    fun putContentType(url: String, contentType: String?) {
        val format = StreamTypeDetector.classify(url, contentType, firstBytes = null).format
        if (format != IptvStreamFormat.UNKNOWN) {
            put(url, format, Source.CONTENT_TYPE)
        }
    }

    fun putManifestSnippet(url: String, snippet: String) {
        val format = StreamTypeDetector.classify(url, contentType = null, firstBytes = snippet).format
        if (format != IptvStreamFormat.UNKNOWN) {
            put(url, format, Source.MANIFEST_SNIFF)
        }
    }

    fun clear() {
        byUrl.clear()
    }

    private fun normalize(url: String): String = url.trim()
}
