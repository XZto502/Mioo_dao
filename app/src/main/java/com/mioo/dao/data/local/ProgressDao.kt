package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ThreadProgressEntity)

    @Query("SELECT * FROM thread_progress WHERE threadId = :threadId LIMIT 1")
    suspend fun get(threadId: String): ThreadProgressEntity?

    @Query("DELETE FROM thread_progress WHERE threadId = :threadId")
    suspend fun delete(threadId: String)

    @Query("DELETE FROM thread_progress")
    suspend fun clearAll()
}
