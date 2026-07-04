package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val fid: String?,
    val content: String,
    val userid: String,
    val now: String,
    val title: String?,
    val name: String?,
    val timestamp: Long = System.currentTimeMillis()
)
