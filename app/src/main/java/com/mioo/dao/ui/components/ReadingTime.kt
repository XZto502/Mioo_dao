package com.mioo.dao.ui.components

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val serverReadingTime = Regex("""^(\d{4}-\d{1,2}-\d{1,2})(?:\([^)]*\)|（[^）]*）)?[ T]?(\d{1,2}:\d{2})(?::\d{2})?$""")
private val readingTimeParser = DateTimeFormatter.ofPattern("uuuu-M-d H:mm", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
private val readingClock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val readingMonthDay = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT)
private val readingYearDay = DateTimeFormatter.ofPattern("yy/MM/dd", Locale.ROOT)

internal fun formatReadingTime(raw: String, today: LocalDate = LocalDate.now()): String {
    val match = serverReadingTime.matchEntire(raw.trim()) ?: return raw
    val time = runCatching {
        LocalDateTime.parse("${match.groupValues[1]} ${match.groupValues[2]}", readingTimeParser)
    }.getOrNull() ?: return raw
    return when (time.toLocalDate()) {
        today -> "今天 ${time.format(readingClock)}"
        today.minusDays(1) -> "昨天 ${time.format(readingClock)}"
        else -> time.format(if (time.year == today.year) readingMonthDay else readingYearDay)
    }
}
