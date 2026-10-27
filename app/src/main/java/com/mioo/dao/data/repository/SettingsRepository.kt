package com.mioo.dao.data.repository

import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    // SupervisorJob: without it one failed write (e.g. DataStore IOException)
    // cancels the scope root and every later settings write becomes a silent no-op.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<UserSettings> = combine(
        combine(
            settingsDataStore.userHashFlow,
            settingsDataStore.themeModeFlow,
            settingsDataStore.fontSizeScaleFlow,
            settingsDataStore.themeColorFlow,
            settingsDataStore.cookiesListFlow,
            settingsDataStore.cookieNotesFlow,
            settingsDataStore.selectedCookieIndexFlow,
            settingsDataStore.authCookieFlow,
            settingsDataStore.feedFoldersFlow,
            settingsDataStore.blockedThreadsFlow,
            settingsDataStore.blockedUsersFlow,
            settingsDataStore.pinnedForumsFlow,
            settingsDataStore.blockedKeywordsFlow,
            settingsDataStore.smartPreloadModeFlow,
            settingsDataStore.preloadCountFlow,
            settingsDataStore.subscriptionNotificationsFlow,
            settingsDataStore.notificationIntervalMinutesFlow,
            settingsDataStore.lineHeightScaleFlow,
            settingsDataStore.expandedForumGroupsFlow,
            settingsDataStore.chineseFontFlow,
            settingsDataStore.englishFontFlow,
            settingsDataStore.dataSaverEnabledFlow
        ) { args ->
            val cookie = args[0] as? String
            val themeModeStr = args[1] as String
            val fontSizeScale = args[2] as Float
            val themeColor = args[3] as String
            @Suppress("UNCHECKED_CAST")
            val cookiesList = args[4] as List<String>
            @Suppress("UNCHECKED_CAST")
            val cookieNotes = args[5] as Map<String, String>
            val selectedIndex = args[6] as Int
            val authCookie = args[7] as? String
            @Suppress("UNCHECKED_CAST")
            val feedFolders = args[8] as List<com.mioo.dao.data.model.FeedFolder>
            @Suppress("UNCHECKED_CAST")
            val blockedThreads = args[9] as List<String>
            @Suppress("UNCHECKED_CAST")
            val blockedUsers = args[10] as List<String>
            @Suppress("UNCHECKED_CAST")
            val pinnedForums = args[11] as List<String>
            @Suppress("UNCHECKED_CAST")
            val blockedKeywords = args[12] as List<String>
            val smartPreloadMode = args[13] as String
            val preloadCount = args[14] as Int
            val subscriptionNotifications = args[15] as Boolean
            val notificationIntervalMinutes = args[16] as Int
            val lineHeightScale = args[17] as Float
            @Suppress("UNCHECKED_CAST")
            val expandedForumGroups = args[18] as List<String>
            val chineseFont = args[19] as String
            val englishFont = args[20] as String
            val dataSaverEnabled = args[21] as Boolean

            val themeMode = try {
                ThemeMode.valueOf(themeModeStr)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
            UserSettings(
                cookie = cookie ?: "",
                themeMode = themeMode,
                fontSizeScale = fontSizeScale,
                lineHeightScale = lineHeightScale,
                chineseFont = chineseFont,
                englishFont = englishFont,
                dataSaverEnabled = dataSaverEnabled,
                themeColor = themeColor,
                cookiesList = cookiesList,
                cookieNotes = cookieNotes,
                selectedCookieIndex = selectedIndex,
                authCookie = authCookie ?: "",
                feedFolders = feedFolders,
                blockedThreads = blockedThreads,
                blockedUsers = blockedUsers,
                pinnedForums = pinnedForums,
                expandedForumGroups = expandedForumGroups,
                blockedKeywords = blockedKeywords,
                smartPreloadMode = smartPreloadMode,
                preloadCount = preloadCount,
                subscriptionNotificationsEnabled = subscriptionNotifications,
                notificationIntervalMinutes = notificationIntervalMinutes
            )
        },
        settingsDataStore.glassEffectEnabledFlow,
        settingsDataStore.cacheLimitMbFlow,
        settingsDataStore.draftBoxEnabledFlow,
        settingsDataStore.quoteAutoDisplayFlow
    ) { base, glassEffectEnabled, cacheLimitMb, draftBoxEnabled, quoteAutoDisplay ->
        base.copy(
            glassEffectEnabled = glassEffectEnabled,
            cacheLimitMb = cacheLimitMb,
            draftBoxEnabled = draftBoxEnabled,
            quoteAutoDisplayEnabled = quoteAutoDisplay
        )
    }
        .combine(settingsDataStore.diceBadgesEnabledFlow) { settings, diceBadgesEnabled ->
            settings.copy(diceBadgesEnabled = diceBadgesEnabled)
        }
        .stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings()
    )

    fun updateSmartPreloadMode(mode: String) {
        repositoryScope.launch {
            settingsDataStore.saveSmartPreloadMode(mode)
        }
    }

    fun updateSubscriptionNotifications(enabled: Boolean) {
        repositoryScope.launch {
            settingsDataStore.saveSubscriptionNotifications(enabled)
        }
    }

    fun updateNotificationIntervalMinutes(minutes: Int) {
        repositoryScope.launch {
            settingsDataStore.saveNotificationIntervalMinutes(minutes)
        }
    }

    fun updateGlassEffectEnabled(enabled: Boolean) {
        repositoryScope.launch {
            settingsDataStore.saveGlassEffectEnabled(enabled)
        }
    }

    fun updateDiceBadgesEnabled(enabled: Boolean) {
        repositoryScope.launch {
            settingsDataStore.saveDiceBadgesEnabled(enabled)
        }
    }

    fun updateDraftBoxEnabled(enabled: Boolean) {
        repositoryScope.launch {
            settingsDataStore.saveDraftBoxEnabled(enabled)
        }
    }

    fun updateQuoteAutoDisplay(enabled: Boolean) {
        repositoryScope.launch {
            settingsDataStore.saveQuoteAutoDisplay(enabled)
        }
    }

    fun updateDataSaverEnabled(enabled: Boolean) {
        repositoryScope.launch {
            settingsDataStore.saveDataSaverEnabled(enabled)
        }
    }

    fun updateCacheLimitMb(mb: Int) {
        repositoryScope.launch {
            settingsDataStore.saveCacheLimitMb(mb)
        }
    }

    fun updatePreloadCount(count: Int) {
        repositoryScope.launch {
            settingsDataStore.savePreloadCount(count)
        }
    }

    fun updateCookie(cookie: String) {
        repositoryScope.launch {
            settingsDataStore.saveUserHash(cookie)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        repositoryScope.launch {
            settingsDataStore.saveThemeMode(themeMode.name)
            settingsDataStore.saveDarkMode(themeMode == ThemeMode.DARK)
        }
    }

    fun updateFontSizeScale(scale: Float) {
        repositoryScope.launch {
            settingsDataStore.saveFontSizeScale(scale)
        }
    }

    fun updateLineHeightScale(scale: Float) {
        repositoryScope.launch {
            settingsDataStore.saveLineHeightScale(scale)
        }
    }

    fun updateChineseFont(name: String) {
        repositoryScope.launch {
            settingsDataStore.saveChineseFont(name)
        }
    }

    fun updateEnglishFont(name: String) {
        repositoryScope.launch {
            settingsDataStore.saveEnglishFont(name)
        }
    }

    fun updateThemeColor(color: String) {
        repositoryScope.launch {
            settingsDataStore.saveThemeColor(color)
        }
    }

    fun addCookie(cookie: String) {
        repositoryScope.launch {
            // 直读 DataStore：settings.value 是 stateIn 产物，存在传播滞后，
            // 快速连续操作时基于旧快照计算会互相覆盖
            val currentList = settingsDataStore.cookiesListFlow.first().toMutableList()
            if (cookie.isNotBlank() && !currentList.contains(cookie)) {
                currentList.add(cookie)
                settingsDataStore.saveCookiesList(currentList)
                if (currentList.size == 1) {
                    settingsDataStore.saveSelectedCookieIndex(0)
                    settingsDataStore.saveUserHash(cookie)
                    // 鉴权饼干默认跟随第一块导入的饼干（用户手动选择其他后不再覆盖）
                    val auth = settingsDataStore.authCookieFlow.first()
                    if (auth.isNullOrBlank()) settingsDataStore.saveAuthCookie(cookie)
                }
            }
        }
    }

    fun selectCookieIndex(index: Int) {
        repositoryScope.launch {
            val list = settings.value.cookiesList
            if (index in list.indices) {
                settingsDataStore.saveSelectedCookieIndex(index)
                settingsDataStore.saveUserHash(list[index])
            }
        }
    }

    suspend fun getThreadCookie(threadId: String): String? =
        settingsDataStore.getThreadCookie(threadId)

    /** 串 → 骰点高亮规则（null = 未设置，调用方回退智能模式）。 */
    suspend fun getThreadDicePreset(threadId: String): com.mioo.dao.utils.DiceRulePreset? =
        settingsDataStore.getThreadDicePreset(threadId)

    fun saveThreadDicePreset(threadId: String, preset: com.mioo.dao.utils.DiceRulePreset) {
        repositoryScope.launch {
            settingsDataStore.saveThreadDicePreset(threadId, preset)
        }
    }

    fun saveThreadCookie(threadId: String, cookie: String) {
        repositoryScope.launch {
            settingsDataStore.saveThreadCookie(threadId, cookie)
        }
    }

    fun deleteCookie(cookie: String) {
        repositoryScope.launch {
            val currentList = settingsDataStore.cookiesListFlow.first().toMutableList()
            val indexToDelete = currentList.indexOf(cookie)
            if (indexToDelete != -1) {
                currentList.removeAt(indexToDelete)
                settingsDataStore.saveCookiesList(currentList)

                val notes = settingsDataStore.cookieNotesFlow.first().toMutableMap()
                notes.remove(cookie)
                settingsDataStore.saveCookieNotes(notes)

                val newIndex = if (currentList.isEmpty()) 0 else {
                    val prevIndex = settingsDataStore.selectedCookieIndexFlow.first()
                    when {
                        // 删除的是选中项之前的饼干 → 选中项左移一位，保持同一逻辑饼干
                        indexToDelete < prevIndex -> prevIndex - 1
                        // 删除的是末尾 → 收缩到新末尾
                        prevIndex >= currentList.size -> currentList.size - 1
                        else -> prevIndex
                    }
                }
                settingsDataStore.saveSelectedCookieIndex(newIndex)

                if (currentList.isNotEmpty()) {
                    settingsDataStore.saveUserHash(currentList[newIndex])
                } else {
                    settingsDataStore.saveUserHash("")
                }
            }
        }
    }

    fun setCookieNote(cookie: String, note: String) {
        repositoryScope.launch {
            val notes = settings.value.cookieNotes.toMutableMap()
            if (note.isBlank()) notes.remove(cookie) else notes[cookie] = note.trim()
            settingsDataStore.saveCookieNotes(notes)
        }
    }

    fun updateAuthCookie(authCookie: String) {
        repositoryScope.launch {
            settingsDataStore.saveAuthCookie(authCookie)
        }
    }

    fun addFeedFolder(folder: com.mioo.dao.data.model.FeedFolder) {
        repositoryScope.launch {
            val currentList = settings.value.feedFolders.toMutableList()
            if (currentList.none { it.uuid == folder.uuid }) {
                currentList.add(folder)
                settingsDataStore.saveFeedFolders(currentList)
            }
        }
    }

    fun removeFeedFolder(uuid: String) {
        repositoryScope.launch {
            val currentList = settings.value.feedFolders.toMutableList()
            currentList.removeAll { it.uuid == uuid }
            settingsDataStore.saveFeedFolders(currentList)
        }
    }

    fun addBlockedThread(threadId: String) {
        repositoryScope.launch {
            val current = settings.value.blockedThreads.toMutableList()
            if (!current.contains(threadId)) {
                current.add(threadId)
                settingsDataStore.saveBlockedThreads(current)
            }
        }
    }

    fun removeBlockedThread(threadId: String) {
        repositoryScope.launch {
            val current = settings.value.blockedThreads.toMutableList()
            if (current.remove(threadId)) {
                settingsDataStore.saveBlockedThreads(current)
            }
        }
    }

    fun addBlockedUser(userHash: String) {
        repositoryScope.launch {
            val current = settings.value.blockedUsers.toMutableList()
            if (!current.contains(userHash)) {
                current.add(userHash)
                settingsDataStore.saveBlockedUsers(current)
            }
        }
    }

    fun removeBlockedUser(userHash: String) {
        repositoryScope.launch {
            val current = settings.value.blockedUsers.toMutableList()
            if (current.remove(userHash)) {
                settingsDataStore.saveBlockedUsers(current)
            }
        }
    }

    fun togglePinForum(forumId: String) {
        repositoryScope.launch {
            val current = settings.value.pinnedForums.toMutableList()
            if (current.contains(forumId)) {
                current.remove(forumId)
            } else {
                current.add(forumId)
            }
            settingsDataStore.savePinnedForums(current)
        }
    }

    fun toggleForumGroupExpand(groupId: String) {
        repositoryScope.launch {
            val current = settings.value.expandedForumGroups.toMutableList()
            if (current.contains(groupId)) {
                current.remove(groupId)
            } else {
                current.add(groupId)
            }
            settingsDataStore.saveExpandedForumGroups(current)
        }
    }

    fun addBlockedKeyword(keyword: String) {
        repositoryScope.launch {
            val current = settings.value.blockedKeywords.toMutableList()
            if (keyword.isNotBlank() && !current.contains(keyword)) {
                current.add(keyword)
                settingsDataStore.saveBlockedKeywords(current)
            }
        }
    }

    fun removeBlockedKeyword(keyword: String) {
        repositoryScope.launch {
            val current = settings.value.blockedKeywords.toMutableList()
            if (current.remove(keyword)) {
                settingsDataStore.saveBlockedKeywords(current)
            }
        }
    }

    suspend fun getThreadDraft(threadId: String): String {
        return settingsDataStore.getThreadDraft(threadId)
    }

    suspend fun saveThreadDraft(threadId: String, draft: String) {
        settingsDataStore.saveThreadDraft(threadId, draft)
    }

    suspend fun getNewThreadDraft(): String {
        return settingsDataStore.getNewThreadDraft()
    }

    suspend fun saveNewThreadDraft(draft: String) {
        settingsDataStore.saveNewThreadDraft(draft)
    }

    /** Export cookies + notes + auth as JSON for backup. */
    fun exportCookiesJson(): String {
        val s = settings.value
        val root = JSONObject()
        root.put("version", 1)
        root.put("selectedCookieIndex", s.selectedCookieIndex)
        root.put("authCookie", s.authCookie)
        val cookiesArr = JSONArray()
        s.cookiesList.forEach { cookiesArr.put(it) }
        root.put("cookies", cookiesArr)
        val notesObj = JSONObject()
        s.cookieNotes.forEach { (k, v) -> notesObj.put(k, v) }
        root.put("cookieNotes", notesObj)
        return root.toString(2)
    }

    /**
     * Import cookies package. Returns number of cookies imported.
     */
    fun importCookiesJson(json: String): Int {
        val root = JSONObject(json)
        val arr = root.optJSONArray("cookies") ?: return 0
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val c = arr.optString(i, "")
            if (c.isNotBlank() && c !in list) list.add(c)
        }
        val notesObj = root.optJSONObject("cookieNotes")
        val notes = mutableMapOf<String, String>()
        if (notesObj != null) {
            val keys = notesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                notes[k] = notesObj.optString(k, "")
            }
        }
        val auth = root.optString("authCookie", "")
        val selected = root.optInt("selectedCookieIndex", 0).coerceIn(0, (list.size - 1).coerceAtLeast(0))

        repositoryScope.launch {
            settingsDataStore.saveCookiesList(list)
            settingsDataStore.saveCookieNotes(notes)
            if (auth.isNotBlank()) {
                settingsDataStore.saveAuthCookie(auth)
            } else if (list.isNotEmpty()) {
                // 导入未带鉴权饼干：默认跟随第一块导入的饼干
                settingsDataStore.saveAuthCookie(list.first())
            }
            if (list.isNotEmpty()) {
                settingsDataStore.saveSelectedCookieIndex(selected)
                settingsDataStore.saveUserHash(list[selected])
            }
        }
        return list.size
    }
}
