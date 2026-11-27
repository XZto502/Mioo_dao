package com.mioo.dao.notification

import com.mioo.dao.data.local.SubscriptionNotifyMode
import com.mioo.dao.data.local.SubscriptionWatchEntity

enum class WatchTier {
    HOT,
    WARM,
    COLD,
    DORMANT
}

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

    fun isInQuietHours(
        hourOfDay: Int,
        enabled: Boolean,
        startHour: Int = QUIET_HOURS_START,
        endHour: Int = QUIET_HOURS_END
    ): Boolean {
        if (!enabled) return false
        val hour = hourOfDay.coerceIn(0, 23)
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        return if (start == end) {
            false
        } else if (start < end) {
            hour in start until end
        } else {
            hour >= start || hour < end
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
