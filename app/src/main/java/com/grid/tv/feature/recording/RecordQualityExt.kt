package com.grid.tv.feature.recording

import com.grid.tv.domain.model.RecordQuality

val RecordQuality.label: String
    get() = when (this) {
        RecordQuality.ORIGINAL -> "Original"
        RecordQuality.P720 -> "720p"
        RecordQuality.P480 -> "480p"
    }

val RecordQuality.bitrateBps: Int
    get() = when (this) {
        RecordQuality.ORIGINAL -> 4_900_000
        RecordQuality.P720 -> 2_570_000
        RecordQuality.P480 -> 1_170_000
    }

val RecordQuality.gbPerHour: Double
    get() = when (this) {
        RecordQuality.ORIGINAL -> 2.1
        RecordQuality.P720 -> 1.1
        RecordQuality.P480 -> 0.5
    }
