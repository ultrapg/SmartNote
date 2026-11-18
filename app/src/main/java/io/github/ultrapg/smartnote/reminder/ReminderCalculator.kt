package io.github.ultrapg.smartnote.reminder

import io.github.ultrapg.smartnote.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ReminderCalculator {

    private val isoLocalFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

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
        nowMs: Long = System.currentTimeMillis()
    ): Pair<Long, String>? {
        if (repeatRule == RepeatRule.NONE) return null
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        var candidate = Instant.ofEpochMilli(currentTriggerMs).atZone(zone)

        // Advance at least once, and keep advancing if still in the past
        var guard = 0
        do {
            candidate = advanceOnce(candidate, repeatRule)
            guard++
        } while (candidate.toInstant().toEpochMilli() <= nowMs && guard < 1000)

        val nextMs = candidate.toInstant().toEpochMilli()
        val nextIso = candidate.toLocalDateTime().format(isoLocalFormatter)
        return nextMs to nextIso
    }

    fun computeSnoozeTriggerMs(nowMs: Long, snoozeMinutes: Int): Long {
        return nowMs + snoozeMinutes.coerceIn(1, 120) * 60_000L
    }

    private fun advanceOnce(zdt: ZonedDateTime, rule: RepeatRule): ZonedDateTime = when (rule) {
        RepeatRule.NONE -> zdt
        RepeatRule.DAILY -> zdt.plusDays(1)
        RepeatRule.WEEKLY -> zdt.plusWeeks(1)
        RepeatRule.MONTHLY -> zdt.plusMonths(1)
        RepeatRule.WEEKDAYS -> {
            var next = zdt.plusDays(1)
            while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                next = next.plusDays(1)
            }
            next
        }
    }
}
