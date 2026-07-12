package com.mioo.dao.utils

import android.content.Intent
import android.net.Uri

/**
 * Extract X-island thread ids from share intents / deep links.
 * Supports common patterns:
 * - https://www.nmbxd.com/t/123
 * - https://adnmb.com/t/123
 * - https://nmbxd.com/Forum/thread/id/123
 * - plain "123" / "No.123" / ">>No.123"
 */
object ThreadLinkParser {

    private val pathPatterns = listOf(
        Regex("""/(?:t|thread)(?:/id)?/(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""[?&]id=(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""No\.(\d+)""", RegexOption.IGNORE_CASE),
        Regex(""">>(\d+)""")
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
}
