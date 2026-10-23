package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE type = :type AND targetId = :targetId LIMIT 1")
    suspend fun find(type: String, targetId: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM drafts WHERE type = :type AND targetId = :targetId")
    suspend fun deleteFor(type: String, targetId: String)
}
