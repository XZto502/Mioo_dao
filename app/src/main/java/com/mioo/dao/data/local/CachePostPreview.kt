package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.mioo.dao.data.model.Thread

@Entity(
    tableName = "cache_post_previews",
    primaryKeys = ["cacheKey", "page", "threadId"],
    foreignKeys = [ForeignKey(
        entity = CacheEntity::class,
        parentColumns = ["cacheKey", "page"],
        childColumns = ["cacheKey", "page"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("threadId")]
)
data class CachePostPreview(
    val cacheKey: String,
    val page: Int,
    val threadId: String,
    val title: String?,
    val content: String,
    val userHash: String,
    val now: String,
    val imageUrl: String?,
    val replyCount: Int
) {
    constructor(cache: CacheEntity, thread: Thread) : this(
        cache.cacheKey, cache.page, thread.idStr, thread.title, thread.content,
        thread.userHash, thread.now, thread.imageUrl, thread.replyCount ?: 0
    )
}
