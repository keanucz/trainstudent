package com.trainstudent.voice

import com.trainstudent.model.ParsedDateTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Natural language time parser for voice input.
 */
@Singleton
class TimeParser @Inject constructor() {

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private val IN_TIME_PATTERN = Regex("""in\s+(\d+)\s*(hour|hr|minute|min)s?""")
        private val DAY_OF_WEEK_PATTERN = Regex("""(next\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
        private val TIME_12H_PATTERN = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)""")
        private val TIME_24H_PATTERN = Regex("""(\d{1,2}):(\d{2})(?:\s|$)""")
        private val WORD_TIME_PATTERN = Regex("""(half past|quarter past|quarter to)\s+(\d{1,2})""")
        private val BARE_HOUR_PATTERN = Regex("""(?:at|around|about)\s+(\d{1,2})(?:\s|$)""")
        
        private val DAYS_MAP = mapOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY
        )
    }

    fun parseTime(input: String): ParsedDateTime? {
        val lower = input.lowercase()
        val now = LocalDateTime.now()

        // Try patterns in priority order
        return parseNow(lower, now)
            ?: parseInTime(lower, now)
            ?: parseDayWithTime(lower, now)
            ?: parseDayOfWeek(lower, now)
    }

    private fun parseNow(input: String, now: LocalDateTime): ParsedDateTime? {
        if (!input.contains("now") && !input.contains("as soon as")) return null
        return ParsedDateTime(
            isoString = now.plusMinutes(5).format(ISO_FORMATTER),
            displayText = "Now",
            confidence = 0.95f,
            isRelative = true
        )
    }

    private fun parseInTime(input: String, now: LocalDateTime): ParsedDateTime? {
        val match = IN_TIME_PATTERN.find(input) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: 1
        val unit = match.groupValues[2]
        val isHours = unit.startsWith("hour") || unit.startsWith("hr")
        val resultTime = if (isHours) now.plusHours(amount.toLong()) else now.plusMinutes(amount.toLong())
        val unitDisplay = if (isHours) "hour" else "min"
        
        return ParsedDateTime(
            isoString = resultTime.format(ISO_FORMATTER),
            displayText = "In $amount $unitDisplay${if (amount > 1) "s" else ""}",
            confidence = 0.9f,
            isRelative = true
        )
    }

    private fun parseDayWithTime(input: String, now: LocalDateTime): ParsedDateTime? {
        val dayOffset = when {
            input.contains("day after tomorrow") -> 2
            input.contains("tomorrow") -> 1
            input.contains("today") -> 0
            else -> return null
        }
        
        val date = LocalDate.now().plusDays(dayOffset.toLong())
        val time = parseTimeOfDay(input)
        
        return if (time != null) {
            val resultTime = LocalDateTime.of(date, time)
            val dayText = when (dayOffset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            ParsedDateTime(
                isoString = resultTime.format(ISO_FORMATTER),
                displayText = "$dayText ${formatTime(time)}",
                confidence = 0.9f,
                isRelative = false
            )
        } else {
            // Day without time - default to 9am
            val resultTime = LocalDateTime.of(date, LocalTime.of(9, 0))
            val dayText = when (dayOffset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            ParsedDateTime(
                isoString = resultTime.format(ISO_FORMATTER),
                displayText = dayText,
                confidence = 0.7f,
                isRelative = false
            )
        }
    }

    private fun parseDayOfWeek(input: String, now: LocalDateTime): ParsedDateTime? {
        val match = DAY_OF_WEEK_PATTERN.find(input) ?: return null
        val isNext = match.groupValues[1].isNotEmpty()
        val targetDay = DAYS_MAP[match.groupValues[2]] ?: return null

        var date = LocalDate.now()
        while (date.dayOfWeek != targetDay) {
            date = date.plusDays(1)
        }
        if (isNext && date == LocalDate.now()) {
            date = date.plusDays(7)
        }

        val time = parseTimeOfDay(input) ?: LocalTime.of(9, 0)
        val resultTime = LocalDateTime.of(date, time)
        val prefix = if (isNext) "Next " else ""
        
        return ParsedDateTime(
            isoString = resultTime.format(ISO_FORMATTER),
            displayText = "$prefix${match.groupValues[2].replaceFirstChar { it.uppercase() }} ${formatTime(time)}",
            confidence = if (parseTimeOfDay(input) != null) 0.9f else 0.7f,
            isRelative = false
        )
    }

    private fun parseTimeOfDay(input: String): LocalTime? {
        // 12-hour format
        TIME_12H_PATTERN.find(input)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val isPm = match.groupValues[3].startsWith("p")
            
            hour = when {
                isPm && hour < 12 -> hour + 12
                !isPm && hour == 12 -> 0
                else -> hour
            }
            return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }

        // 24-hour format
        TIME_24H_PATTERN.find(input)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return LocalTime.of(hour, minute)
        }

        // Word-based time
        WORD_TIME_PATTERN.find(input)?.let { match ->
            val phrase = match.groupValues[1]
            var hour = match.groupValues[2].toIntOrNull() ?: return null
            if (hour in 1..11 && (input.contains("pm") || input.contains("afternoon") || input.contains("evening"))) {
                hour += 12
            }
            return when (phrase) {
                "half past" -> LocalTime.of(hour.coerceIn(0, 23), 30)
                "quarter past" -> LocalTime.of(hour.coerceIn(0, 23), 15)
                "quarter to" -> LocalTime.of((hour - 1).coerceIn(0, 23), 45)
                else -> null
            }
        }

        // Bare hour
        BARE_HOUR_PATTERN.find(input)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            if (hour in 1..9 && !input.contains("am") && !input.contains("morning")) {
                hour += 12
            }
            return LocalTime.of(hour.coerceIn(0, 23), 0)
        }

        return null
    }

    private fun formatTime(time: LocalTime): String {
        val hour = time.hour
        val minute = time.minute
        val displayHour = when {
            hour > 12 -> hour - 12
            hour == 0 -> 12
            else -> hour
        }
        val period = if (hour >= 12) "pm" else "am"
        return if (minute == 0) "$displayHour$period" else "$displayHour:${minute.toString().padStart(2, '0')}$period"
    }
}
