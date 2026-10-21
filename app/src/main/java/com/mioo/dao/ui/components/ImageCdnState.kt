package com.mioo.dao.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 当前图片 CDN。列表缩略图读取这里，CDN 更新后不必重造每条 PostData。
 */
object ImageCdnState {
    const val DEFAULT_BASE = "https://image.nmb.best"
    const val FALLBACK_BASE = "https://nmbimg.fastmirror.org"

    var baseUrl by mutableStateOf(DEFAULT_BASE)
        private set

    fun publish(url: String) {
        val clean = normalizeBase(url) ?: return
        if (clean != baseUrl) baseUrl = clean
    }

    /** 纯函数，可在后台线程调用。无法识别时返回 null。 */
    fun normalizeBaseOrNull(raw: String): String? = normalizeBase(raw)

    fun imageUrl(filename: String?): String? {
        if (filename.isNullOrBlank()) return null
        if (filename.startsWith("http://") || filename.startsWith("https://")) return fullSize(filename)
        return "${baseUrl.trimEnd('/')}/image/${filename.trimStart('/')}"
    }

    /** 点击看大图：换到当前 CDN 的 /image/。非岛图原样返回。 */
    fun fullSize(url: String): String {
        val name = islandFilename(url) ?: return url
        return "${baseUrl.trimEnd('/')}/image/$name"
    }

    /**
     * 列表加载顺序：当前 CDN 的 /thumb/，再 /image/，然后备用域名，最后原链接所在域名。
     */
    fun thumbCandidates(url: String): List<String> {
        val name = islandFilename(url) ?: return listOf(url)
        val hosts = LinkedHashSet<String>()
        hosts.add(baseUrl.trimEnd('/'))
        hosts.add(FALLBACK_BASE)
        originOf(url)?.let { hosts.add(it) }
        return hosts.flatMap { host ->
            listOf("$host/thumb/$name", "$host/image/$name")
        }.distinct()
    }

    private fun originOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val pathStart = url.indexOf('/', schemeEnd + 3)
        if (pathStart <= 0) return null
        return url.substring(0, pathStart).trimEnd('/')
    }

    private fun islandFilename(url: String): String? {
        val marker = when {
            "/image/" in url -> "/image/"
            "/thumb/" in url -> "/thumb/"
            else -> return null
        }
        val name = url.substringAfter(marker).substringBefore('?').substringBefore('#')
        if (name.isBlank() || '/' in name || ' ' in name) return null
        return name
    }

    private fun normalizeBase(raw: String): String? {
        var url = raw.trim().trim('"').trimEnd('/')
        if (url.startsWith("//")) url = "https:$url"
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        url = url.removeSuffix("/image").removeSuffix("/thumb").trimEnd('/')
        return url
    }
}
