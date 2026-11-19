package io.github.ultrapg.smartnote.reminder

import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class DetectedReminderSuggestion(
    val dateTime: LocalDateTime,
    val matchedPhrase: String,
    val suggestedRepeat: RepeatRule = RepeatRule.NONE
)

object NaturalDateTimeParser {

    private val relativeMinutesRegex = Regex(
        """\bin\s+(\d{1,3})\s*(?:min|mins|minute|minutes|minute|minuten)\b""",
        RegexOption.IGNORE_CASE
    )
    private val relativeHoursRegex = Regex(
        """\bin\s+(\d{1,2})\s*(?:h|hr|hrs|hour|hours|stunde|stunden)\b""",
        RegexOption.IGNORE_CASE
    )
    private val isoDateRegex = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
    private val euDateRegex = Regex("""\b(\d{1,2})\.(\d{1,2})\.(20\d{2})?(?!\d)""")
    private val namedMonthDateRegex = Regex(
        """\b(?:(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\s+(\d{1,2})|(\d{1,2})\s+(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december))\b""",
        RegexOption.IGNORE_CASE
    )
    private val namedDayRegex = Regex(
        """\b(uebermorgen|übermorgen|tomorrow|morgen|tonight|today|heute abend|heute|next week|naechste woche|nächste woche|monday|tuesday|wednesday|thursday|friday|saturday|sunday|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)\b""",
        RegexOption.IGNORE_CASE
    )
    private val timeWithContextRegex = Regex(
        """(?:^|[\s,(])((?:at|um|@)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*(?:uhr)?|(\d{1,2}):(\d{2})\s*(am|pm)?\s*(?:uhr)?|(\d{1,2})\s*(am|pm|uhr))\b""",
        RegexOption.IGNORE_CASE
    )

    private val repeatPatterns: List<Pair<Regex, RepeatRule>> = listOf(
        Regex("""\b(every weekday|weekdays|werktags|jeden werktag)\b""", RegexOption.IGNORE_CASE) to RepeatRule.WEEKDAYS,
        Regex("""\b(every day|daily|jeden tag|taeglich|täglich)\b""", RegexOption.IGNORE_CASE) to RepeatRule.DAILY,
        Regex("""\b(every week|weekly|jede woche|woechentlich|wöchentlich)\b""", RegexOption.IGNORE_CASE) to RepeatRule.WEEKLY,
        Regex("""\b(every month|monthly|jeden monat|monatlich)\b""", RegexOption.IGNORE_CASE) to RepeatRule.MONTHLY,
        Regex("""\b(every year|yearly|annually|jedes jahr|jaehrlich|jährlich)\b""", RegexOption.IGNORE_CASE) to RepeatRule.YEARLY
    )

    fun extractFromNote(
        title: String,
        blocks: List<NoteBlock>,
        now: LocalDateTime = LocalDateTime.now()
    ): DetectedReminderSuggestion? {
        val text = buildString {
            append(title.trim()).append('\n')
            blocks
                .filter { it.type == NoteBlockType.TEXT || it.type == NoteBlockType.CHECKLIST }
                .forEach { append(it.textContent.trim()).append('\n') }
        }.trim()
        if (text.isBlank()) return null

        val suggestedRepeat = repeatPatterns
            .firstOrNull { (regex, _) -> regex.containsMatchIn(text) }
            ?.second ?: RepeatRule.NONE

        // 1. Relative durations ("in 20 min", "in 2 hours", "in 3 Stunden")
        relativeMinutesRegex.find(text)?.let { match ->
            val mins = match.groupValues[1].toLongOrNull()
            if (mins != null && mins in 2..1440) {
                val target = now.plusMinutes(mins).withSecond(0).withNano(0)
                if (target.isAfter(now.plusMinutes(1))) {
                    return DetectedReminderSuggestion(target, match.value.trim(), suggestedRepeat)
                }
            }
        }
        relativeHoursRegex.find(text)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull()
            if (hours != null && hours in 1..72) {
                val target = now.plusHours(hours).withSecond(0).withNano(0)
                if (target.isAfter(now.plusMinutes(1))) {
                    return DetectedReminderSuggestion(target, match.value.trim(), suggestedRepeat)
                }
            }
        }

