package com.grid.tv.data.network.parser

import android.util.Log
import com.grid.tv.data.db.entity.ProgramEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import com.grid.tv.domain.epg.EpgTime
import java.util.Locale
import java.util.zip.GZIPInputStream

class XmlTvParser {

    fun parse(xml: String, playlistId: Long = 0L): ParsedXmlTv =
        parse(xml.byteInputStream(Charsets.UTF_8), Charsets.UTF_8.name(), playlistId)

    /** Parses XMLTV from an in-memory or file-backed stream. */
    fun parse(input: InputStream, encoding: String = Charsets.UTF_8.name(), playlistId: Long = 0L): ParsedXmlTv {
        input.use { stream ->
            val parser = newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(stream, encoding)
            }
            return parseDocument(parser, playlistId)
        }
    }

    /**
     * Parses XMLTV from a disk cache file written during EPG download.
     * Handles gzip Content-Encoding, `.gz` URLs, and gzip magic bytes in the file header.
     */
    fun parseFile(
        file: File,
        contentEncoding: String? = null,
        sourceUrl: String? = null,
        playlistId: Long = 0L
    ): ParsedXmlTv {
        require(file.exists()) { "EPG cache file does not exist: ${file.absolutePath}" }
        require(file.length() > 0L) { "EPG cache file is empty: ${file.absolutePath}" }
        FileInputStream(file).use { fileStream ->
            openDecompressedStream(BufferedInputStream(fileStream), contentEncoding, sourceUrl).use { decoded ->
                val parser = newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(decoded, Charsets.UTF_8.name())
                }
                return parseDocument(parser, playlistId)
            }
        }
    }

    /**
     * Streams programme rows to [onProgramBatch] during parse so large EPG files are not held in memory.
     */
    suspend fun parseFileBatched(
        file: File,
        contentEncoding: String? = null,
        sourceUrl: String? = null,
        playlistId: Long = 0L,
        batchSize: Int = PROGRAM_BATCH_SIZE,
        onProgramBatch: suspend (List<ProgramEntity>) -> Unit,
    ): ParsedXmlTv {
        require(file.exists()) { "EPG cache file does not exist: ${file.absolutePath}" }
        require(file.length() > 0L) { "EPG cache file is empty: ${file.absolutePath}" }
        FileInputStream(file).use { fileStream ->
            openDecompressedStream(BufferedInputStream(fileStream), contentEncoding, sourceUrl).use { decoded ->
                val parser = newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(decoded, Charsets.UTF_8.name())
                }
                return parseDocumentBatched(parser, playlistId, batchSize, onProgramBatch)
            }
        }
    }

    private fun parseDocument(parser: XmlPullParser, playlistId: Long): ParsedXmlTv {
        val channelMap = mutableMapOf<String, String>()
        val programs = mutableListOf<ProgramEntity>()
        parseDocumentLoop(parser, playlistId, channelMap) { programs += it }
        return ParsedXmlTv(channelMap, programs, programs.size)
    }

    private suspend fun parseDocumentBatched(
        parser: XmlPullParser,
        playlistId: Long,
        batchSize: Int,
        onProgramBatch: suspend (List<ProgramEntity>) -> Unit,
    ): ParsedXmlTv {
        val channelMap = mutableMapOf<String, String>()
        val batch = ArrayList<ProgramEntity>(batchSize)
        var programCount = 0
        var event = parser.eventType
        var currentTag = ""
        var channelId = ""
        var channelDisplay = ""
        val channelDisplayBuffer = StringBuilder()

        var pChannel = ""
        val pTitle = StringBuilder()
        val pDesc = StringBuilder()
        var pStart = 0L
        var pEnd = 0L
        var pGenre = "GENERAL"

        suspend fun flushBatch() {
            if (batch.isEmpty()) return
            onProgramBatch(batch.toList())
            batch.clear()
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "channel" -> {
                            channelId = parser.getAttributeValue(null, "id") ?: ""
                            channelDisplay = ""
                            channelDisplayBuffer.setLength(0)
                        }

                        "programme" -> {
                            pChannel = parser.getAttributeValue(null, "channel") ?: ""
                            pStart = parseTime(parser.getAttributeValue(null, "start"))
                            pEnd = parseTime(parser.getAttributeValue(null, "stop"))
                            pTitle.setLength(0)
                            pDesc.setLength(0)
                            pGenre = "GENERAL"
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    when (currentTag) {
                        "display-name" -> {
                            if (channelDisplay.isBlank()) appendXmlText(channelDisplayBuffer, parser.text)
                        }
                        "title" -> appendXmlText(pTitle, parser.text)
                        "desc" -> appendXmlText(pDesc, parser.text)
                        "category" -> {
                            val normalized = normalizeGenre(parser.text)
                            if (pGenre == "GENERAL" && normalized != "GENERAL") {
                                pGenre = normalized
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "display-name" -> {
                            if (channelDisplay.isBlank()) {
                                channelDisplay = normalizeXmlText(channelDisplayBuffer)
                            }
                        }
                        "channel" -> if (channelId.isNotBlank()) {
                            channelMap[channelId] = channelDisplay.ifBlank { channelId }
                        }
                        "programme" -> {
                            if (pChannel.isNotBlank() && pEnd > pStart) {
                                programCount++
                                batch += ProgramEntity(
                                    id = stableProgramId(playlistId, pChannel, pStart),
                                    playlistId = playlistId,
                                    channelEpgId = pChannel,
                                    title = normalizeXmlText(pTitle).ifBlank { "Untitled" },
                                    description = normalizeXmlText(pDesc),
                                    startTime = pStart,
                                    endTime = pEnd,
                                    genre = pGenre
                                )
                                if (batch.size >= batchSize) {
                                    flushBatch()
                                }
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }

        flushBatch()
        return ParsedXmlTv(channelMap, emptyList(), programCount)
    }

    private inline fun parseDocumentLoop(
        parser: XmlPullParser,
        playlistId: Long,
        channelMap: MutableMap<String, String>,
        onProgram: (ProgramEntity) -> Unit,
    ) {
        var event = parser.eventType
        var currentTag = ""
        var channelId = ""
        var channelDisplay = ""
        val channelDisplayBuffer = StringBuilder()

        var pChannel = ""
        val pTitle = StringBuilder()
        val pDesc = StringBuilder()
        var pStart = 0L
        var pEnd = 0L
        var pGenre = "GENERAL"

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "channel" -> {
                            channelId = parser.getAttributeValue(null, "id") ?: ""
                            channelDisplay = ""
                            channelDisplayBuffer.setLength(0)
                        }

                        "programme" -> {
                            pChannel = parser.getAttributeValue(null, "channel") ?: ""
                            pStart = parseTime(parser.getAttributeValue(null, "start"))
                            pEnd = parseTime(parser.getAttributeValue(null, "stop"))
                            pTitle.setLength(0)
                            pDesc.setLength(0)
                            pGenre = "GENERAL"
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    when (currentTag) {
                        "display-name" -> {
                            if (channelDisplay.isBlank()) appendXmlText(channelDisplayBuffer, parser.text)
                        }
                        "title" -> appendXmlText(pTitle, parser.text)
                        "desc" -> appendXmlText(pDesc, parser.text)
                        "category" -> {
                            val normalized = normalizeGenre(parser.text)
                            if (pGenre == "GENERAL" && normalized != "GENERAL") {
                                pGenre = normalized
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "display-name" -> {
                            if (channelDisplay.isBlank()) {
                                channelDisplay = normalizeXmlText(channelDisplayBuffer)
                            }
                        }
                        "channel" -> if (channelId.isNotBlank()) {
                            channelMap[channelId] = channelDisplay.ifBlank { channelId }
                        }
                        "programme" -> {
                            if (pChannel.isNotBlank() && pEnd > pStart) {
                                onProgram(
                                    ProgramEntity(
                                        id = stableProgramId(playlistId, pChannel, pStart),
                                        playlistId = playlistId,
                                        channelEpgId = pChannel,
                                        title = normalizeXmlText(pTitle).ifBlank { "Untitled" },
                                        description = normalizeXmlText(pDesc),
                                        startTime = pStart,
                                        endTime = pEnd,
                                        genre = pGenre
                                    )
                                )
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }
    }

    private fun appendXmlText(target: StringBuilder, raw: String?) {
        val value = raw.orEmpty()
        if (value.isEmpty()) return
        if (target.isNotEmpty() && !target.last().isWhitespace() && !value.first().isWhitespace()) {
            target.append(' ')
        }
        target.append(value)
    }

    private fun normalizeXmlText(value: StringBuilder): String =
        value.toString().replace(Regex("\\s+"), " ").trim()

    private fun normalizeGenre(value: String): String {
        val lower = value.lowercase(Locale.US)
        return when {
            "news" in lower -> "NEWS"
            "sport" in lower -> "SPORTS"
            "movie" in lower || "film" in lower -> "MOVIES"
            "kids" in lower || "children" in lower -> "KIDS"
            else -> "GENERAL"
        }
    }

    private fun parseTime(input: String?): Long {
        if (input.isNullOrBlank()) return 0L
        val normalized = normalizeXmlTvTimestamp(input.trim())
        val hasExplicitTimezone = normalized.length > 14 &&
            (normalized[14] == ' ' || normalized[14] == '+' || normalized[14] == '-')
        return if (hasExplicitTimezone) {
            EpgTime.parseXmlTvWithOffset(normalized)
        } else {
            EpgTime.parseXmlTvLocalWallClock(normalized)
        }
    }

    companion object {
        private const val TAG = "EpgFlow"
        private const val PROGRAM_BATCH_SIZE = 500
        private const val GZIP_MAGIC_0: Byte = 0x1f
        private const val GZIP_MAGIC_1: Byte = 0x8b.toByte()

        /**
         * XMLTV timestamps vary by provider: `20240615120000 +0000`, `20240615120000+0000`,
         * `20240615120000+00:00`, `...Z`.
         */
        internal fun normalizeXmlTvTimestamp(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.endsWith('Z', ignoreCase = true) && trimmed.length >= 15) {
                return trimmed.dropLast(1).trimEnd() + " +0000"
            }
            Regex("""^(\d{14})([\+\-]\d{2}):?(\d{2})$""").matchEntire(trimmed)?.let { match ->
                return "${match.groupValues[1]} ${match.groupValues[2]}${match.groupValues[3]}"
            }
            Regex("""^(\d{14})([\+\-]\d{4})$""").matchEntire(trimmed)?.let { match ->
                return "${match.groupValues[1]} ${match.groupValues[2]}"
            }
            return trimmed
        }

        /** Stable primary key so EPG refresh upserts instead of duplicating rows. */
        fun stableProgramId(playlistId: Long, channelEpgId: String, startTime: Long): Long {
            var hash = playlistId
            hash = 31L * hash + channelEpgId.lowercase().hashCode().toLong()
            hash = 31L * hash + startTime
            return hash and Long.MAX_VALUE
        }

        @Deprecated("Use stableProgramId(playlistId, channelEpgId, startTime)")
        fun stableProgramId(channelEpgId: String, startTime: Long): Long =
            stableProgramId(playlistId = 0L, channelEpgId = channelEpgId, startTime = startTime)

        private fun newPullParser(): XmlPullParser =
            XmlPullParserFactory.newInstance().newPullParser()

        internal fun openDecompressedStream(
            stream: InputStream,
            contentEncoding: String?,
            url: String?
        ): InputStream {
            if (contentEncoding?.contains("gzip", ignoreCase = true) == true ||
                url?.endsWith(".gz", ignoreCase = true) == true ||
                url?.contains(".xml.gz", ignoreCase = true) == true
            ) {
                Log.i(TAG, "EPG cache file for $url is gzip-encoded — decompressing from disk")
                return GZIPInputStream(BufferedInputStream(stream))
            }
            val buffered = if (stream is BufferedInputStream) stream else BufferedInputStream(stream)
            buffered.mark(2)
            val first = buffered.read()
            val second = buffered.read()
            buffered.reset()
            return if (first == GZIP_MAGIC_0.toInt() && second == GZIP_MAGIC_1.toInt()) {
                Log.i(TAG, "EPG cache file for $url has gzip magic — decompressing from disk")
                GZIPInputStream(buffered)
            } else {
                buffered
            }
        }
    }
}
