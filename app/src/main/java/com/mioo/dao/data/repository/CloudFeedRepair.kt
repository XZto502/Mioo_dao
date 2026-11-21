package com.mioo.dao.data.repository

import android.content.Context
import java.io.File
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
        failed.isEmpty() && removed > 0 -> "已从 ${removed} 个云端收藏夹移除"
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
