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

    /** Page snapshot of one feed: replace what we previously cached for it. */
    @Transaction
    suspend fun replaceAll(feedUuid: String, items: List<CloudFeedThreadEntity>) {
        clearFeed(feedUuid)
        insertAll(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CloudFeedThreadEntity)

    @Query("DELETE FROM cloud_feed_threads WHERE feedUuid = :feedUuid AND threadId = :tid")
    suspend fun removeFeedThread(feedUuid: String, tid: String)

    @Query("SELECT feedUuid FROM cloud_feed_threads WHERE threadId = :tid")
    suspend fun feedUuidsFor(tid: String): List<String>
}