        val today = now.toLocalDate()

        // 2. Explicit dates (ISO / EU dot / Month Name)
        val explicitDateMatch: Pair<LocalDate, MatchResult>? =
            parseIsoDateMatch(text)
                ?: parseEuDateMatch(text, today)
                ?: parseNamedMonthDateMatch(text, today)

        if (explicitDateMatch != null) {
            val (date, dateMatch) = explicitDateMatch
            val nearbyTime = findTime(text, excludeRange = dateMatch.range)
            val resolvedTime = nearbyTime?.first ?: LocalTime.of(9, 0)
            val candidate = LocalDateTime.of(date, resolvedTime)
            if (candidate.isAfter(now.plusMinutes(1))) {
                val phrase = listOfNotNull(dateMatch.value.trim(), nearbyTime?.second?.trim())
                    .joinToString(" ")
                return DetectedReminderSuggestion(candidate, phrase, suggestedRepeat)
            }
        }

        // 3. Named day phrases ("today", "tonight", "tomorrow", "morgen", weekdays, "next week")
        namedDayRegex.find(text)?.let { dayMatch ->
            val kw = dayMatch.groupValues[1].lowercase(Locale.ROOT)
            val nearbyTime = findTime(text, excludeRange = null)
            val defaultTime = when (kw) {
                "tonight", "heute abend" -> LocalTime.of(20, 0)
                "today", "heute" -> now.plusHours(1).withMinute(0).toLocalTime()
                else -> LocalTime.of(9, 0)
            }
            val resolvedTime = nearbyTime?.first ?: defaultTime
            val targetDate = when (kw) {
                "today", "heute", "tonight", "heute abend" -> today
                "tomorrow", "morgen" -> today.plusDays(1)
                "uebermorgen", "übermorgen" -> today.plusDays(2)
                "next week", "naechste woche", "nächste woche" -> today.plusWeeks(1)
                else -> {
                    val dow = parseWeekday(kw)
                    if (dow != null) {
                        if (today.dayOfWeek == dow && LocalDateTime.of(today, resolvedTime).isAfter(now.plusMinutes(1))) {
                            today
                        } else {
                            today.with(TemporalAdjusters.next(dow))
                        }
                    } else {
                        today
                    }
                }
            }
            val candidate = LocalDateTime.of(targetDate, resolvedTime)
            if (candidate.isAfter(now.plusMinutes(1))) {
                val phrase = if (nearbyTime != null && !nearbyTime.second.contains(dayMatch.value, ignoreCase = true)) {
                    "${dayMatch.value.trim()} ${nearbyTime.second.trim()}"
                } else {
                    dayMatch.value.trim()
                }
                return DetectedReminderSuggestion(candidate, phrase, suggestedRepeat)
            }
        }

        // 4. Standalone explicit time ("at 15:30", "um 18:00", "9:30 am")
        findTime(text, excludeRange = null)?.let { (time, timePhrase) ->
            val todayCandidate = LocalDateTime.of(today, time)
            val target = if (todayCandidate.isAfter(now.plusMinutes(1))) {
                todayCandidate
            } else {
                LocalDateTime.of(today.plusDays(1), time)
            }
            return DetectedReminderSuggestion(target, timePhrase.trim(), suggestedRepeat)
        }

