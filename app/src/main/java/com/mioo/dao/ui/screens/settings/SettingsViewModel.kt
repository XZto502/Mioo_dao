package com.mioo.dao.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.model.UserSettings
import com.mioo.dao.data.repository.BackupRepository
import com.mioo.dao.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.utils.FontStore
import javax.inject.Inject

import com.mioo.dao.data.local.HistoryEntity
import com.mioo.dao.data.repository.ThreadRepository
import kotlinx.coroutines.launch

/** Minimal slice for ThreadScreen — avoids recomposing on theme/font/etc. changes. */
@Immutable
data class ThreadScreenSettings(
    val cookiesList: List<String> = emptyList(),
    val selectedCookieIndex: Int = 0,
    val blockedThreads: List<String> = emptyList(),
    val quoteAutoDisplayEnabled: Boolean = true,
    val diceBadgesEnabled: Boolean = true
)

/** Minimal slice for ForumScreen — pins + compose cookies only. */
@Immutable
data class ForumScreenSettings(
    val cookiesList: List<String> = emptyList(),
    val selectedCookieIndex: Int = 0,
    val pinnedForums: List<String> = emptyList(),
    val expandedForumGroups: List<String> = emptyList(),
    val draftBoxEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val threadRepository: ThreadRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    val settingsState: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    val threadScreenSettings: StateFlow<ThreadScreenSettings> = settingsRepository.settings
        .map {
            ThreadScreenSettings(
                cookiesList = it.cookiesList,
                selectedCookieIndex = it.selectedCookieIndex,
                blockedThreads = it.blockedThreads,
                quoteAutoDisplayEnabled = it.quoteAutoDisplayEnabled,
                diceBadgesEnabled = it.diceBadgesEnabled
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThreadScreenSettings()
        )

    val forumScreenSettings: StateFlow<ForumScreenSettings> = settingsRepository.settings
        .map {
            ForumScreenSettings(
                cookiesList = it.cookiesList,
                selectedCookieIndex = it.selectedCookieIndex,
                pinnedForums = it.pinnedForums,
                expandedForumGroups = it.expandedForumGroups,
                draftBoxEnabled = it.draftBoxEnabled
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ForumScreenSettings()
        )

    private var newThreadDraftJob: Job? = null
    private companion object {
        const val NEW_THREAD_DRAFT_DEBOUNCE_MS = 400L
    }

    val historyState: StateFlow<List<HistoryEntity>> = threadRepository.getHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearHistory() {
        viewModelScope.launch {
            threadRepository.clearHistory()
        }
    }

    fun updateCookie(cookie: String) {
        settingsRepository.updateCookie(cookie)
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        settingsRepository.updateThemeMode(themeMode)
    }

    fun updateFontSize(scale: Float) {
        settingsRepository.updateFontSizeScale(scale)
    }

    fun updateLineHeight(scale: Float) {
        settingsRepository.updateLineHeightScale(scale)
    }

    private val _fontsState = MutableStateFlow<List<String>>(emptyList())
    val fontsState: StateFlow<List<String>> = _fontsState.asStateFlow()

    fun refreshFonts(context: android.content.Context) {
        _fontsState.value = FontStore.list(context).map { it.name }
    }

    /** 导入字体文件，成功返回 true。 */
    fun importFont(context: android.content.Context, uri: android.net.Uri): Boolean {
        val name = FontStore.import(context, uri)
        if (name != null) refreshFonts(context)
        return name != null
    }

    /** 删除字体；若正被中文/英文槽位使用则同时清空该槽位。 */
    fun deleteFont(context: android.content.Context, name: String) {
        FontStore.delete(context, name)
        refreshFonts(context)
        val s = settingsState.value
        if (s.chineseFont == name) settingsRepository.updateChineseFont("")
        if (s.englishFont == name) settingsRepository.updateEnglishFont("")
    }

    fun updateChineseFont(name: String) {
        settingsRepository.updateChineseFont(name)
    }

    fun updateEnglishFont(name: String) {
        settingsRepository.updateEnglishFont(name)
    }

    fun updateThemeColor(color: String) {
        settingsRepository.updateThemeColor(color)
    }

    fun addCookie(cookie: String) {
        settingsRepository.addCookie(cookie)
    }

    fun selectCookie(index: Int) {
        settingsRepository.selectCookieIndex(index)
    }

    fun deleteCookie(cookie: String) {
        settingsRepository.deleteCookie(cookie)
    }

    fun setCookieNote(cookie: String, note: String) {
        settingsRepository.setCookieNote(cookie, note)
    }

    fun exportCookiesJson(): String = settingsRepository.exportCookiesJson()

    fun importCookiesJson(json: String): Int = settingsRepository.importCookiesJson(json)

    suspend fun buildBackup(sections: Set<String>): String =
        backupRepository.exportBackup(sections)

    suspend fun restoreBackup(json: String): BackupRepository.ImportResult =
        backupRepository.importBackup(json)

    fun updateAuthCookie(authCookie: String) {
        settingsRepository.updateAuthCookie(authCookie)
    }

    fun addFeedFolder(name: String, uuid: String) {
        settingsRepository.addFeedFolder(com.mioo.dao.data.model.FeedFolder(name, uuid))
    }

    fun removeFeedFolder(uuid: String) {
        settingsRepository.removeFeedFolder(uuid)
    }

    fun addBlockedThread(threadId: String) {
        settingsRepository.addBlockedThread(threadId)
    }

    fun removeBlockedThread(threadId: String) {
        settingsRepository.removeBlockedThread(threadId)
    }

    fun addBlockedUser(userHash: String) {
        settingsRepository.addBlockedUser(userHash)
    }

    fun removeBlockedUser(userHash: String) {
        settingsRepository.removeBlockedUser(userHash)
    }



    fun togglePinForum(forumId: String) {
        settingsRepository.togglePinForum(forumId)
    }

    fun toggleForumGroupExpand(groupId: String) {
        settingsRepository.toggleForumGroupExpand(groupId)
    }

    fun addBlockedKeyword(keyword: String) {
        settingsRepository.addBlockedKeyword(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        settingsRepository.removeBlockedKeyword(keyword)
    }

    suspend fun getNewThreadDraft(): String {
        return settingsRepository.getNewThreadDraft()
    }

    /** Debounced draft write — safe to call every keystroke. */
    fun saveNewThreadDraft(draft: String) {
        newThreadDraftJob?.cancel()
        newThreadDraftJob = viewModelScope.launch {
            delay(NEW_THREAD_DRAFT_DEBOUNCE_MS)
            settingsRepository.saveNewThreadDraft(draft)
        }
    }

    /** Immediate clear/flush (submit success or explicit discard). */
    fun clearNewThreadDraft() {
        newThreadDraftJob?.cancel()
        viewModelScope.launch {
            settingsRepository.saveNewThreadDraft("")
        }
    }

    private val _cacheSizeState = MutableStateFlow("0.0 KB")
    val cacheSizeState: StateFlow<String> = _cacheSizeState.asStateFlow()

    private val _imageCacheSizeState = MutableStateFlow("0.00 B")
    val imageCacheSizeState: StateFlow<String> = _imageCacheSizeState.asStateFlow()

    private val _preloadProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val preloadProgressState: StateFlow<Pair<Int, Int>?> = _preloadProgress.asStateFlow()

    private val _isPreloading = MutableStateFlow(false)
    val isPreloadingState: StateFlow<Boolean> = _isPreloading.asStateFlow()

    init {
        updateCacheSize()
    }

    fun updateCacheSize() {
        viewModelScope.launch {
            threadRepository.getCacheSize().collect { size ->
                _cacheSizeState.value = size
            }
        }
    }

    fun updateImageCacheSize(context: android.content.Context) {
        viewModelScope.launch {
            _imageCacheSizeState.value = com.mioo.dao.utils.StorageUtil.getImageDiskCacheSizeFormatted(context)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            threadRepository.clearCache().collect { response ->
                if (response is XdResponse.Success) {
                    updateCacheSize()
                }
            }
        }
    }

    fun clearImageCache(context: android.content.Context) {
        viewModelScope.launch {
            com.mioo.dao.utils.StorageUtil.clearImageCache(context)
            updateImageCacheSize(context)
        }
    }

    fun preloadBookmarks() {
        if (_isPreloading.value) return
        _isPreloading.value = true
        viewModelScope.launch {
            threadRepository.preloadBookmarks { current, total ->
                _preloadProgress.value = Pair(current, total)
            }.collect { response ->
                _isPreloading.value = false
                _preloadProgress.value = null
                updateCacheSize()
            }
        }
    }

    fun updateSmartPreloadMode(mode: String) {
        settingsRepository.updateSmartPreloadMode(mode)
    }

    fun updatePreloadCount(count: Int) {
        settingsRepository.updatePreloadCount(count)
    }

    fun updateSubscriptionNotifications(enabled: Boolean) {
        settingsRepository.updateSubscriptionNotifications(enabled)
    }

    fun updateNotificationIntervalMinutes(minutes: Int) {
        settingsRepository.updateNotificationIntervalMinutes(minutes)
    }

    fun updateGlassEffectEnabled(enabled: Boolean) {
        settingsRepository.updateGlassEffectEnabled(enabled)
    }

    fun updateDiceBadgesEnabled(enabled: Boolean) {
        settingsRepository.updateDiceBadgesEnabled(enabled)
    }

    fun setShowShareCardBranding(enabled: Boolean) {
        settingsRepository.setShowShareCardBranding(enabled)
    }

    fun updateDraftBoxEnabled(enabled: Boolean) {
        settingsRepository.updateDraftBoxEnabled(enabled)
    }

    fun updateQuoteAutoDisplay(enabled: Boolean) {
        settingsRepository.updateQuoteAutoDisplay(enabled)
    }

    fun updateDataSaverEnabled(enabled: Boolean) {
        settingsRepository.updateDataSaverEnabled(enabled)
    }

    fun updateCacheLimitMb(mb: Int) {
        settingsRepository.updateCacheLimitMb(mb)
    }

    fun checkUpdate(onResult: (XdResponse<GithubRelease>) -> Unit) {
        viewModelScope.launch {
            threadRepository.checkLatestRelease().collect { response ->
                onResult(response)
            }
        }
    }
}
