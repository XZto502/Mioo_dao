package com.mioo.dao.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.model.UserSettings
import com.mioo.dao.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.model.GithubRelease
import javax.inject.Inject

import com.mioo.dao.data.local.HistoryEntity
import com.mioo.dao.data.repository.ThreadRepository
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val threadRepository: ThreadRepository
) : ViewModel() {

    val settingsState: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

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

    fun addBlockedKeyword(keyword: String) {
        settingsRepository.addBlockedKeyword(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        settingsRepository.removeBlockedKeyword(keyword)
    }

    suspend fun getNewThreadDraft(): String {
        return settingsRepository.getNewThreadDraft()
    }

    suspend fun saveNewThreadDraft(draft: String) {
        settingsRepository.saveNewThreadDraft(draft)
    }

    private val _cacheSizeState = MutableStateFlow("0.0 KB")
    val cacheSizeState: StateFlow<String> = _cacheSizeState.asStateFlow()

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

    fun clearCache() {
        viewModelScope.launch {
            threadRepository.clearCache().collect { response ->
                if (response is XdResponse.Success) {
                    updateCacheSize()
                }
            }
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

    fun checkUpdate(onResult: (XdResponse<GithubRelease>) -> Unit) {
        viewModelScope.launch {
            threadRepository.checkLatestRelease().collect { response ->
                onResult(response)
            }
        }
    }
}
