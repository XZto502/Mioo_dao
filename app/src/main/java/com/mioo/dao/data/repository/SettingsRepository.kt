package com.mioo.dao.data.repository

import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    val settings: StateFlow<UserSettings> = combine(
        settingsDataStore.userHashFlow,
        settingsDataStore.themeModeFlow,
        settingsDataStore.fontSizeScaleFlow,
        settingsDataStore.themeColorFlow,
        settingsDataStore.cookiesListFlow,
        settingsDataStore.selectedCookieIndexFlow,
        settingsDataStore.authCookieFlow,
        settingsDataStore.feedFoldersFlow,
        settingsDataStore.blockedThreadsFlow,
        settingsDataStore.blockedUsersFlow,
        settingsDataStore.pinnedForumsFlow,
        settingsDataStore.blockedKeywordsFlow
    ) { args ->
        val cookie = args[0] as? String
        val themeModeStr = args[1] as String
        val fontSizeScale = args[2] as Float
        val themeColor = args[3] as String
        @Suppress("UNCHECKED_CAST")
        val cookiesList = args[4] as List<String>
        val selectedIndex = args[5] as Int
        val authCookie = args[6] as? String
        @Suppress("UNCHECKED_CAST")
        val feedFolders = args[7] as List<com.mioo.dao.data.model.FeedFolder>
        @Suppress("UNCHECKED_CAST")
        val blockedThreads = args[8] as List<String>
        @Suppress("UNCHECKED_CAST")
        val blockedUsers = args[9] as List<String>
        @Suppress("UNCHECKED_CAST")
        val pinnedForums = args[10] as List<String>
        @Suppress("UNCHECKED_CAST")
        val blockedKeywords = args[11] as List<String>

        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        UserSettings(
            cookie = cookie ?: "",
            themeMode = themeMode,
            fontSizeScale = fontSizeScale,
            themeColor = themeColor,
            cookiesList = cookiesList,
            selectedCookieIndex = selectedIndex,
            authCookie = authCookie ?: "",
            feedFolders = feedFolders,
            blockedThreads = blockedThreads,
            blockedUsers = blockedUsers,
            pinnedForums = pinnedForums,
            blockedKeywords = blockedKeywords
        )
    }.stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings()
    )

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

    fun updateThemeColor(color: String) {
        repositoryScope.launch {
            settingsDataStore.saveThemeColor(color)
        }
    }

    fun addCookie(cookie: String) {
        repositoryScope.launch {
            val currentList = settings.value.cookiesList.toMutableList()
            if (cookie.isNotBlank() && !currentList.contains(cookie)) {
                currentList.add(cookie)
                settingsDataStore.saveCookiesList(currentList)
                if (currentList.size == 1) {
                    settingsDataStore.saveSelectedCookieIndex(0)
                    settingsDataStore.saveUserHash(cookie)
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

    fun deleteCookie(cookie: String) {
        repositoryScope.launch {
            val currentList = settings.value.cookiesList.toMutableList()
            val indexToDelete = currentList.indexOf(cookie)
            if (indexToDelete != -1) {
                currentList.removeAt(indexToDelete)
                settingsDataStore.saveCookiesList(currentList)
                
                val newIndex = if (currentList.isEmpty()) 0 else {
                    val prevIndex = settings.value.selectedCookieIndex
                    if (prevIndex >= currentList.size) currentList.size - 1 else prevIndex
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
}
