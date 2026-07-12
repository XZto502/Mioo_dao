package com.mioo.dao.data.model

import androidx.compose.runtime.Immutable

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
    val blockedKeywords: List<String> = emptyList(),
    val smartPreloadMode: String = "WIFI_ONLY",
    val preloadCount: Int = 10
)
