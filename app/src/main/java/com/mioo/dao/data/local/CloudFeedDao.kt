package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudFeedDao {

    @Query("SELECT EXISTS(SELECT 1 FROM cloud_feed_threads WHERE threadId = :tid)")
    fun isMember(tid: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CloudFeedThreadEntity>)

    @Query("DELETE FROM cloud_feed_threads WHERE feedUuid = :feedUuid")
    suspend fun clearFeed(feedUuid: String)

    /** Page snapshot of one feed: replace what we previously cached for it, keeping known reply counts. */
    @Transaction
    suspend fun replaceAll(feedUuid: String, items: List<CloudFeedThreadEntity>) {
        val counts = getThreadsForFeed(feedUuid).associate { it.threadId to it.replyCount }
        clearFeed(feedUuid)
        insertAll(items.map { item ->
            counts[item.threadId]?.takeIf { it > 0 }?.let { item.copy(replyCount = it) } ?: item
        })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CloudFeedThreadEntity)

    @Query("DELETE FROM cloud_feed_threads WHERE feedUuid = :feedUuid AND threadId = :tid")
    suspend fun removeFeedThread(feedUuid: String, tid: String)

    @Query("SELECT feedUuid FROM cloud_feed_threads WHERE threadId = :tid")
    suspend fun feedUuidsFor(tid: String): List<String>

    @Query("SELECT * FROM cloud_feed_threads WHERE feedUuid = :feedUuid")
    suspend fun getThreadsForFeed(feedUuid: String): List<CloudFeedThreadEntity>

    /** 记录一次补抓到的回复总数；同一串可能在多个夹里，按 threadId 全量更新。 */
    @Query("UPDATE cloud_feed_threads SET replyCount = :count WHERE threadId = :tid")
    suspend fun updateReplyCount(tid: String, count: Int)

    /** 响应式版 feedUuidsFor：收藏菜单按夹显示"已收藏进该夹"态。 */
    @Query("SELECT feedUuid FROM cloud_feed_threads WHERE threadId = :tid")
    fun observeFeedUuidsFor(tid: String): Flow<List<String>>
}
