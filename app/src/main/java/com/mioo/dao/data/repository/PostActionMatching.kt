package com.mioo.dao.data.repository

import com.mioo.dao.data.local.PostActionHistoryEntity
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.effectiveTitle
import com.squareup.moshi.Moshi
import java.time.LocalDateTime
import java.time.ZoneId

private val jsonValueAdapter = Moshi.Builder().build().adapter(Any::class.java)

/** Public cookie identity only. Never put authentication tokens into history/backup. */
internal fun historyCookieIdentity(raw: String?): String {
    if (raw.isNullOrBlank()) return "未记录饼干"
    val json = runCatching { jsonValueAdapter.fromJson(raw) as? Map<*, *> }.getOrNull()
    val display = listOf("name", "user_hash", "hash").firstNotNullOfOrNull { key ->
        (json?.get(key) as? String)?.takeIf { it.isNotBlank() && it.length <= 16 }
    }
    if (display != null) return display
    if (!raw.startsWith("{") && raw.length <= 16) return raw
    val token = (json?.get("cookie") ?: json?.get("userhash"))?.toString() ?: raw
    val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        .take(4).joinToString("") { "%02x".format(it) }
    return "饼干-$fingerprint"
}

/** IDs come from an explicit result/redirect, never unrelated links in the response page. */
internal fun createdThreadId(raw: String): String? {
    fun positive(value: Any?): String? {
        val id = when (value) { is Number -> value.toLong(); is String -> value.toLongOrNull(); else -> null }
        return id?.takeIf { it > 0 }?.toString()
    }
    fun fromUrl(value: String): String? = Regex("(?:/t/|/thread/|[?&](?:tid|threadId|resto)=)([0-9]+)", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.get(1)?.let(::positive)
    val parsed = runCatching { jsonValueAdapter.fromJson(raw.trim()) }.getOrNull()
    val obj = parsed as? Map<*, *>
    val data = obj?.get("data") as? Map<*, *>
    for (source in listOfNotNull(data, obj)) {
        for (key in listOf("threadId", "thread_id", "tid", "id")) positive(source[key])?.let { return it }
        for (key in listOf("url", "redirect", "location")) (source[key] as? String)?.let { fromUrl(it)?.let { id -> return id } }
    }
    if (parsed is String) fromUrl(parsed)?.let { return it }
    if (raw.trim().startsWith("http")) fromUrl(raw.trim())?.let { return it }
    val redirect = Regex("""(?:window\.)?location(?:\.href)?\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        .find(raw)?.groupValues?.get(1)
    return redirect?.let(::fromUrl)
}

private fun normalizedText(content: String): String = content.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

internal fun historyPlainText(content: String): String = normalizedText(content
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("&(#x[0-9a-fA-F]+|#[0-9]+|nbsp|quot|apos|lt|gt|amp);")) { match ->
        val entity = match.groupValues[1]
        when (entity) {
            "nbsp" -> " "; "quot" -> "\""; "apos" -> "'"; "lt" -> "<"; "gt" -> ">"; "amp" -> "&"
            else -> runCatching {
                val code = if (entity.startsWith("#x")) entity.drop(2).toInt(16) else entity.drop(1).toInt()
                String(Character.toChars(code))
            }.getOrDefault(match.value)
        }
    })

internal fun matchCreatedThread(record: PostActionHistoryEntity, candidates: List<Thread>): Thread? {
    val publicHash = record.cookieHash.takeUnless { it.startsWith("饼干-") || it == "未记录饼干" }
    val matches = candidates.filter { thread ->
        val isNew = record.minimumThreadId?.let { thread.id > it } ?: run {
            // A failed preflight must not attach an old identical post to a new action.
            val date = Regex("(\\d{4}-\\d{2}-\\d{2}).*?(\\d{2}:\\d{2}:\\d{2})").find(thread.now)
            val createdAt = date?.let { runCatching {
                LocalDateTime.parse("${it.groupValues[1]}T${it.groupValues[2]}")
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            }.getOrNull() }
            publicHash != null && createdAt != null && createdAt >= record.timestamp - 120_000
        }
        isNew && (publicHash != null || record.content.isNotBlank() || !record.title.isNullOrBlank()) &&
            thread.fidStr == record.forumId &&
            (publicHash == null || thread.userHash == publicHash) &&
            historyPlainText(thread.content) == normalizedText(record.content) &&
            historyPlainText(thread.title.effectiveTitle().orEmpty()) == normalizedText(record.title.effectiveTitle().orEmpty()) &&
            (record.authorName.isNullOrBlank() || historyPlainText(thread.name.orEmpty()) == normalizedText(record.authorName))
    }
    return matches.singleOrNull()
}
