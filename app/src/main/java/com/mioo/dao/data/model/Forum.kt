package com.mioo.dao.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ForumGroup(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "sort") val sort: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "forums") val forums: List<Forum> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Forum(
    @Json(name = "id") val id: String,
    @Json(name = "fgroup") val fgroup: String? = null,
    @Json(name = "sort") val sort: String? = null,
    @Json(name = "name") val name: String,
    @Json(name = "showName") val showName: String? = null,
    @Json(name = "msg") val msg: String? = null,
    @Json(name = "interval") val interval: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updateAt") val updateAt: String? = null,
    @Json(name = "status") val status: String? = null
)
