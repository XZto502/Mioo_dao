package com.mioo.dao.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Reply(
    @Json(name = "id") val id: Long,
    @Json(name = "fid") val fid: Long? = null,
    @Json(name = "now") val now: String = "",
    @Json(name = "user_hash") val userHash: String = "",
    @Json(name = "name") val name: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "content") val content: String = "",
    @Json(name = "img") val img: String? = null,
    @Json(name = "ext") val ext: String? = null,
    @Json(name = "sage") val sage: Int? = 0,
    @Json(name = "admin") val admin: Int? = 0,
    @Json(name = "Hide") val hide: Int? = 0,
    @Json(name = "ReplyCount") val replyCount: Int? = 0,
    @Json(name = "resto") val resto: Long? = null
) {
    /** Stringified id for navigation and display */
    val idStr: String get() = id.toString()

    val imageUrl: String?
        get() = if (!img.isNullOrBlank() && !ext.isNullOrBlank()) {
            "$img$ext"
        } else {
            null
        }

    val isAdmin: Boolean
        get() = admin == 1

    val isSage: Boolean
        get() = sage == 1
}
