package com.mioo.dao.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Feed(
    @Json(name = "id") val id: String,
    @Json(name = "tid") val tid: String,
    @Json(name = "fid") val fid: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "thread") val thread: Thread? = null
)
