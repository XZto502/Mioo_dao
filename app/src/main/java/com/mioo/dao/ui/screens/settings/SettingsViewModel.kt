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
}
