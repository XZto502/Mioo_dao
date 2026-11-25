package com.mioo.dao.utils

import android.content.Intent
import android.net.Uri
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Extract X-island thread ids from share intents / deep links / clipboard.
 * Supports common patterns:
 * - https://www.nmbxd.com/t/123
 * - https://www.nmbxd1.com/t/123
 * - https://nmbxd.com/Forum/thread/id/123
 * - plain "123" / "No.123" / ">>No.123"
 * - clipboard: standalone 8-digit thread No. (current X-island id length)
 */
object ThreadLinkParser {

    private val pathPatterns = listOf(
        Regex("""/(?:t|thread)(?:/id)?/(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""[?&]id=(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""No\.(\d+)""", RegexOption.IGNORE_CASE),
        Regex(""">>(\d+)""")
    )

    /**
     * Exactly 8 digits, not part of a longer number (clipboard auto-jump).
     * Uses a non-lookbehind pattern for better Android/Java regex compatibility.
     */
    private val eightDigitPattern = Regex("""(?:^|[^0-9])([0-9]{8})(?:[^0-9]|$)""")

    private val xIslandRootDomains = setOf(
        "nmbxd.com",
        "nmbxd1.com"
    )

    private val webThreadPathPattern = Regex(
        """(?:^|/)(?:m/)?(?:t|thread)(?:/id)?/(\d+)(?:\.html?)?(?:/|$)""",
        RegexOption.IGNORE_CASE
    )

    private val webThreadQueryEndpointPattern = Regex(
        """(?:^|/)(?:m/)?(?:t|thread)(?:\.html?)?/?$""",
        RegexOption.IGNORE_CASE
    )

    fun parseThreadId(intent: Intent?): String? {
        if (intent == null) return null
        // VIEW deep link
        intent.data?.let { uri ->
            parseFromUri(uri)?.let { return it }
        }
        // SEND text share
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            parseFromText(text)?.let { return it }
        }
        return null
    }

    fun parseFromUri(uri: Uri): String? {
        val full = uri.toString()
        parseFromText(full)?.let { return it }
        // last path segment if numeric
        val last = uri.lastPathSegment
        if (!last.isNullOrBlank() && last.all(Char::isDigit)) return last
        return null
    }

    fun parseFromText(text: String): String? {
        if (text.isBlank()) return null
        for (pattern in pathPatterns) {
            val m = pattern.find(text)
            if (m != null) return m.groupValues[1]
        }
        val trimmed = text.trim()
        if (trimmed.all(Char::isDigit) && trimmed.length in 1..12) return trimmed
        return null
    }

    /**
     * Detect a likely thread id from clipboard content.
     * Only matches **exactly 8 digits** (not embedded in longer numbers),
     * e.g. `62810245`, `No.62810245`, `看看这个 62810245 串`.
     */
    fun parseEightDigitThreadId(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
            // Normalize common full-width digits from some IM keyboards
            .map { ch ->
                if (ch in '０'..'９') ('0' + (ch - '０')) else ch
            }
            .joinToString("")

        // Fast path: clipboard is exactly an 8-digit id
        if (trimmed.length == 8 && trimmed.all { it in '0'..'9' }) {
            return trimmed
        }

        // "No.12345678" / ">>No.12345678"
        Regex("""(?:>>)?No\.([0-9]{8})(?:[^0-9]|$)""", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        // Any standalone 8-digit token
        return eightDigitPattern.find(trimmed)?.groupValues?.get(1)
    }

    /**
     * Extract an X-Island thread ID ONLY when [rawUrl] points to a verified X-Island host
     * (or a known search-engine redirect wrapper pointing directly to one).
     * Non-X-Island pages (search result pagination, help pages, image CDNs, forums) return null.
     */
    fun parseXIslandThreadIdFromWebUrl(rawUrl: String?, depth: Int = 0): String? {
        if (rawUrl.isNullOrBlank() || depth > 2) return null
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.trim()?.lowercase()?.trimEnd('.') ?: return null
        val path = uri.rawPath.orEmpty()

        // Unwrap search engine click-tracking redirects when they wrap a thread URL.
        if (host == "google.com" || host.contains(".google.")) {
            if (path == "/url" || path == "/imgres") {
                val query = parseQueryParams(uri.rawQuery)
                val target = query["q"] ?: query["url"]
                if (!target.isNullOrBlank()) {
                    return parseXIslandThreadIdFromWebUrl(target, depth + 1)
                }
            } else if (path.startsWith("/amp/s/")) {
                val target = "https://${path.removePrefix("/amp/s/")}"
                return parseXIslandThreadIdFromWebUrl(target, depth + 1)
            } else if (path.startsWith("/amp/")) {
                val target = "http://${path.removePrefix("/amp/")}"
                return parseXIslandThreadIdFromWebUrl(target, depth + 1)
            }
        } else if ((host == "duckduckgo.com" || host.endsWith(".duckduckgo.com")) &&
            (path == "/l/" || path == "/l")
        ) {
            val target = parseQueryParams(uri.rawQuery)["uddg"]
            if (!target.isNullOrBlank()) {
                return parseXIslandThreadIdFromWebUrl(target, depth + 1)
            }
        } else if ((host == "bing.com" || host.endsWith(".bing.com")) && path == "/ck/a") {
            val uParam = parseQueryParams(uri.rawQuery)["u"]
            if (!uParam.isNullOrBlank() && uParam.startsWith("a1")) {
                val decoded = decodeBase64Url(uParam.removePrefix("a1"))
                if (decoded != null && (decoded.startsWith("http://") || decoded.startsWith("https://"))) {
                    return parseXIslandThreadIdFromWebUrl(decoded, depth + 1)
                }
            }
        }

        if (!isXIslandHost(host)) return null
        if (host.startsWith("image.") || host.startsWith("img.")) return null

        val matchDigits = webThreadPathPattern.find(path)?.groupValues?.get(1)
            ?: if (webThreadQueryEndpointPattern.containsMatchIn(path)) {
                parseQueryParams(uri.rawQuery)["id"]?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            } else null

        return matchDigits?.takeIf { it.length in 1..12 && it.any { ch -> ch != '0' } }
    }

    private fun isXIslandHost(host: String): Boolean {
        return xIslandRootDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = HashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val rawKey = if (idx >= 0) pair.substring(0, idx) else pair
            val rawVal = if (idx >= 0) pair.substring(idx + 1) else ""
            val key = decodeUrlComponent(rawKey) ?: continue
            val value = decodeUrlComponent(rawVal) ?: continue
            result.putIfAbsent(key, value)
        }
        return result
    }

    private fun decodeUrlComponent(raw: String): String? = runCatching {
        URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
    }.getOrNull()

    private fun decodeBase64Url(encoded: String): String? = runCatching {
        val padded = when (encoded.length % 4) {
            2 -> "$encoded=="
            3 -> "$encoded="
            else -> encoded
        }
        String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
    }.getOrNull()
}

