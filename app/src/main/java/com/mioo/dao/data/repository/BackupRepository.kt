package com.mioo.dao.data.repository

import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.DraftDao
import com.mioo.dao.data.local.DraftEntity
import com.mioo.dao.data.local.HistoryDao
import com.mioo.dao.data.local.HistoryEntity
import com.mioo.dao.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 通用备份/恢复。备份段常量与文件里的顶层键一致。 */
@Singleton
class BackupRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val historyDao: HistoryDao,
    private val draftDao: DraftDao
) {
    companion object {
        const val SECTION_COOKIES = "cookies"
        const val SECTION_HISTORY = "history"
        const val SECTION_BOOKMARKS = "bookmarks"
        const val SECTION_DRAFTS = "drafts"
        const val SECTION_SETTINGS = "settings"
        const val SECTION_BLOCKS = "blocks"
        val ALL_SECTIONS = listOf(
            SECTION_COOKIES, SECTION_HISTORY, SECTION_BOOKMARKS,
            SECTION_DRAFTS, SECTION_SETTINGS, SECTION_BLOCKS
        )
        private const val VERSION = 2
    }

    /** 各段恢复条数，-1 表示文件里没有该段。 */
    data class ImportResult(
        val cookies: Int = -1,
        val history: Int = -1,
        val bookmarks: Int = -1,
        val drafts: Int = -1,
        val settings: Boolean = false,
        val blocks: Boolean = false,
        val legacyCookiesOnly: Boolean = false
    )

    suspend fun exportBackup(sections: Set<String>): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        if (SECTION_COOKIES in sections) {
            // 结构同旧版 v1 的顶层字段，但收在 "cookies" 键下
            val obj = JSONObject()
            val cookiesArr = JSONArray()
            settingsDataStore.cookiesListFlow.first().forEach { cookiesArr.put(it) }
            obj.put("cookies", cookiesArr)
            val notesObj = JSONObject()
            settingsDataStore.cookieNotesFlow.first().forEach { (k, v) -> notesObj.put(k, v) }
            obj.put("cookieNotes", notesObj)
            obj.put("authCookie", settingsDataStore.authCookieFlow.first() ?: "")
            obj.put("selectedCookieIndex", settingsDataStore.selectedCookieIndexFlow.first())
            root.put(SECTION_COOKIES, obj)
        }

        if (SECTION_HISTORY in sections) {
            val arr = JSONArray()
            // getAllHistory 带 LIMIT 参数，备份取全量
            historyDao.getAllHistory(Int.MAX_VALUE).first().forEach { h ->
                val o = JSONObject()
                o.put("id", h.id)
                o.put("fid", h.fid ?: JSONObject.NULL)
                o.put("content", h.content)
                o.put("userid", h.userid)
                o.put("now", h.now)
                o.put("title", h.title ?: JSONObject.NULL)
                o.put("name", h.name ?: JSONObject.NULL)
                o.put("timestamp", h.timestamp)
                arr.put(o)
            }
            root.put(SECTION_HISTORY, arr)
        }

        if (SECTION_BOOKMARKS in sections) {
            val arr = JSONArray()
            historyDao.getAllBookmarks().first().forEach { b ->
                val o = JSONObject()
                o.put("id", b.id)
                o.put("fid", b.fid ?: JSONObject.NULL)
                o.put("content", b.content)
                o.put("userid", b.userid)
                o.put("now", b.now)
                o.put("title", b.title ?: JSONObject.NULL)
                o.put("name", b.name ?: JSONObject.NULL)
                o.put("timestamp", b.timestamp)
                o.put("lastReadReplyCount", b.lastReadReplyCount)
                o.put("lastKnownReplyCount", b.lastKnownReplyCount)
                arr.put(o)
            }
            root.put(SECTION_BOOKMARKS, arr)
        }

        if (SECTION_DRAFTS in sections) {
            val arr = JSONArray()
            draftDao.getAll().first().forEach { d ->
                val o = JSONObject()
                // id 为自增主键，不导出
                o.put("type", d.type)
                o.put("targetId", d.targetId)
                o.put("targetName", d.targetName)
                o.put("title", d.title ?: JSONObject.NULL)
                o.put("name", d.name ?: JSONObject.NULL)
                o.put("content", d.content)
                o.put("updatedAt", d.updatedAt)
                arr.put(o)
            }
            root.put(SECTION_DRAFTS, arr)
        }

        if (SECTION_SETTINGS in sections) {
            val o = JSONObject()
            o.put("themeMode", settingsDataStore.themeModeFlow.first())
            o.put("themeColor", settingsDataStore.themeColorFlow.first())
            o.put("fontSizeScale", settingsDataStore.fontSizeScaleFlow.first().toDouble())
            o.put("lineHeightScale", settingsDataStore.lineHeightScaleFlow.first().toDouble())
            o.put("chineseFont", settingsDataStore.chineseFontFlow.first())
            o.put("englishFont", settingsDataStore.englishFontFlow.first())
            o.put("dataSaverEnabled", settingsDataStore.dataSaverEnabledFlow.first())
            o.put("glassEffectEnabled", settingsDataStore.glassEffectEnabledFlow.first())
            o.put("quoteAutoDisplayEnabled", settingsDataStore.quoteAutoDisplayFlow.first())
            o.put("showShareCardBranding", settingsDataStore.showShareCardBrandingFlow.first())
            o.put("draftBoxEnabled", settingsDataStore.draftBoxEnabledFlow.first())
            o.put("smartPreloadMode", settingsDataStore.smartPreloadModeFlow.first())
            o.put("preloadCount", settingsDataStore.preloadCountFlow.first())
            o.put("subscriptionNotificationsEnabled", settingsDataStore.subscriptionNotificationsFlow.first())
            o.put("notificationIntervalMinutes", settingsDataStore.notificationIntervalMinutesFlow.first())
            o.put("cacheLimitMb", settingsDataStore.cacheLimitMbFlow.first())
            root.put(SECTION_SETTINGS, o)
        }

        if (SECTION_BLOCKS in sections) {
            val o = JSONObject()
            fun putList(key: String, list: List<String>) {
                val arr = JSONArray()
                list.forEach { arr.put(it) }
                o.put(key, arr)
            }
            putList("blockedThreads", settingsDataStore.blockedThreadsFlow.first())
            putList("blockedUsers", settingsDataStore.blockedUsersFlow.first())
            putList("blockedKeywords", settingsDataStore.blockedKeywordsFlow.first())
            putList("pinnedForums", settingsDataStore.pinnedForumsFlow.first())
            putList("expandedForumGroups", settingsDataStore.expandedForumGroupsFlow.first())
            root.put(SECTION_BLOCKS, o)
        }

        root.toString(2)
    }

    suspend fun importBackup(json: String): ImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(json)

        // 旧版纯饼干备份（v1）：顶层就有 cookies 数组
        if (root.optInt("version", 1) < 2 && root.optJSONArray(SECTION_COOKIES) != null) {
            val cookies = parseCookieList(root.optJSONArray(SECTION_COOKIES))
            settingsDataStore.saveCookiesList(cookies)
            settingsDataStore.saveCookieNotes(parseCookieNotes(root.optJSONObject("cookieNotes")))
            val auth = root.optString("authCookie", "")
            if (auth.isNotBlank()) {
                settingsDataStore.saveAuthCookie(auth)
            } else if (cookies.isNotEmpty()) {
                // 未带鉴权饼干：默认跟随第一块导入的饼干
                settingsDataStore.saveAuthCookie(cookies.first())
            }
            if (cookies.isNotEmpty()) {
                val selected = root.optInt("selectedCookieIndex", 0)
                    .coerceIn(0, cookies.size - 1)
                settingsDataStore.saveSelectedCookieIndex(selected)
                settingsDataStore.saveUserHash(cookies[selected])
            }
            return@withContext ImportResult(cookies = cookies.size, legacyCookiesOnly = true)
        }

        // v2：按文件里实际存在的段恢复
        var cookiesCount = -1
        var historyCount = -1
        var bookmarksCount = -1
        var draftsCount = -1
        var settingsRestored = false
        var blocksRestored = false

        root.optJSONObject(SECTION_COOKIES)?.let { obj ->
            val cookies = parseCookieList(obj.optJSONArray("cookies"))
            settingsDataStore.saveCookiesList(cookies)
            settingsDataStore.saveCookieNotes(parseCookieNotes(obj.optJSONObject("cookieNotes")))
            val auth = obj.optString("authCookie", "")
            if (auth.isNotBlank()) {
                settingsDataStore.saveAuthCookie(auth)
            } else if (cookies.isNotEmpty()) {
                // 未带鉴权饼干：默认跟随第一块导入的饼干
                settingsDataStore.saveAuthCookie(cookies.first())
            }
            if (cookies.isNotEmpty()) {
                val selected = obj.optInt("selectedCookieIndex", 0)
                    .coerceIn(0, cookies.size - 1)
                settingsDataStore.saveSelectedCookieIndex(selected)
                settingsDataStore.saveUserHash(cookies[selected])
            }
            cookiesCount = cookies.size
        }

        root.optJSONArray(SECTION_HISTORY)?.let { arr ->
            val entities = mutableListOf<HistoryEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                entities.add(
                    HistoryEntity(
                        id = o.optString("id"),
                        fid = o.optNullableString("fid"),
                        content = o.optString("content"),
                        userid = o.optString("userid"),
                        now = o.optString("now"),
                        title = o.optNullableString("title"),
                        name = o.optNullableString("name"),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            if (entities.isNotEmpty()) historyDao.insertHistories(entities)
            historyCount = entities.size
        }

        root.optJSONArray(SECTION_BOOKMARKS)?.let { arr ->
            val entities = mutableListOf<BookmarkEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                entities.add(
                    BookmarkEntity(
                        id = o.optString("id"),
                        fid = o.optNullableString("fid"),
                        content = o.optString("content"),
                        userid = o.optString("userid"),
                        now = o.optString("now"),
                        title = o.optNullableString("title"),
                        name = o.optNullableString("name"),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        lastReadReplyCount = o.optInt("lastReadReplyCount", 0),
                        lastKnownReplyCount = o.optInt("lastKnownReplyCount", 0)
                    )
                )
            }
            if (entities.isNotEmpty()) historyDao.insertBookmarks(entities)
            bookmarksCount = entities.size
        }

        root.optJSONArray(SECTION_DRAFTS)?.let { arr ->
            var count = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val parsed = DraftEntity(
                    type = o.optString("type"),
                    targetId = o.optString("targetId"),
                    targetName = o.optString("targetName"),
                    title = o.optNullableString("title"),
                    name = o.optNullableString("name"),
                    content = o.optString("content"),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
                // 按 (type, targetId) 合并已有草稿的 id，避免重复导入产生重复行
                val existing = draftDao.find(parsed.type, parsed.targetId)
                draftDao.upsert(if (existing != null) parsed.copy(id = existing.id) else parsed)
                count++
            }
            draftsCount = count
        }

        root.optJSONObject(SECTION_SETTINGS)?.let { o ->
            // 只恢复文件里出现的键，缺键跳过
            if (o.has("themeMode")) settingsDataStore.saveThemeMode(o.optString("themeMode"))
            if (o.has("themeColor")) settingsDataStore.saveThemeColor(o.optString("themeColor"))
            if (o.has("fontSizeScale")) settingsDataStore.saveFontSizeScale(o.optDouble("fontSizeScale").toFloat())
            if (o.has("lineHeightScale")) settingsDataStore.saveLineHeightScale(o.optDouble("lineHeightScale").toFloat())
            if (o.has("chineseFont")) settingsDataStore.saveChineseFont(o.optString("chineseFont"))
            if (o.has("englishFont")) settingsDataStore.saveEnglishFont(o.optString("englishFont"))
            if (o.has("dataSaverEnabled")) settingsDataStore.saveDataSaverEnabled(o.optBoolean("dataSaverEnabled"))
            if (o.has("glassEffectEnabled")) settingsDataStore.saveGlassEffectEnabled(o.optBoolean("glassEffectEnabled"))
            if (o.has("quoteAutoDisplayEnabled")) settingsDataStore.saveQuoteAutoDisplay(o.optBoolean("quoteAutoDisplayEnabled"))
            if (o.has("showShareCardBranding")) settingsDataStore.setShowShareCardBranding(o.optBoolean("showShareCardBranding", true))
            if (o.has("draftBoxEnabled")) settingsDataStore.saveDraftBoxEnabled(o.optBoolean("draftBoxEnabled"))
            if (o.has("smartPreloadMode")) settingsDataStore.saveSmartPreloadMode(o.optString("smartPreloadMode"))
            if (o.has("preloadCount")) settingsDataStore.savePreloadCount(o.optInt("preloadCount"))
            if (o.has("subscriptionNotificationsEnabled")) settingsDataStore.saveSubscriptionNotifications(o.optBoolean("subscriptionNotificationsEnabled"))
            if (o.has("notificationIntervalMinutes")) settingsDataStore.saveNotificationIntervalMinutes(o.optInt("notificationIntervalMinutes"))
            if (o.has("cacheLimitMb")) settingsDataStore.saveCacheLimitMb(o.optInt("cacheLimitMb"))
            settingsRestored = true
        }

        root.optJSONObject(SECTION_BLOCKS)?.let { o ->
            if (o.has("blockedThreads")) settingsDataStore.saveBlockedThreads(parseStringList(o.optJSONArray("blockedThreads")))
            if (o.has("blockedUsers")) settingsDataStore.saveBlockedUsers(parseStringList(o.optJSONArray("blockedUsers")))
            if (o.has("blockedKeywords")) settingsDataStore.saveBlockedKeywords(parseStringList(o.optJSONArray("blockedKeywords")))
            if (o.has("pinnedForums")) settingsDataStore.savePinnedForums(parseStringList(o.optJSONArray("pinnedForums")))
            if (o.has("expandedForumGroups")) settingsDataStore.saveExpandedForumGroups(parseStringList(o.optJSONArray("expandedForumGroups")))
            blocksRestored = true
        }

        ImportResult(
            cookies = cookiesCount,
            history = historyCount,
            bookmarks = bookmarksCount,
            drafts = draftsCount,
            settings = settingsRestored,
            blocks = blocksRestored
        )
    }

    private fun parseCookieList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val c = arr.optString(i, "")
            if (c.isNotBlank() && c !in list) list.add(c)
        }
        return list
    }

    private fun parseCookieNotes(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val notes = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            notes[k] = obj.optString(k, "")
        }
        return notes
    }

    private fun parseStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }
}

/** JSON 里缺键或显式 null 时返回 null（isNull 对缺键同样为 true）。 */
private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key)
