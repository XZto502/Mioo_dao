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

/**
 * Convert a Thread data model to a display-ready PostData.
 */
fun Thread.toPostData(cdnUrl: String = "https://image.nmb.best"): PostData {
    return PostData(
        id = this.idStr,
        title = this.title.effectiveTitle(),
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = this.now,
        content = this.content,
        imageUrl = if (this.imageUrl != null) "$cdnUrl/image/${this.imageUrl}" else null,
        isPo = false,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.idStr
    )
}

/**
 * Convert a Reply to display-ready PostData.
 */
fun Reply.toPostData(isPo: Boolean, cdnUrl: String = "https://image.nmb.best"): PostData {
    return PostData(
        id = this.idStr,
        title = this.title.effectiveTitle(),
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = this.now,
        content = this.content,
        imageUrl = if (this.imageUrl != null) "$cdnUrl/image/${this.imageUrl}" else null,
        isPo = isPo,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.resto?.toString() ?: this.idStr
    )
}

@Immutable
data class StablePostList(val list: List<PostData>)
