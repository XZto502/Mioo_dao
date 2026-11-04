package com.mioo.dao.data.repository

import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.local.PostActionHistoryEntity
import com.squareup.moshi.Moshi
import java.io.File
import java.util.Base64
import java.util.UUID

/** Platform-independent backup format; local absolute paths never leave the device. */
internal object PostActionBackupCodec {
    private val json = Moshi.Builder().build().adapter(Any::class.java)

    fun encode(record: PostActionHistoryEntity, readImage: (String) -> ByteArray? = { path ->
        File(path).takeIf { it.isFile }?.readBytes()
    }): Map<String, Any?> = linkedMapOf<String, Any?>(
        "id" to record.id, "type" to record.type, "threadId" to record.threadId,
        "forumId" to record.forumId, "forumName" to record.forumName, "cookieHash" to record.cookieHash,
        "threadTitle" to record.threadTitle, "threadSummary" to record.threadSummary,
        "title" to record.title, "authorName" to record.authorName, "content" to record.content,
        "imageUrl" to record.imageUrl, "timestamp" to record.timestamp, "minimumThreadId" to record.minimumThreadId
    ).apply {
        record.imagePath?.let { path -> readImage(path)?.let { bytes ->
            put("imageExtension", File(path).extension)
            put("imageBase64", Base64.getEncoder().encodeToString(bytes))
        } }
    }

    fun decode(raw: String, expectedType: HistoryType, restoreImage: (String, String, ByteArray) -> String): PostActionHistoryEntity? {
        val data = json.fromJson(raw) as? Map<*, *> ?: return null
        fun text(key: String) = data[key] as? String
        fun long(key: String) = (data[key] as? Number)?.toLong() ?: text(key)?.toLongOrNull()
        val type = text("type") ?: expectedType.name
        if (type != expectedType.name || expectedType == HistoryType.BROWSE) return null
        val id = text("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val content = text("content") ?: return null
        val threadId = text("threadId")?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }
        if (expectedType == HistoryType.REPLY && threadId == null) return null
        val image = text("imageBase64")?.let { restoreImage(id, text("imageExtension") ?: "jpg", Base64.getDecoder().decode(it)) }
        return PostActionHistoryEntity(
            id = id, type = type, threadId = threadId,
            forumId = text("forumId"), forumName = text("forumName").orEmpty(), cookieHash = text("cookieHash").orEmpty(),
            threadTitle = text("threadTitle"), threadSummary = text("threadSummary").orEmpty(), title = text("title"),
            authorName = text("authorName"), content = content, imageUrl = text("imageUrl"), imagePath = image,
            timestamp = long("timestamp") ?: System.currentTimeMillis(), minimumThreadId = long("minimumThreadId")
        )
    }
}
