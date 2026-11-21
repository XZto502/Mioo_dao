package com.mioo.dao.data.repository

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Global search displays just the thread header and main post. Skip decoding replies. */
@JsonClass(generateAdapter = true)
internal data class CachedThreadSearchPreview(
    val id: Long,
    val title: String? = null,
    val content: String = "",
    @Json(name = "user_hash") val userHash: String = "",
    val now: String = "",
    val img: String? = null,
    val ext: String? = null,
    @Json(name = "ReplyCount") val replyCount: Int? = 0
) {
    fun toPreview(cache: com.mioo.dao.data.local.CacheEntity) =
        com.mioo.dao.data.local.CachePostPreview(
            cache.cacheKey, cache.page, id.toString(), title, content, userHash, now,
            if (!img.isNullOrBlank() && !ext.isNullOrBlank()) "$img$ext" else null,
            replyCount ?: 0
        )
}
