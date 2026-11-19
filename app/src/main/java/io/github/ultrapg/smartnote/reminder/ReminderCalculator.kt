package io.github.ultrapg.smartnote.reminder

import io.github.ultrapg.smartnote.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object ReminderCalculator {

    private val isoLocalFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val sameYearDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
    private val otherYearDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val time24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun dayMaskBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isDaySelected(mask: Int, day: DayOfWeek): Boolean = (mask and dayMaskBit(day)) != 0

    fun toggleDay(mask: Int, day: DayOfWeek): Int = mask xor dayMaskBit(day)

    fun selectedDays(mask: Int): List<DayOfWeek> =
        DayOfWeek.entries.filter { isDaySelected(mask, it) }

    fun computeTriggerEpochMs(
        localDateTimeIso: String,
        zoneId: String
    ): Long {
        val local = LocalDateTime.parse(localDateTimeIso, isoLocalFormatter)
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        return local.atZone(zone).toInstant().toEpochMilli()
    }

    fun formatLocalIso(epochMs: Long, zoneId: String): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        return Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime().format(isoLocalFormatter)
    }

    fun recomputeForCurrentZone(
        localDateTimeIso: String,
        newZoneId: String
    ): Long {
        val local = runCatching { LocalDateTime.parse(localDateTimeIso, isoLocalFormatter) }
            .getOrNull() ?: return System.currentTimeMillis()
        val zone = runCatching { ZoneId.of(newZoneId) }.getOrDefault(ZoneId.systemDefault())
        return local.atZone(zone).toInstant().toEpochMilli()
    }

    fun computeNextOccurrence(
        currentTriggerMs: Long,
        zoneId: String,
        repeatRule: RepeatRule,
        nowMs: Long = System.currentTimeMillis(),
        repeatDaysMask: Int = 0,
        repeatInterval: Int = 1
    ): Pair<Long, String>? {
        if (repeatRule == RepeatRule.NONE) return null
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        var candidate = Instant.ofEpochMilli(currentTriggerMs).atZone(zone)
        val interval = repeatInterval.coerceAtLeast(1).toLong()

        var guard = 0
        do {
            candidate = advanceOnce(candidate, repeatRule, repeatDaysMask, interval)
            guard++
        } while (candidate.toInstant().toEpochMilli() <= nowMs && guard < 1000)

        val nextMs = candidate.toInstant().toEpochMilli()
        val nextIso = candidate.toLocalDateTime().format(isoLocalFormatter)
        return nextMs to nextIso
    }

    fun computeSnoozeTriggerMs(nowMs: Long, snoozeMinutes: Int): Long {
        return nowMs + snoozeMinutes.coerceIn(1, 1440) * 60_000L
    }

    fun formatRelativeCountdown(
        targetMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val diffMs = targetMs - nowMs
        if (diffMs < 0L) {
            val overdueMins = (-diffMs) / 60_000L
            return when {
                overdueMins < 1L -> "Due now"
                overdueMins < 60L -> "Overdue by ${overdueMins}m"
                overdueMins < 1440L -> "Overdue by ${overdueMins / 60L}h"
                else -> "Overdue by ${overdueMins / 1440L}d"
            }
        }
        val mins = diffMs / 60_000L
        return when {
            mins <= 1L -> "In under a minute"
            mins < 60L -> "In $mins min"
            mins < 24L * 60L -> {
                val hours = mins / 60L
                val remMins = mins % 60L
                if (remMins == 0L) "In ${hours}h" else "In ${hours}h ${remMins}m"
            }
            else -> {
                val days = mins / 1440L
                val remHours = (mins % 1440L) / 60L
                when {
                    remHours > 0L && days < 7L -> "In ${days}d ${remHours}h"
                    days == 1L -> "In 1 day"
                    else -> "In $days days"
                }
            }
        }
    }

    fun formatHumanDate(
        date: LocalDate,
        today: LocalDate = LocalDate.now()
    ): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date.year == today.year -> date.format(sameYearDateFormatter)
        else -> date.format(otherYearDateFormatter)
    }

    fun formatHumanDateTime(
        epochMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val dt = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()
        return "${formatHumanDate(dt.toLocalDate(), today)}, ${dt.format(time24Formatter)}"
    }

    fun formatRepeatSummary(
        rule: RepeatRule,
        repeatDaysMask: Int = 0,
        repeatInterval: Int = 1
    ): String {
        val interval = repeatInterval.coerceAtLeast(1)
        return when (rule) {
            RepeatRule.NONE -> "Once"
            RepeatRule.DAILY -> if (interval == 1) "Daily" else "Every $interval days"
            RepeatRule.WEEKDAYS -> "Weekdays (Mon–Fri)"
            RepeatRule.WEEKLY -> if (interval == 1) "Weekly" else "Every $interval weeks"
            RepeatRule.MONTHLY -> if (interval == 1) "Monthly" else "Every $interval months"
            RepeatRule.YEARLY -> "Yearly"
            RepeatRule.CUSTOM_DAYS -> {
                val days = selectedDays(repeatDaysMask)
                if (days.isEmpty()) {
                    "Custom days"
                } else {
                    days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
                }
            }
        }
    }

    fun formatPreAlertLabel(preAlertMinutes: Int): String = when {
        preAlertMinutes <= 0 -> "At time"
        preAlertMinutes == 60 -> "1h before"
        preAlertMinutes == 1440 -> "1d before"
        preAlertMinutes % 60 == 0 && preAlertMinutes < 1440 -> "${preAlertMinutes / 60}h before"
        else -> "${preAlertMinutes}m before"
    }

    fun isSameLocalDay(
        epochMs: Long,
        referenceMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val targetDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val refDate = Instant.ofEpochMilli(referenceMs).atZone(zone).toLocalDate()
        return targetDate == refDate
    }

    fun startOfReminderLocalDayMs(
        triggerAtMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = Instant.ofEpochMilli(triggerAtMs).atZone(zone).toLocalDate()
        return date.atTime(0, 1, 0).atZone(zone).toInstant().toEpochMilli()
    }

    private fun advanceOnce(
        zdt: ZonedDateTime,
        rule: RepeatRule,
        repeatDaysMask: Int,
        interval: Long
    ): ZonedDateTime = when (rule) {
        RepeatRule.NONE -> zdt
        RepeatRule.DAILY -> zdt.plusDays(interval)
        RepeatRule.WEEKLY -> zdt.plusWeeks(interval)
        RepeatRule.MONTHLY -> zdt.plusMonths(interval)
        RepeatRule.YEARLY -> zdt.plusYears(interval)
        RepeatRule.WEEKDAYS -> {
            var next = zdt.plusDays(1)
            while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                next = next.plusDays(1)
            }
            next
        }
        RepeatRule.CUSTOM_DAYS -> {
            val effectiveMask = (repeatDaysMask and 0x7F).let { m ->
                if (m == 0) dayMaskBit(zdt.dayOfWeek) else m
            }
            var next = zdt.plusDays(1)
            var guard = 0
            while (!isDaySelected(effectiveMask, next.dayOfWeek) && guard < 14) {
                next = next.plusDays(1)
                guard++
            }
            next
        }
    }
}
