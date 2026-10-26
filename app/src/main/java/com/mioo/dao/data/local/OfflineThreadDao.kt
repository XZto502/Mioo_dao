package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineThreadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineThreadEntity)

    @Query("SELECT * FROM offline_threads WHERE threadId = :threadId LIMIT 1")
    suspend fun get(threadId: String): OfflineThreadEntity?

    @Query("SELECT * FROM offline_threads")
    suspend fun getAllOnce(): List<OfflineThreadEntity>

    /**
     * 管理页列表：按下载时间倒序。cachedPages / jsonBytes 从 api_cache 实时聚合，
     * 两张表任一变动都会触发 Flow 重算。
     */
    @Query(
        """
        SELECT
            o.threadId AS threadId,
            o.title AS title,
            o.contentSummary AS contentSummary,
            o.poUserHash AS poUserHash,
            o.fid AS fid,
            o.forumName AS forumName,
            o.replyCount AS replyCount,
            o.downloadedAt AS downloadedAt,
            o.imageBytes AS imageBytes,
            o.coverFile AS coverFile,
            (SELECT COUNT(*) FROM api_cache a
             WHERE a.cacheKey = 'thread_' || o.threadId) AS cachedPages,
            (SELECT COALESCE(SUM(LENGTH(a.jsonResponse)), 0) FROM api_cache a
             WHERE a.cacheKey = 'thread_' || o.threadId) AS jsonBytes
        FROM offline_threads o
        ORDER BY o.downloadedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<OfflineThreadSummary>>

    @Query("SELECT EXISTS(SELECT 1 FROM offline_threads WHERE threadId = :threadId)")
    fun observeDownloaded(threadId: String): Flow<Boolean>

    @Query("DELETE FROM offline_threads WHERE threadId = :threadId")
    suspend fun delete(threadId: String)

    @Query("DELETE FROM offline_threads")
    suspend fun clear()
}
