package com.grid.tv.data.network.parser

import android.util.Xml
import com.grid.tv.data.db.entity.ProgramEntity
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class XmlTvParser {

    private val formatWithTimezone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val formatLocal = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun parse(xml: String): ParsedXmlTv {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xml.reader())
        }

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
                        "channel" -> if (channelId.isNotBlank()) channelMap[channelId] = channelDisplay.ifBlank { channelId }
                        "programme" -> {
                            if (pChannel.isNotBlank() && pEnd > pStart) {
                                programs += ProgramEntity(
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
        val trimmed = input.trim()
        val hasExplicitTimezone = trimmed.length > 14 &&
            (trimmed[14] == ' ' || trimmed[14] == '+' || trimmed[14] == '-')
        return if (hasExplicitTimezone) {
            runCatching { formatWithTimezone.parse(trimmed)?.time }.getOrNull() ?: 0L
        } else {
            val datePart = trimmed.take(14)
            runCatching { formatLocal.parse(datePart)?.time }.getOrNull() ?: 0L
        }
    }
}
