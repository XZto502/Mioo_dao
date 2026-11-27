package com.mioo.dao.notification

import com.mioo.dao.data.local.SubscriptionNotifyMode
import com.mioo.dao.data.local.SubscriptionWatchEntity

enum class WatchTier {
    HOT,
    WARM,
    COLD,
    DORMANT
}

data class IntervalValidationResult(
    val minutes: Int?,
    val error: String?
)

enum class QuietTimeField {
    START_HOUR,
    START_MINUTE,
    END_HOUR,
    END_MINUTE,
    RANGE
}

data class QuietHoursValidationResult(
    val startHour: Int?,
    val startMinute: Int?,
    val endHour: Int?,
    val endMinute: Int?,
    val error: String?,
    val invalidFields: Set<QuietTimeField> = emptySet()
)

object SubscriptionPollPolicy {
    const val DEFAULT_BASE_INTERVAL_MINUTES = 60
    const val MIN_INTERVAL_MINUTES = 15
    const val MAX_INTERVAL_MINUTES = 180
    const val MAX_THREADS_PER_RUN = 6
    const val MAX_HOT_WARM_SLOTS = 4
    const val RESERVED_COLD_DORMANT_SLOTS = 2
    const val MAX_PREVIEW_FETCHES_PER_RUN = 2
    const val CLOUD_FEED_DISCOVERY_INTERVAL_MS = 6L * 3600_000L
    const val QUIET_HOURS_START = 0
    const val QUIET_HOURS_END = 8

    private const val ONE_DAY_MS = 24L * 3600_000L
    private const val SEVEN_DAYS_MS = 7L * ONE_DAY_MS
    private const val THIRTY_DAYS_MS = 30L * ONE_DAY_MS
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun classifyTier(lastActivityAt: Long, nowMs: Long): WatchTier {
        if (lastActivityAt <= 0L) return WatchTier.DORMANT
        val idleMs = (nowMs - lastActivityAt).coerceAtLeast(0L)
        return when {
            idleMs < ONE_DAY_MS -> WatchTier.HOT
            idleMs < SEVEN_DAYS_MS -> WatchTier.WARM
            idleMs < THIRTY_DAYS_MS -> WatchTier.COLD
            else -> WatchTier.DORMANT
        }
    }

    fun nextCheckDelayMs(tier: WatchTier, baseIntervalMinutes: Int): Long {
        val clampedBase = baseIntervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        return when (tier) {
            WatchTier.HOT -> clampedBase * 60_000L
            WatchTier.WARM -> 4L * 3600_000L
            WatchTier.COLD -> 18L * 3600_000L
            WatchTier.DORMANT -> 48L * 3600_000L
        }
    }

    fun selectDueWatches(
        watches: List<SubscriptionWatchEntity>,
        nowMs: Long,
        maxTotal: Int = MAX_THREADS_PER_RUN,
        maxHotWarm: Int = MAX_HOT_WARM_SLOTS,
        reservedColdDormant: Int = RESERVED_COLD_DORMANT_SLOTS
    ): List<SubscriptionWatchEntity> {
        if (maxTotal <= 0) return emptyList()
        val due = watches.filter { it.notifyMode != SubscriptionNotifyMode.MUTE && it.nextCheckAt <= nowMs }
        if (due.isEmpty()) return emptyList()

        val (hotWarmRaw, coldDormantRaw) = due.partition {
            val tier = classifyTier(it.lastActivityAt, nowMs)
            tier == WatchTier.HOT || tier == WatchTier.WARM
        }
        val hotWarm = hotWarmRaw.sortedWith(compareBy<SubscriptionWatchEntity> { it.nextCheckAt }.thenBy { it.lastCheckedAt })
        val coldDormant = coldDormantRaw.sortedWith(compareBy<SubscriptionWatchEntity> { it.lastCheckedAt }.thenBy { it.nextCheckAt })

        val chosenHotWarm = hotWarm.take(maxHotWarm.coerceAtMost(maxTotal))
        val chosenColdDormant = coldDormant.take(reservedColdDormant.coerceAtMost(maxTotal - chosenHotWarm.size))

        val result = ArrayList<SubscriptionWatchEntity>(maxTotal)
        result.addAll(chosenHotWarm)
        result.addAll(chosenColdDormant)

        if (result.size < maxTotal) {
            val remaining = maxTotal - result.size
            val extraHotWarm = hotWarm.drop(chosenHotWarm.size).take(remaining)
            result.addAll(extraHotWarm)
            if (result.size < maxTotal) {
                val extraCold = coldDormant.drop(chosenColdDormant.size).take(maxTotal - result.size)
                result.addAll(extraCold)
            }
        }
        return result
    }

