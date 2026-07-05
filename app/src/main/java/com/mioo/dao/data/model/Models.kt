package com.mioo.dao.data.model

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class FeedFolder(
    val name: String,
    val uuid: String
)

data class UserSettings(
    val cookie: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSizeScale: Float = 1.0f,
    val themeColor: String = "dynamic",
    val cookiesList: List<String> = emptyList(),
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
