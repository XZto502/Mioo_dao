package com.mioo.dao.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mioo_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_USER_HASH = stringPreferencesKey("user_hash")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_CDN_PATH = stringPreferencesKey("cdn_path")
        val KEY_LAST_FORUM_ID = stringPreferencesKey("last_forum_id")
        val KEY_LAST_FORUM_NAME = stringPreferencesKey("last_forum_name")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
        val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        val KEY_COOKIES_LIST = stringPreferencesKey("cookies_list")
        val KEY_SELECTED_COOKIE_INDEX = intPreferencesKey("selected_cookie_index")
        val KEY_AUTH_COOKIE = stringPreferencesKey("auth_cookie")
        val KEY_FEED_FOLDERS = stringPreferencesKey("feed_folders")
        val KEY_BLOCKED_THREADS = stringPreferencesKey("blocked_threads")
        val KEY_BLOCKED_USERS = stringPreferencesKey("blocked_users")
        val KEY_PINNED_FORUMS = stringPreferencesKey("pinned_forums")
        val KEY_BLOCKED_KEYWORDS = stringPreferencesKey("blocked_keywords")
        val KEY_THREAD_DRAFTS = stringPreferencesKey("thread_drafts")
        val KEY_NEW_THREAD_DRAFT = stringPreferencesKey("new_thread_draft")
        val KEY_SMART_PRELOAD_MODE = stringPreferencesKey("smart_preload_mode")
        val KEY_PRELOAD_COUNT = intPreferencesKey("preload_count")
        val KEY_ENABLE_WATERMARK = booleanPreferencesKey("enable_watermark")
    }

    val userHashFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_USER_HASH]
        }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_DARK_MODE] ?: false
        }

    val cdnPathFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_CDN_PATH]
        }

    val lastForumIdFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_LAST_FORUM_ID]
        }

    val lastForumNameFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_LAST_FORUM_NAME]
        }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_THEME_MODE] ?: "SYSTEM"
        }

    val fontSizeScaleFlow: Flow<Float> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_FONT_SIZE_SCALE] ?: 1.0f
        }

    val themeColorFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_THEME_COLOR] ?: "dynamic"
        }

    val cookiesListFlow: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val listStr = preferences[KEY_COOKIES_LIST] ?: ""
            if (listStr.startsWith("[")) {
                try {
                    val jsonArray = org.json.JSONArray(listStr)
                    val list = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                    list
                } catch (e: Exception) {
                    listStr.split(",").filter { it.isNotBlank() }
                }
            } else {
                listStr.split(",").filter { it.isNotBlank() }
            }
        }

    val selectedCookieIndexFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_SELECTED_COOKIE_INDEX] ?: 0
        }

    val authCookieFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_AUTH_COOKIE] ?: ""
        }

    val feedFoldersFlow: Flow<List<com.mioo.dao.data.model.FeedFolder>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonStr = preferences[KEY_FEED_FOLDERS]
            val list = mutableListOf<com.mioo.dao.data.model.FeedFolder>()
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val uuid = obj.optString("uuid", "")
                        if (uuid.isNotEmpty()) {
                            list.add(com.mioo.dao.data.model.FeedFolder(name, uuid))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            list
        }

    val blockedThreadsFlow: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonStr = preferences[KEY_BLOCKED_THREADS]
            val list = mutableListOf<String>()
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            list
        }

    val blockedUsersFlow: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonStr = preferences[KEY_BLOCKED_USERS]
            val list = mutableListOf<String>()
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            list
        }

    val pinnedForumsFlow: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonStr = preferences[KEY_PINNED_FORUMS]
            val list = mutableListOf<String>()
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            list
        }

    val blockedKeywordsFlow: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonStr = preferences[KEY_BLOCKED_KEYWORDS]
            val list = mutableListOf<String>()
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            list
        }

    val smartPreloadModeFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_SMART_PRELOAD_MODE] ?: "WIFI_ONLY"
        }

    val preloadCountFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_PRELOAD_COUNT] ?: 10
        }

    val enableWatermarkFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_ENABLE_WATERMARK] ?: true
        }

    suspend fun saveUserHash(userHash: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_HASH] = userHash
        }
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_MODE] = enabled
        }
    }

    suspend fun saveSmartPreloadMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SMART_PRELOAD_MODE] = mode
        }
    }

    suspend fun savePreloadCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PRELOAD_COUNT] = count
        }
    }

    suspend fun saveEnableWatermark(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ENABLE_WATERMARK] = enabled
        }
    }

    suspend fun saveCdnPath(cdnPath: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CDN_PATH] = cdnPath
        }
    }

    suspend fun saveLastForum(id: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_FORUM_ID] = id
            preferences[KEY_LAST_FORUM_NAME] = name
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }

    suspend fun saveFontSizeScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE_SCALE] = scale
        }
    }

    suspend fun saveThemeColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_COLOR] = color
        }
    }

    suspend fun saveCookiesList(list: List<String>) {
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            for (item in list) {
                jsonArray.put(item)
            }
            preferences[KEY_COOKIES_LIST] = jsonArray.toString()
        }
    }

    suspend fun saveSelectedCookieIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_COOKIE_INDEX] = index
        }
    }

    suspend fun saveFeedFolders(folders: List<com.mioo.dao.data.model.FeedFolder>) {
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            for (folder in folders) {
                val obj = org.json.JSONObject()
                obj.put("name", folder.name)
                obj.put("uuid", folder.uuid)
                jsonArray.put(obj)
            }
            preferences[KEY_FEED_FOLDERS] = jsonArray.toString()
        }
    }

    suspend fun saveAuthCookie(authCookie: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTH_COOKIE] = authCookie
        }
    }

    suspend fun saveBlockedThreads(list: List<String>) {
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            for (item in list) {
                jsonArray.put(item)
            }
            preferences[KEY_BLOCKED_THREADS] = jsonArray.toString()
        }
    }

    suspend fun saveBlockedUsers(list: List<String>) {
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            for (item in list) {
                jsonArray.put(item)
            }
            preferences[KEY_BLOCKED_USERS] = jsonArray.toString()
        }
    }

    suspend fun savePinnedForums(list: List<String>) {
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            for (item in list) {
                jsonArray.put(item)
            }
            preferences[KEY_PINNED_FORUMS] = jsonArray.toString()
        }
    }
    suspend fun saveBlockedKeywords(list: List<String>) {
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            for (item in list) {
                jsonArray.put(item)
            }
            preferences[KEY_BLOCKED_KEYWORDS] = jsonArray.toString()
        }
    }

    suspend fun getThreadDraft(threadId: String): String {
        val preferences = context.dataStore.data.first()
        val jsonStr = preferences[KEY_THREAD_DRAFTS] ?: ""
        if (jsonStr.isNotEmpty()) {
            try {
                val jsonObject = org.json.JSONObject(jsonStr)
                return jsonObject.optString(threadId, "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }

    suspend fun saveThreadDraft(threadId: String, draft: String) {
        context.dataStore.edit { preferences ->
            val jsonStr = preferences[KEY_THREAD_DRAFTS] ?: ""
            val jsonObject = if (jsonStr.isNotEmpty()) {
                try {
                    org.json.JSONObject(jsonStr)
                } catch (e: Exception) {
                    org.json.JSONObject()
                }
            } else {
                org.json.JSONObject()
            }
            if (draft.isEmpty()) {
                jsonObject.remove(threadId)
            } else {
                jsonObject.put(threadId, draft)
            }
            preferences[KEY_THREAD_DRAFTS] = jsonObject.toString()
        }
    }

    suspend fun getNewThreadDraft(): String {
        val preferences = context.dataStore.data.first()
        return preferences[KEY_NEW_THREAD_DRAFT] ?: ""
    }

    suspend fun saveNewThreadDraft(draft: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NEW_THREAD_DRAFT] = draft
        }
    }

    suspend fun clearUserHash() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_USER_HASH)
        }
    }

    /**
     * Synchronously read last forum ID (blocking, use only during init).
     */
    suspend fun getLastForumId(): String? = lastForumIdFlow.first()

    /**
     * Synchronously read last forum name (blocking, use only during init).
     */
    suspend fun getLastForumName(): String? = lastForumNameFlow.first()

}
