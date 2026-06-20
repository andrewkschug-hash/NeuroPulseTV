package com.grid.tv.data.network.parser

import android.util.Log
import com.grid.tv.data.db.entity.ProgramEntity
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

class XmlTvParser {

    private val formatWithTimezone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = UTC
    }
    /** IPTV providers (Xtream xmltv.php) send timezone-less timestamps as UTC wall clock. */
    private val formatUtc = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = UTC
    }

    fun parse(xml: String): ParsedXmlTv =
        parse(xml.byteInputStream(Charsets.UTF_8), Charsets.UTF_8.name())

    /** Parses XMLTV from an in-memory or file-backed stream. */
    fun parse(input: InputStream, encoding: String = Charsets.UTF_8.name()): ParsedXmlTv {
        input.use { stream ->
            val parser = newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(stream, encoding)
            }
            return parseDocument(parser)
        }
    }

    /**
     * Parses XMLTV from a disk cache file written during EPG download.
     * Handles gzip Content-Encoding, `.gz` URLs, and gzip magic bytes in the file header.
     */
    fun parseFile(
        file: File,
        contentEncoding: String? = null,
        sourceUrl: String? = null
    ): ParsedXmlTv {
        require(file.exists()) { "EPG cache file does not exist: ${file.absolutePath}" }
        require(file.length() > 0L) { "EPG cache file is empty: ${file.absolutePath}" }
        FileInputStream(file).use { fileStream ->
            openDecompressedStream(BufferedInputStream(fileStream), contentEncoding, sourceUrl).use { decoded ->
                val parser = newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(decoded, Charsets.UTF_8.name())
                }
                return parseDocument(parser)
            }
        }
    }

    private fun parseDocument(parser: XmlPullParser): ParsedXmlTv {
        val channelMap = mutableMapOf<String, String>()
        val programs = mutableListOf<ProgramEntity>()

        var event = parser.eventType
        var currentTag = ""
        var channelId = ""
        var channelDisplay = ""

        var pChannel = ""
        var pTitle = ""
        var pDesc = ""
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
                        }

                        "programme" -> {
                            pChannel = parser.getAttributeValue(null, "channel") ?: ""
                            pStart = parseTime(parser.getAttributeValue(null, "start"))
                            pEnd = parseTime(parser.getAttributeValue(null, "stop"))
                            pTitle = ""
                            pDesc = ""
                            pGenre = "GENERAL"
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    when (currentTag) {
                        "display-name" -> if (channelDisplay.isBlank()) channelDisplay = parser.text.trim()
                        "title" -> pTitle = parser.text.trim()
                        "desc" -> pDesc = parser.text.trim()
                        "category" -> pGenre = normalizeGenre(parser.text)
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> if (channelId.isNotBlank()) {
                            channelMap[channelId] = channelDisplay.ifBlank { channelId }
                        }
                        "programme" -> {
                            if (pChannel.isNotBlank() && pEnd > pStart) {
                                programs += ProgramEntity(
                                    id = stableProgramId(pChannel, pStart),
                                    channelEpgId = pChannel,
                                    title = pTitle.ifBlank { "Untitled" },
                                    description = pDesc,
                                    startTime = pStart,
                                    endTime = pEnd,
                                    genre = pGenre
                                )
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }

        return ParsedXmlTv(channelMap, programs)
    }

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
            runCatching { formatWithTimezone.parse(normalized)?.time }.getOrNull() ?: 0L
        } else {
            val datePart = normalized.take(14)
            runCatching { formatUtc.parse(datePart)?.time }.getOrNull() ?: 0L
        }
    }

    companion object {
        private const val TAG = "EpgFlow"
        private const val GZIP_MAGIC_0: Byte = 0x1f
        private const val GZIP_MAGIC_1: Byte = 0x8b.toByte()
        private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

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
        fun stableProgramId(channelEpgId: String, startTime: Long): Long {
            var hash = channelEpgId.lowercase().hashCode().toLong()
            hash = 31L * hash + startTime
            return hash and Long.MAX_VALUE
        }

        private fun newPullParser(): XmlPullParser = KXmlParser()

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
