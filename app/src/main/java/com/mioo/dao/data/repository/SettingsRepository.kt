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
    val offlineUpdatePolicy = settingsDataStore.offlineUpdatePolicy
    suspend fun saveOfflineUpdateEnabled(enabled: Boolean) = settingsDataStore.saveOfflineUpdateEnabled(enabled)
    suspend fun saveOfflineUpdateWifiOnly(wifiOnly: Boolean) = settingsDataStore.saveOfflineUpdateWifiOnly(wifiOnly)
    // SupervisorJob: without it one failed write (e.g. DataStore IOException)
    // cancels the scope root and every later settings write becomes a silent no-op.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val scrollTouchGuardEnabled = settingsDataStore.scrollTouchGuardFlow.stateIn(
        repositoryScope, SharingStarted.Eagerly, false
    )
    fun setScrollTouchGuardEnabled(enabled: Boolean) {
        repositoryScope.launch { settingsDataStore.saveScrollTouchGuard(enabled) }
    }

    val updateChannel = settingsDataStore.updateChannelFlow.stateIn(
        repositoryScope, SharingStarted.Eagerly, com.mioo.dao.utils.UpdateChannel.STABLE
    )
    fun setUpdateChannel(channel: com.mioo.dao.utils.UpdateChannel) {
        repositoryScope.launch { settingsDataStore.saveUpdateChannel(channel) }
    }
    val predictiveBackEnabled = settingsDataStore.predictiveBackFlow.stateIn(
        repositoryScope, SharingStarted.Eagerly, false
    )

    fun setPredictiveBackEnabled(enabled: Boolean) {
        repositoryScope.launch { settingsDataStore.savePredictiveBack(enabled) }
    }

    val thirdPartyConsent = settingsDataStore.thirdPartyConsent.stateIn(
        repositoryScope, SharingStarted.Eagerly, null
    )

    suspend fun saveThirdPartyConsent(allowed: Boolean) = settingsDataStore.saveThirdPartyConsent(allowed)

    val shareCardBrandingDefault = settingsDataStore.showShareCardBrandingFlow.stateIn(
        repositoryScope, SharingStarted.Eagerly, null
    )

    val forumPreviewLines = settingsDataStore.forumPreviewLinesFlow

    fun updateForumPreviewLines(lines: Int) {
        repositoryScope.launch { settingsDataStore.saveForumPreviewLines(lines) }
    }

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
        .combine(settingsDataStore.confirmCookieBeforeSendFlow) { settings, enabled ->
            settings.copy(confirmCookieBeforeSend = enabled)
        }
        .combine(settingsDataStore.showShareCardBrandingFlow) { settings, branding ->
            settings.copy(showShareCardBranding = branding)
        }
        .combine(settingsDataStore.diceBadgesEnabledFlow) { settings, diceBadgesEnabled ->
            settings.copy(diceBadgesEnabled = diceBadgesEnabled)
        }
        .combine(settingsDataStore.creatorModeFlow) { settings, enabled ->
            settings.copy(enableCreatorMode = enabled)
        }
        .combine(settingsDataStore.networkCompatibilityFlow) { settings, enabled ->
            settings.copy(networkCompatibilityEnabled = enabled)
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

    fun setShowShareCardBranding(enabled: Boolean) {
        repositoryScope.launch { settingsDataStore.setShowShareCardBranding(enabled) }
    }

    fun updateConfirmCookieBeforeSend(enabled: Boolean) {
        repositoryScope.launch { settingsDataStore.saveConfirmCookieBeforeSend(enabled) }
    }

    suspend fun saveCreatorMode(enabled: Boolean) = settingsDataStore.saveCreatorMode(enabled)

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

    fun updateNetworkCompatibility(enabled: Boolean) {
        repositoryScope.launch { settingsDataStore.saveNetworkCompatibility(enabled) }
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

    fun updateGlobalFont(name: String) {
        repositoryScope.launch { settingsDataStore.saveGlobalFont(name) }
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
            settingsDataStore.changeCookie(cookie, remove = false)
        }
    }

    fun selectCookieIndex(index: Int) {
        repositoryScope.launch {
            settingsDataStore.selectCookie(index)
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
            settingsDataStore.changeCookie(cookie, remove = true)
        }
    }

    fun setCookieNote(cookie: String, note: String) {
        repositoryScope.launch {
            settingsDataStore.updateCookieNote(cookie, note)
        }
    }

    fun updateAuthCookie(authCookie: String) {
        repositoryScope.launch {
            settingsDataStore.saveAuthCookie(authCookie)
        }
    }

    fun addFeedFolder(folder: com.mioo.dao.data.model.FeedFolder) {
        repositoryScope.launch {
            settingsDataStore.changeFeedFolder(null, folder)
        }
    }

    fun updateFeedFolder(oldUuid: String, folder: com.mioo.dao.data.model.FeedFolder) {
        repositoryScope.launch {
            settingsDataStore.changeFeedFolder(oldUuid, folder)
        }
    }

    fun removeFeedFolder(uuid: String) {
        repositoryScope.launch {
            settingsDataStore.changeFeedFolder(uuid, null)
        }
    }

    fun addBlockedThread(threadId: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_BLOCKED_THREADS) { if (threadId !in it) it.add(threadId) }
        }
    }

    fun removeBlockedThread(threadId: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_BLOCKED_THREADS) { it.remove(threadId) }
        }
    }

    fun addBlockedUser(userHash: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_BLOCKED_USERS) { if (userHash !in it) it.add(userHash) }
        }
    }

    fun removeBlockedUser(userHash: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_BLOCKED_USERS) { it.remove(userHash) }
        }
    }

    fun togglePinForum(forumId: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_PINNED_FORUMS) { if (!it.remove(forumId)) it.add(forumId) }
        }
    }

    fun toggleForumGroupExpand(groupId: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_EXPANDED_FORUM_GROUPS) { if (!it.remove(groupId)) it.add(groupId) }
        }
    }

    fun addBlockedKeyword(keyword: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_BLOCKED_KEYWORDS) { if (keyword.isNotBlank() && keyword !in it) it.add(keyword) }
        }
    }

    fun removeBlockedKeyword(keyword: String) {
        repositoryScope.launch {
            settingsDataStore.updateStringList(SettingsDataStore.KEY_BLOCKED_KEYWORDS) { it.remove(keyword) }
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
