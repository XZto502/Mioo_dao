package com.mioo.dao.data.repository

import com.mioo.dao.data.api.extractQuotedJsonString
import com.mioo.dao.data.model.XdResponse

/**
 * X岛发串 / 回复在 HTTP 200 里返回结果。
 * 成功经常是跳转后的页面，正文里没有「成功」；失败则是 alert、ThinkPHP error 或一段纯文本。
 * 只有明确的失败信号才判失败，避免把已经发出的串当成失败后再发一次。
 */
internal fun interpretForumWriteResponse(raw: String): XdResponse<String> {
    val trimmed = raw.trim().removePrefix("\uFEFF")
    if (trimmed.isEmpty()) return XdResponse.Error(message = "岛方返回空响应")

    extractQuotedJsonString(trimmed)?.let { msg ->
        return if (msg.contains("成功")) {
            XdResponse.Success(msg.take(80).ifBlank { "发送成功" })
        } else {
            XdResponse.Error(message = msg.take(160).ifBlank { "发送失败" })
        }
    }

    if (trimmed.startsWith("{")) {
        try {
            val obj = org.json.JSONObject(trimmed)
            val message = sequenceOf("error", "msg", "message", "info")
                .map { obj.optString(it) }
                .firstOrNull { it.isNotBlank() }
            val status = obj.opt("status")
            val failed = (obj.has("success") && !obj.optBoolean("success", true)) ||
                status == 0 || status == "0" ||
                (status is String && status.equals("fail", ignoreCase = true))
            val succeeded = obj.optBoolean("success", false) ||
                status == 1 || status == "1" ||
                (status is String && status.equals("success", ignoreCase = true))
            if (failed) {
                return XdResponse.Error(message = message?.take(160) ?: "发送被拒绝")
            }
            if (succeeded) {
                return XdResponse.Success(message?.take(80) ?: "发送成功")
            }
        } catch (_: Exception) {
            // 不是 JSON，继续按页面文本判断
        }
    }

    val alert = ALERT.find(trimmed)?.groupValues?.getOrNull(2)
        ?.replace("\\n", " ")
        ?.replace("\\'", "'")
        ?.trim()
    if (!alert.isNullOrEmpty()) {
        return if (alert.contains("成功")) {
            XdResponse.Success(alert.take(80))
        } else {
            XdResponse.Error(message = alert.take(160))
        }
    }

    val plain = SCRIPT_OR_STYLE.replace(trimmed, " ")
        .let { TAG.replace(it, " ") }
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(WHITESPACE, " ")
        .trim()
        .trim('"')

    if (plain.contains("成功")) {
        return XdResponse.Success(plain.take(80))
    }

    if (ERROR_CLASS.containsMatchIn(trimmed)) {
        return XdResponse.Error(message = plain.take(160).ifBlank { "发送失败" })
    }

    val looksLikeDocument = trimmed.contains("<html", ignoreCase = true) ||
        trimmed.contains("<body", ignoreCase = true) ||
        (trimmed.length > 500 && trimmed.contains("<"))
    if (looksLikeDocument) {
        return XdResponse.Success("已提交")
    }

    if (trimmed.isNotEmpty() && trimmed.all(Char::isDigit)) {
        return XdResponse.Success(trimmed)
    }

    // 没有 success/status 字段的 JSON 不当成失败，避免把未知成功回包再发一次
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        return XdResponse.Success("已提交")
    }

    if (FAILURE_HINTS.any { plain.contains(it) } || plain.length <= 120) {
        return XdResponse.Error(message = plain.take(160).ifBlank { "发送失败" })
    }
    return XdResponse.Success("已提交")
}

private val ALERT = Regex("""alert\(\s*(['"])([\s\S]*?)\1\s*\)""", RegexOption.IGNORE_CASE)
private val SCRIPT_OR_STYLE = Regex(
    """<(script|style)[^>]*>[\s\S]*?</\1>""",
    RegexOption.IGNORE_CASE
)
private val TAG = Regex("<[^>]+>")
private val WHITESPACE = Regex("\\s+")
private val ERROR_CLASS = Regex("""class\s*=\s*['"][^'"]*\berror\b""", RegexOption.IGNORE_CASE)

private val FAILURE_HINTS = listOf(
    "失败",
    "错误",
    "没有饼干",
    "饼干无效",
    "饼干过期",
    "冷却",
    "太快",
    "稍后再",
    "禁止",
    "拒绝",
    "验证码",
    "水桶",
    "未登录",
    "需要登录",
    "频繁",
    "内容为空"
)
