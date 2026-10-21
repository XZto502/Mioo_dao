package com.mioo.dao.data.cdn

import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.api.extractQuotedJsonString
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.ui.components.ImageCdnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCdn @Inject constructor(
    private val api: XdApiService,
    private val settingsDataStore: SettingsDataStore
) {
    private val _base = MutableStateFlow(ImageCdnState.DEFAULT_BASE)
    val base: StateFlow<String> = _base.asStateFlow()

    /** 先用本地缓存，再请求 getCDNPath。失败时保留已有地址。 */
    suspend fun warm() {
        publishCached()
        runCatching { fetchAndStore() }
    }

    suspend fun refresh(): String {
        publishCached()
        return fetchAndStore()
    }

    private suspend fun publishCached() {
        val cached = settingsDataStore.cdnPathFlow.firstOrNull()?.trim()?.trimEnd('/')
        if (!cached.isNullOrBlank()) {
            publish(cached)
        }
    }

    private suspend fun fetchAndStore(): String {
        val parsed = parseCdnBody(api.getCDNPath().string())
            ?: throw java.io.IOException("CDN 地址无法识别")
        settingsDataStore.saveCdnPath(parsed)
        publish(parsed)
        return _base.value
    }

    private suspend fun publish(url: String) {
        withContext(Dispatchers.Main.immediate) {
            ImageCdnState.publish(url)
            _base.value = ImageCdnState.baseUrl
        }
    }

    private fun parseCdnBody(raw: String): String? {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        val quoted = extractQuotedJsonString(trimmed)
        val text = (quoted ?: trimmed).trim()
        if (text.startsWith("{")) {
            val obj = runCatching { org.json.JSONObject(text) }.getOrNull()
            if (obj != null) {
                val field = sequenceOf("url", "cdn", "path", "cdnPath", "CDNPath", "data")
                    .map { obj.optString(it) }
                    .firstOrNull { it.isNotBlank() }
                if (field != null) return ImageCdnState.normalizeBaseOrNull(field)
            }
        }
        if (text.startsWith("[")) {
            val first = runCatching { org.json.JSONArray(text).optString(0) }.getOrNull()
            if (!first.isNullOrBlank()) return ImageCdnState.normalizeBaseOrNull(first)
        }
        return ImageCdnState.normalizeBaseOrNull(text)
    }
}
