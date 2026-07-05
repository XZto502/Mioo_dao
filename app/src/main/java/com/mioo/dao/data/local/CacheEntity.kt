package com.mioo.dao.data.local

import androidx.room.Entity

@Entity(tableName = "api_cache", primaryKeys = ["cacheKey", "page"])
data class CacheEntity(
    val cacheKey: String,
    val page: Int,
    val jsonResponse: String,
    val cachedAt: Long = System.currentTimeMillis()
)