        return null
    }

    private fun parseIsoDateMatch(text: String): Pair<LocalDate, MatchResult>? {
        val m = isoDateRegex.find(text) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val d = m.groupValues[3].toIntOrNull() ?: return null
        val date = runCatching { LocalDate.of(y, mo, d) }.getOrNull() ?: return null
        return date to m
    }

    private fun parseEuDateMatch(text: String, today: LocalDate): Pair<LocalDate, MatchResult>? {
        val m = euDateRegex.find(text) ?: return null
        val d = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val explicitYear = m.groupValues[3].toIntOrNull()
        val candidateYear = explicitYear ?: today.year
        val parsed = runCatching { LocalDate.of(candidateYear, mo, d) }.getOrNull() ?: return null
        val resolved = if (explicitYear == null && parsed.isBefore(today)) {
            runCatching { LocalDate.of(today.year + 1, mo, d) }.getOrDefault(parsed)
        } else {
            parsed
        }
        return resolved to m
    }

    private fun parseNamedMonthDateMatch(text: String, today: LocalDate): Pair<LocalDate, MatchResult>? {
        val m = namedMonthDateRegex.find(text) ?: return null
        val monthStr = m.groupValues[1].ifBlank { m.groupValues[4] }
        val dayStr = m.groupValues[2].ifBlank { m.groupValues[3] }
        val month = parseMonth(monthStr) ?: return null
        val day = dayStr.toIntOrNull() ?: return null
        val parsed = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        val resolved = if (parsed.isBefore(today)) {
            runCatching { LocalDate.of(today.year + 1, month, day) }.getOrDefault(parsed)
        } else {
            parsed
        }
        return resolved to m
    }

    private fun findTime(text: String, excludeRange: IntRange?): Pair<LocalTime, String>? {
        for (match in timeWithContextRegex.findAll(text)) {
            if (excludeRange != null &&
                match.range.first <= excludeRange.last &&
                match.range.last >= excludeRange.first
            ) {
                continue
            }
            val rawHour: Int?
            val rawMin: Int
            val amPm: String
            if (match.groupValues[2].isNotBlank()) {
                rawHour = match.groupValues[2].toIntOrNull()
                rawMin = match.groupValues[3].toIntOrNull() ?: 0
                amPm = match.groupValues[4].lowercase(Locale.ROOT)
            } else if (match.groupValues[5].isNotBlank()) {
                rawHour = match.groupValues[5].toIntOrNull()
                rawMin = match.groupValues[6].toIntOrNull() ?: 0
                amPm = match.groupValues[7].lowercase(Locale.ROOT)
            } else {
                rawHour = match.groupValues[8].toIntOrNull()
                rawMin = 0
                amPm = match.groupValues[9].lowercase(Locale.ROOT)
            }
            if (rawHour == null || rawMin !in 0..59) continue
            val hour24 = when {
                amPm == "pm" && rawHour in 1..11 -> rawHour + 12
                amPm == "am" && rawHour == 12 -> 0
                amPm in setOf("am", "pm") && rawHour !in 1..12 -> continue
                rawHour in 0..23 -> rawHour
                else -> continue
            }
            return LocalTime.of(hour24, rawMin) to match.groupValues[1]
        }
        return null
    }

    private fun parseWeekday(token: String): DayOfWeek? = when (token) {
        "monday", "montag" -> DayOfWeek.MONDAY
        "tuesday", "dienstag" -> DayOfWeek.TUESDAY
        "wednesday", "mittwoch" -> DayOfWeek.WEDNESDAY
        "thursday", "donnerstag" -> DayOfWeek.THURSDAY
        "friday", "freitag" -> DayOfWeek.FRIDAY
        "saturday", "samstag" -> DayOfWeek.SATURDAY
        "sunday", "sonntag" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseMonth(token: String): Month? = when (token.lowercase(Locale.ROOT)) {
        "jan", "january" -> Month.JANUARY
        "feb", "february" -> Month.FEBRUARY
        "mar", "march" -> Month.MARCH
        "apr", "april" -> Month.APRIL
        "may" -> Month.MAY
        "jun", "june" -> Month.JUNE
        "jul", "july" -> Month.JULY
        "aug", "august" -> Month.AUGUST
        "sep", "sept", "september" -> Month.SEPTEMBER
        "oct", "october" -> Month.OCTOBER
        "nov", "november" -> Month.NOVEMBER
        "dec", "december" -> Month.DECEMBER
        else -> null
    }
}
