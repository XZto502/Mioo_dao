package com.mioo.dao.data.api

import com.mioo.dao.data.repository.SettingsRepository
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches userhash / auth cookies without blocking the OkHttp dispatcher.
 * Reads the in-memory [SettingsRepository.settings] snapshot (eagerly shared),
 * so list scrolling + pagination never pay a DataStore round-trip per request.
 */
@Singleton
class CookieInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = settingsRepository.settings.value
        val originalRequest = chain.request()

        var activeCookie = settings.cookiesList.getOrNull(settings.selectedCookieIndex)
            ?: settings.cookie.takeIf { it.isNotBlank() }
        activeCookie = extractHash(activeCookie)

        var authCookie = extractHash(settings.authCookie.takeIf { it.isNotBlank() })

        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "HavfunClient-Android")

        val cookieParts = ArrayList<String>(2)
        if (!activeCookie.isNullOrBlank()) {
            cookieParts.add("userhash=$activeCookie")
        }
        if (!authCookie.isNullOrBlank()) {
            cookieParts.add("auth=$authCookie")
        }
        if (cookieParts.isNotEmpty()) {
            requestBuilder.header("Cookie", cookieParts.joinToString("; "))
        }

        return chain.proceed(requestBuilder.build())
    }

    private fun extractHash(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (!raw.startsWith("{")) return raw
        return try {
            val json = org.json.JSONObject(raw)
            json.optString("cookie", "")
                .ifEmpty { json.optString("userhash", "") }
                .ifEmpty { raw }
        } catch (_: Exception) {
            raw
        }
    }
}
