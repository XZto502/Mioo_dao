package com.mioo.dao.data.repository

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Global search displays just the thread header and main post. Skip decoding replies. */
@JsonClass(generateAdapter = true)
internal data class CachedThreadSearchPreview(
    val id: Long,
    val title: String? = null,
    val content: String = "",
    @Json(name = "user_hash") val userHash: String = ""
)
