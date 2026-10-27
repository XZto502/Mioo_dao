package com.mioo.dao.ui.components

import androidx.compose.runtime.Immutable
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.effectiveTitle

/**
 * Common data model representing a post (either a thread header or a reply).
 */
@Immutable
data class PostData(
    val id: String,
    val title: String? = null,
    val userName: String? = null,
    val userId: String,
    val createdAt: String,
    val content: String,
    val imageUrl: String? = null,
    val isPo: Boolean = false,
    val isAdmin: Boolean = false,
    val isSage: Boolean = false,
    val resto: String? = null
)

/**
 * Pre-built list row for thread replies — heavy work done off the main thread.
 */
@Immutable
data class ReplyDisplayItem(
    val id: Long,
    val idStr: String,
    val userHash: String,
    val postData: PostData,
    val quoteIds: List<String>,
    val hasImage: Boolean,
    val rawContent: String
)

private val TIME_SECONDS_REGEX = Regex("(\\d{1,2}:\\d{2}):\\d{2}$")

/**
 * 将发串/回复时间精确到分钟，去除末尾秒数（如 2026-10-27(二)22:42:37 → 2026-10-27(二)22:42）。
 */
fun formatPostTime(raw: String): String {
    if (raw.isBlank()) return raw
    return raw.trim().replace(TIME_SECONDS_REGEX, "$1")
}

/**
 * Convert a Thread data model to a display-ready PostData.
 */
fun Thread.toPostData(): PostData {
    return PostData(
        id = this.idStr,
        title = this.title.effectiveTitle(),
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = formatPostTime(this.now),
        content = this.content,
        imageUrl = IslandImage.url(this.imageUrl),
        isPo = false,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.idStr
    )
}

/**
 * Convert a Reply to display-ready PostData.
 */
fun Reply.toPostData(isPo: Boolean): PostData {
    return PostData(
        id = this.idStr,
        title = this.title.effectiveTitle(),
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = formatPostTime(this.now),
        content = this.content,
        imageUrl = IslandImage.url(this.imageUrl),
        isPo = isPo,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.resto?.toString() ?: this.idStr
    )
}

@Immutable
data class StablePostList(val list: List<PostData>)
