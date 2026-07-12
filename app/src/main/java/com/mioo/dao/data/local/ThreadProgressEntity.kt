package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thread_progress")
data class ThreadProgressEntity(
    @PrimaryKey val threadId: String,
    val page: Int,
    val firstVisibleIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
