package com.mioo.dao.utils

import android.content.Intent
import android.net.Uri

/**
 * Extract X-island thread ids from share intents / deep links / clipboard.
 * Supports common patterns:
 * - https://www.nmbxd.com/t/123
 * - https://adnmb.com/t/123
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
}

