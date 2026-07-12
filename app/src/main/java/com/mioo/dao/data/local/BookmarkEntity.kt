package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val fid: String?,
    val content: String,
    val userid: String,
    val now: String,
    val title: String?,
    val name: String?,
    val timestamp: Long = System.currentTimeMillis(),
    /** Reply count when user last opened this thread. */
    val lastReadReplyCount: Int = 0,
    /** Latest known reply count (refreshed in background). */
    val lastKnownReplyCount: Int = 0
) {
    val newReplyCount: Int
        get() = (lastKnownReplyCount - lastReadReplyCount).coerceAtLeast(0)
}
