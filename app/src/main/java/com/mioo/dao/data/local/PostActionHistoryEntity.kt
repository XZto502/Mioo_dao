package com.mioo.dao.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class HistoryType(val label: String) {
    BROWSE("浏览历史"), REPLY("发言历史"), NEW_THREAD("发串历史")
}

/** Stable UUIDs also make repeated backup imports idempotent. */
@Entity(tableName = "post_action_history", indices = [Index(value = ["type", "timestamp"])])
data class PostActionHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,
    val threadId: String? = null,
    val forumId: String? = null,
    val forumName: String = "",
    val cookieHash: String = "",
    val threadTitle: String? = null,
    val threadSummary: String = "",
    val title: String? = null,
    val authorName: String? = null,
    val content: String,
    val imageUrl: String? = null,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Board's newest thread before submission; never match an older duplicate. */
    val minimumThreadId: Long? = null
)

data class BrowseHistoryRecord(
    @Embedded val history: HistoryEntity,
    val lastReadPage: Int = 1
)
