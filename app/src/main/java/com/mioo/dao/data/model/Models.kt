package com.mioo.dao.data.model

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Immutable
data class FeedFolder(
    val name: String,
    val uuid: String
)

@Immutable
data class UserSettings(
    val cookie: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSizeScale: Float = 1.0f,
    val lineHeightScale: Float = 1.0f,
    val chineseFont: String = "",
    val englishFont: String = "",
    val themeColor: String = "dynamic",
    val cookiesList: List<String> = emptyList(),
    /** Display notes keyed by cookie raw string. */
    val cookieNotes: Map<String, String> = emptyMap(),
    val selectedCookieIndex: Int = 0,
    val authCookie: String = "",
    val feedFolders: List<FeedFolder> = emptyList(),
    val blockedThreads: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList(),
    val pinnedForums: List<String> = emptyList(),
    val expandedForumGroups: List<String> = emptyList(),
    val blockedKeywords: List<String> = emptyList(),
    val smartPreloadMode: String = "DISABLED",
    val preloadCount: Int = 10,
    /** Local poll of bookmarked threads for new-reply system notifications (蓝岛-style). */
    val subscriptionNotificationsEnabled: Boolean = false,
    /** Minutes between background checks; clamped 15–180. */
    val notificationIntervalMinutes: Int = 30,
    /** Semi-transparent glass surfaces + ambient glow background. */
    val glassEffectEnabled: Boolean = true,
    /** Image disk-cache cap in megabytes. Oldest files are dropped past this. */
    val cacheLimitMb: Int = ImageCacheLimit.DEFAULT_MB,
    /** 草稿箱总开关：关闭后回复/新串不再自动存草稿。 */
    val draftBoxEnabled: Boolean = true,
    /** 引用自动显示：关闭后串内页回复正文不再内嵌展开引用卡片，仅保留可点击链接。 */
    val quoteAutoDisplayEnabled: Boolean = true,
    /** 省流量模式：列表图片不自动加载，点击单张加载。 */
    val dataSaverEnabled: Boolean = false,
    /** 音量键翻页：串内页按音量键上下翻页。 */
    val volumeKeyPaging: Boolean = false
)

/** User-facing cap for the Coil image disk cache. */
object ImageCacheLimit {
    const val DEFAULT_MB = 1024
    const val MIN_MB = 128
    const val MAX_MB = 4096
    const val STEP_MB = 128
    val SLIDER_STEPS: Int = (MAX_MB - MIN_MB) / STEP_MB - 1

    fun coerce(mb: Int): Int {
        val clamped = mb.coerceIn(MIN_MB, MAX_MB)
        val steps = ((clamped - MIN_MB).toDouble() / STEP_MB).roundToInt()
        return (MIN_MB + steps * STEP_MB).coerceIn(MIN_MB, MAX_MB)
    }

    fun toBytes(mb: Int): Long = coerce(mb).toLong() * 1024L * 1024L
}
