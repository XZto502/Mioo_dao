package com.mioo.dao.ui.components

import androidx.compose.runtime.Immutable
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.effectiveTitle

/**
 * Pre-built board/timeline list row. Built off the main thread.
 */
@Immutable
data class ThreadListItem(
    val id: Long,
    val idStr: String,
    val userHash: String,
    val replyCount: Int,
    val postData: PostData,
    val rawContent: String,
    val hasImage: Boolean
)

/**
 * Pre-built local bookmark row.
 */
@Immutable
data class BookmarkListItem(
    val id: String,
    val postData: PostData,
    val rawContent: String,
    val newReplyCount: Int = 0
)

fun List<Thread>.toFilteredThreadListItems(
    blockedThreads: Set<String>,
    blockedUsers: Set<String>,
    blockedKeywords: List<String>,
    cdnUrl: String = "https://image.nmb.best"
): List<ThreadListItem> {
    if (isEmpty()) return emptyList()
    val result = ArrayList<ThreadListItem>(size)
    for (thread in this) {
        if (thread.idStr in blockedThreads) continue
        if (thread.userHash in blockedUsers) continue
        if (blockedKeywords.isNotEmpty() &&
            blockedKeywords.any {
                thread.title?.contains(it, ignoreCase = true) == true ||
                    thread.content.contains(it, ignoreCase = true)
            }
        ) {
            continue
        }
        result.add(
            ThreadListItem(
                id = thread.id,
                idStr = thread.idStr,
                userHash = thread.userHash,
                replyCount = thread.replyCount ?: 0,
                postData = thread.toPostData(cdnUrl),
                rawContent = thread.content,
                hasImage = !thread.img.isNullOrBlank()
            )
        )
    }
    return result
}

fun List<BookmarkEntity>.toBookmarkListItems(): List<BookmarkListItem> {
    if (isEmpty()) return emptyList()
    return map { entity ->
        BookmarkListItem(
            id = entity.id,
            postData = PostData(
                id = entity.id,
                title = entity.title.effectiveTitle(),
                userName = entity.name ?: "Anonymous",
                userId = entity.userid,
                createdAt = entity.now,
                content = entity.content,
                imageUrl = null,
                isPo = false,
                isAdmin = false,
                isSage = false,
                resto = entity.id
            ),
            rawContent = entity.content,
            newReplyCount = entity.newReplyCount
        )
    }
}
