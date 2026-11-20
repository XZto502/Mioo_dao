package com.mioo.dao.ui.components

import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val serverReadingTime = Regex("""^(\d{4}-\d{1,2}-\d{1,2})(?:\([^)]*\)|（[^）]*）)?[ T]?(\d{1,2}:\d{2}(?::\d{2})?)$""")
private val readingTimeParser = DateTimeFormatter.ofPattern("uuuu-M-d H:mm[:ss]", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
private val readingClockSeconds = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val readingClockMinutes = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val readingMonthDaySeconds = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss", Locale.ROOT)
private val readingMonthDayMinutes = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)
private val readingYearDaySeconds = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss", Locale.ROOT)
private val readingYearDayMinutes = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm", Locale.ROOT)

private class ParsedReadingTime(val value: LocalDateTime?)

private val readingTimeCacheLock = Any()
private val parsedReadingTimeCache =
    object : LinkedHashMap<String, ParsedReadingTime>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParsedReadingTime>?): Boolean =
            size > 512
    }

private fun parseServerReadingTime(trimmed: String): LocalDateTime? {
    synchronized(readingTimeCacheLock) {
        parsedReadingTimeCache[trimmed]?.let { return it.value }
    }
    val match = serverReadingTime.matchEntire(trimmed)
    val parsed = if (match != null) {
        runCatching {
            LocalDateTime.parse("${match.groupValues[1]} ${match.groupValues[2]}", readingTimeParser)
        }.getOrNull()
    } else null
    synchronized(readingTimeCacheLock) {
        parsedReadingTimeCache[trimmed] = ParsedReadingTime(parsed)
    }
    return parsed
}

internal fun prewarmReadingTimes(rawTimes: Collection<String>) {
    for (raw in rawTimes) {
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty()) parseServerReadingTime(trimmed)
    }
}

internal fun formatReadingTime(raw: String, now: LocalDateTime = LocalDateTime.now()): String {
    val time = parseServerReadingTime(raw.trim()) ?: return raw
    return formatReadingDateTime(time, now)
}

internal fun formatReadingTimestamp(timestamp: Long, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault(), includeSeconds: Boolean = true): String =
    formatReadingDateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone), now, includeSeconds)

private fun formatReadingDateTime(time: LocalDateTime, now: LocalDateTime, includeSeconds: Boolean = true): String {
    val age = Duration.between(time, now)
    // A small server/client clock skew must not hide seconds on a just-sent post.
    val recent = includeSeconds && age < Duration.ofDays(7)
    val clock = if (recent) readingClockSeconds else readingClockMinutes
    return when (time.toLocalDate()) {
        now.toLocalDate() -> "今天 ${time.format(clock)}"
        now.toLocalDate().minusDays(1) -> "昨天 ${time.format(clock)}"
        else -> time.format(if (time.year == now.year) {
            if (recent) readingMonthDaySeconds else readingMonthDayMinutes
        } else {
            if (recent) readingYearDaySeconds else readingYearDayMinutes
        })
    }
}
