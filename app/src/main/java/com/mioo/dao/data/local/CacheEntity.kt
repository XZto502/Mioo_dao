package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo

@Entity(tableName = "api_cache", primaryKeys = ["cacheKey", "page"])
data class CacheEntity(
    val cacheKey: String,
    val page: Int,
    val jsonResponse: String,
    val cachedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val searchIndexed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val feedStale: Boolean = false
)
