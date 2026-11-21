package com.mioo.dao.data.repository

import android.content.Context
import com.mioo.dao.data.api.extractQuotedJsonString
import com.mioo.dao.data.model.XdResponse
import java.io.File
import java.io.IOException
import java.security.MessageDigest

// Each folder has its own crash marker. It contains no cookie or subscription identifier.
internal fun cloudFeedRepairMarker(context: Context, uuid: String): File {
    val digest = MessageDigest.getInstance("SHA-256").digest(uuid.toByteArray(Charsets.UTF_8))
    val key = android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or
        android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    return File(context.noBackupFilesDir, "cloud-feed-repair-$key")
}

data class CloudRemovalResult(val removed: Int, val absent: Int, val failed: Set<String>) {
    fun message(): String = when {
        failed.isEmpty() && removed > 0 -> "${removed} 个云端收藏夹已确认移除"
        failed.isEmpty() -> "云端收藏夹中没有找到该串"
        removed > 0 -> "已从 ${removed} 个收藏夹移除，另有 ${failed.size} 个失败，可重试"
        else -> "${failed.size} 个收藏夹移除失败，可重试"
    }
}

private val absentSubscription = Regex(
    """(?:您|你)?(?:尚未|并未|未|没有)(?:订阅|收藏)(?:过)?(?:该串|此串|这个串|该主题|本串)?|(?:该|此)?订阅不存在"""
)

internal fun isAbsentSubscription(message: String): Boolean =
    absentSubscription.matches(message.trim().trimEnd('。', '！', '!'))

internal fun parseCloudFeedWrite(raw: String, removing: Boolean): XdResponse<String> {
    val body = raw.trim().removePrefix("\uFEFF").trim()
    val text = (extractQuotedJsonString(body) ?: body.removeSurrounding("\"")).trim()
    val normalized = text.trimEnd('!', '！', '。')
    val successful = if (removing) normalized == "取消订阅成功" else
        normalized in setOf("订阅成功", "订阅大成功→_→", "订阅大成功")
    return when {
        successful -> XdResponse.Success(text)
        removing && isAbsentSubscription(text) -> XdResponse.Error(text)
        normalized in setOf("该串不存在", "该主题不存在", "请求失败", "订阅失败", "取消订阅失败") ->
            XdResponse.Error(text)
        else -> XdResponse.Error("收藏操作结果待确认", throwable = IOException("Unexpected cloud feed write response"))
    }
}
