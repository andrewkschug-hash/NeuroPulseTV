package com.grid.tv.feature.parental

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.UserProfile
import java.util.Calendar

object ProfileAccessGuard {
    private val adultPatterns = listOf("adult", "18+", "xxx", "mature", "erotic", "porn", "adults only")

    fun isAdultGroup(groupName: String): Boolean {
        val lower = groupName.lowercase()
        return adultPatterns.any { lower.contains(it) }
    }

    fun shouldHideChannel(profile: UserProfile, channel: Channel, hideAdultContent: Boolean = true): Boolean {
        if (!hideAdultContent) return false
        return isAdultGroup(channel.group)
    }

    fun isWithinAllowedHours(profile: UserProfile, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!profile.isParental) return true
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = profile.allowedStartMinutes
        val end = profile.allowedEndMinutes
        return if (start <= end) {
            minutes in start..end
        } else {
            minutes >= start || minutes <= end
        }
    }

    fun outsideHoursMessage(profile: UserProfile): String {
        val startH = profile.allowedStartMinutes / 60
        val startM = profile.allowedStartMinutes % 60
        val endH = profile.allowedEndMinutes / 60
        val endM = profile.allowedEndMinutes % 60
        return "This profile is only available ${startH}:${"%02d".format(startM)} – ${endH}:${"%02d".format(endM)}"
    }
}
