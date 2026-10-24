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
        val originalRequest = chain.request()
        // 图床不需要、也不该收到发言饼干
        if (!shouldAttachCookie(originalRequest.url.host)) {
            return chain.proceed(originalRequest)
        }
        val settings = settingsRepository.settings.value

        // X-Mioo-Cookie：串级饼干覆盖（每串记忆），优先于全局选中
        var activeCookie = extractHash(originalRequest.header("X-Mioo-Cookie"))
            ?: settings.cookiesList.getOrNull(settings.selectedCookieIndex)?.let { extractHash(it) }
            ?: settings.cookie.takeIf { it.isNotBlank() }?.let { extractHash(it) }

        var authCookie = extractHash(settings.authCookie.takeIf { it.isNotBlank() })

        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "HavfunClient-Android")
        requestBuilder.removeHeader("X-Mioo-Cookie")

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

    private fun shouldAttachCookie(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized != "image.nmb.best" &&
            !normalized.startsWith("image.") &&
            !normalized.startsWith("img.")
    }

    companion object {
        /**
         * 用户粘贴的饼干可能是 X岛导出的 JSON（{"cookie":"..."}）；归一化为纯 hash。
         * JSON 里没有 cookie/userhash 字段时返回 null（视为未配置，而不是把整段 JSON 发出去）。
         */
        fun extractHash(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            if (!raw.startsWith("{")) return raw
            return try {
                val json = org.json.JSONObject(raw)
                json.optString("cookie", "")
                    .ifEmpty { json.optString("userhash", "") }
                    .ifEmpty { null }
            } catch (_: Exception) {
                // 不是合法 JSON 的花括号串按原样处理
                raw
            }
        }
    }
}