    fun validateIntervalMinutes(raw: String): IntervalValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return IntervalValidationResult(null, "请输入检查间隔（15–180 分钟）")
        }
        if (trimmed.any { it !in '0'..'9' }) {
            return IntervalValidationResult(null, "请输入有效整数")
        }
        val parsed = trimmed.toIntOrNull()
            ?: return IntervalValidationResult(null, "请输入有效整数")
        if (parsed < MIN_INTERVAL_MINUTES) {
            return IntervalValidationResult(null, "最低不能小于 $MIN_INTERVAL_MINUTES 分钟")
        }
        if (parsed > MAX_INTERVAL_MINUTES) {
            return IntervalValidationResult(null, "最高不能超过 $MAX_INTERVAL_MINUTES 分钟")
        }
        return IntervalValidationResult(parsed, null)
    }

    fun validateQuietHoursInput(
        startHourRaw: String,
        startMinuteRaw: String,
        endHourRaw: String,
        endMinuteRaw: String
    ): QuietHoursValidationResult {
        val fields = listOf(
            QuietTimeField.START_HOUR to startHourRaw.trim(),
            QuietTimeField.START_MINUTE to startMinuteRaw.trim(),
            QuietTimeField.END_HOUR to endHourRaw.trim(),
            QuietTimeField.END_MINUTE to endMinuteRaw.trim()
        )
        val emptyFields = fields.filter { it.second.isEmpty() }.mapTo(mutableSetOf()) { it.first }
        if (emptyFields.isNotEmpty()) {
            return QuietHoursValidationResult(null, null, null, null, "请输入完整时间", emptyFields)
        }
        val nonDigitFields = fields.filter { (_, v) -> v.any { it !in '0'..'9' } }.mapTo(mutableSetOf()) { it.first }
        if (nonDigitFields.isNotEmpty()) {
            return QuietHoursValidationResult(null, null, null, null, "仅支持数字", nonDigitFields)
        }
        val sh = fields[0].second.toIntOrNull()
        val sm = fields[1].second.toIntOrNull()
        val eh = fields[2].second.toIntOrNull()
        val em = fields[3].second.toIntOrNull()
        if (sh == null || sm == null || eh == null || em == null) {
            return QuietHoursValidationResult(null, null, null, null, "仅支持数字", fields.mapTo(mutableSetOf()) { it.first })
        }
        val badHours = mutableSetOf<QuietTimeField>()
        if (sh !in 0..23) badHours.add(QuietTimeField.START_HOUR)
        if (eh !in 0..23) badHours.add(QuietTimeField.END_HOUR)
        if (badHours.isNotEmpty()) {
            return QuietHoursValidationResult(null, null, null, null, "小时需在 00–23 之间", badHours)
        }
        val badMinutes = mutableSetOf<QuietTimeField>()
        if (sm !in 0..59) badMinutes.add(QuietTimeField.START_MINUTE)
        if (em !in 0..59) badMinutes.add(QuietTimeField.END_MINUTE)
        if (badMinutes.isNotEmpty()) {
            return QuietHoursValidationResult(null, null, null, null, "分钟需在 00–59 之间", badMinutes)
        }
        if (sh == eh && sm == em) {
            return QuietHoursValidationResult(
                null, null, null, null,
                "开始与结束时间不能相同",
                setOf(QuietTimeField.RANGE)
            )
        }
        return QuietHoursValidationResult(sh, sm, eh, em, null)
    }

    fun isInQuietHours(
        hourOfDay: Int,
        enabled: Boolean,
        startHour: Int = QUIET_HOURS_START,
        endHour: Int = QUIET_HOURS_END
    ): Boolean = isInQuietHours(
        hourOfDay = hourOfDay,
        minuteOfHour = 0,
        enabled = enabled,
        startHour = startHour,
        startMinute = 0,
        endHour = endHour,
        endMinute = 0
    )

    fun isInQuietHours(
        hourOfDay: Int,
        minuteOfHour: Int,
        enabled: Boolean,
        startHour: Int = QUIET_HOURS_START,
        startMinute: Int = 0,
        endHour: Int = QUIET_HOURS_END,
        endMinute: Int = 0
    ): Boolean {
        if (!enabled) return false
        val nowMin = hourOfDay.coerceIn(0, 23) * 60 + minuteOfHour.coerceIn(0, 59)
        val startMin = startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
        val endMin = endHour.coerceIn(0, 23) * 60 + endMinute.coerceIn(0, 59)
        return if (startMin == endMin) {
            false
        } else if (startMin < endMin) {
            nowMin in startMin until endMin
        } else {
            nowMin >= startMin || nowMin < endMin
        }
    }

    fun nextCooldownMs(consecutiveErrors: Int): Long {
        return when {
            consecutiveErrors <= 1 -> 30L * 60_000L
            consecutiveErrors == 2 -> 2L * 3600_000L
            else -> 6L * 3600_000L
        }
    }

    fun shouldNotifyUpdate(
        watch: SubscriptionWatchEntity,
        newReplyCount: Int,
        hasNewPoReply: Boolean
    ): Boolean {
        return when (watch.notifyMode) {
            SubscriptionNotifyMode.MUTE -> false
            SubscriptionNotifyMode.PO_ONLY -> hasNewPoReply
            else -> newReplyCount > maxOf(watch.lastNotifiedReplyCount, watch.lastReadReplyCount)
        }
    }

    fun extractPlainPreview(html: String?, maxChars: Int = 72): String? {
        if (html.isNullOrBlank()) return null
        val text = html
            .replace("<br>", " ", ignoreCase = true)
            .replace("<br/>", " ", ignoreCase = true)
            .replace("<br />", " ", ignoreCase = true)
            .replace(HTML_TAG_REGEX, " ")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        if (text.isEmpty()) return null
        return if (text.length <= maxChars) text else text.take(maxChars.coerceAtLeast(1)) + "…"
    }

    fun calculateLastPage(replyCount: Int, pageSize: Int = 19): Int {
        return ((replyCount.coerceAtLeast(1) - 1) / pageSize) + 1
    }

    fun shouldSkipDuplicatePeriodicCheck(
        lastCheckAt: Long,
        nowMs: Long,
        intervalMinutes: Int
    ): Boolean {
        if (lastCheckAt <= 0L || nowMs <= lastCheckAt) return false
        val minGapMs = (intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES) * 60_000L) / 2L
        return (nowMs - lastCheckAt) < minGapMs
    }
}
