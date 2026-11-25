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

    // 用离线表变更触发统计。更新已有离线串时条数可以不变，每次发射都要处理。
    @Query("SELECT COUNT(*) FROM offline_threads")
    fun observeSummaryChanges(): Flow<Int>

    /** 按下载时间倒序，单次遍历缓存页计算页数及大小。 */
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
            COUNT(a.page) AS cachedPages,
            COALESCE(SUM(LENGTH(a.jsonResponse)), 0) AS jsonBytes
        FROM offline_threads o
        LEFT JOIN api_cache a ON a.cacheKey = 'thread_' || o.threadId
        GROUP BY o.threadId
        ORDER BY o.downloadedAt DESC
        """
    )
    suspend fun getSummaries(): List<OfflineThreadSummary>

    @Query("SELECT EXISTS(SELECT 1 FROM offline_threads WHERE threadId = :threadId)")
    fun observeDownloaded(threadId: String): Flow<Boolean>

    @Query("DELETE FROM offline_threads WHERE threadId = :threadId")
    suspend fun delete(threadId: String)

    @Query("DELETE FROM offline_threads")
    suspend fun clear()
}
