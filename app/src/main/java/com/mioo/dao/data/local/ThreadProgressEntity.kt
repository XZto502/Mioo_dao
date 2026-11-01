package com.mioo.dao.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thread_progress")
data class ThreadProgressEntity(
    @PrimaryKey val threadId: String,
    val page: Int,
    val firstVisibleIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val scrollOffset: Int = 0,
    val anchorPostId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
