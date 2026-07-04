package com.mioo.dao.data.api

import com.mioo.dao.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CookieInterceptor @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val selectedIndex = runBlocking { settingsDataStore.selectedCookieIndexFlow.first() }
        val cookiesList = runBlocking { settingsDataStore.cookiesListFlow.first() }
        val oldUserHash = runBlocking { settingsDataStore.userHashFlow.first() }
        val authCookie = runBlocking { settingsDataStore.authCookieFlow.first() }
        
        var activeCookie = if (selectedIndex in cookiesList.indices) {
            cookiesList[selectedIndex]
        } else {
            oldUserHash
        }
        
        if (activeCookie?.startsWith("{") == true) {
            try {
                val json = org.json.JSONObject(activeCookie)
                val hash = json.optString("cookie", "")
                if (hash.isNotEmpty()) {
                    activeCookie = hash
                } else {
                    val userHash = json.optString("userhash", "")
                    if (userHash.isNotEmpty()) {
                        activeCookie = userHash
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        val requestBuilder = originalRequest.newBuilder()
        requestBuilder.header("User-Agent", "HavfunClient-Android")

        val cookies = mutableListOf<String>()
        if (!activeCookie.isNullOrBlank()) {
            cookies.add("userhash=$activeCookie")
        }
        var actualAuthCookie = authCookie
        if (actualAuthCookie?.startsWith("{") == true) {
            try {
                val json = org.json.JSONObject(actualAuthCookie)
                val hash = json.optString("cookie", "")
                if (hash.isNotEmpty()) {
                    actualAuthCookie = hash
                } else {
                    val userHash = json.optString("userhash", "")
                    if (userHash.isNotEmpty()) {
                        actualAuthCookie = userHash
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        if (!actualAuthCookie.isNullOrBlank()) {
            cookies.add("auth=$actualAuthCookie")
        }

        if (cookies.isNotEmpty()) {
            requestBuilder.header("Cookie", cookies.joinToString("; "))
        }

        return chain.proceed(requestBuilder.build())
    }
}
