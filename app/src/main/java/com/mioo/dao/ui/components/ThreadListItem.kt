package com.mioo.dao.ui.components

import androidx.compose.runtime.Immutable
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.utils.KeywordMatcher

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
    val matcher = KeywordMatcher.build(blockedKeywords)
    val result = ArrayList<ThreadListItem>(size)
    for (thread in this) {
        if (thread.idStr in blockedThreads) continue
        if (thread.userHash in blockedUsers) continue
        if (!matcher.isEmpty) {
            val haystack = buildString(thread.content.length + (thread.title?.length ?: 0) + 1) {
                thread.title?.let { append(it); append('\n') }
                append(thread.content)
            }.lowercase()
            if (matcher.containsMatch(haystack, textIsLowercase = true)) continue
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

/**
 * Prefer when the caller already holds a [KeywordMatcher] (e.g. ViewModel rebuild path)
 * so the automaton is not rebuilt per filter pass.
 */
fun List<Thread>.toFilteredThreadListItems(
    blockedThreads: Set<String>,
    blockedUsers: Set<String>,
    keywordMatcher: KeywordMatcher,
    cdnUrl: String = "https://image.nmb.best"
): List<ThreadListItem> {
    if (isEmpty()) return emptyList()
    val result = ArrayList<ThreadListItem>(size)
    for (thread in this) {
        if (thread.idStr in blockedThreads) continue
        if (thread.userHash in blockedUsers) continue
        if (!keywordMatcher.isEmpty) {
            val haystack = buildString(thread.content.length + (thread.title?.length ?: 0) + 1) {
                thread.title?.let { append(it); append('\n') }
                append(thread.content)
            }.lowercase()
            if (keywordMatcher.containsMatch(haystack, textIsLowercase = true)) continue
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
