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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CloudFeedThreadEntity>)

    @Query("SELECT threadId, title, position FROM cloud_feed_threads WHERE feedUuid = :uuid AND threadId IN (:ids)")
    suspend fun readPageEntries(uuid: String, ids: List<String>): List<CloudFeedIndexEntry>

    @Query("""
        UPDATE cloud_feed_threads SET title = :title, position = :position
        WHERE feedUuid = :uuid AND threadId = :id
            AND (title IS NOT :title OR position != :position)
    """)
    suspend fun updateIndexEntry(uuid: String, id: String, title: String?, position: Int)

    @Transaction
    suspend fun indexPage(uuid: String, items: List<CloudFeedThreadEntity>): Int {
        var inserted = 0
        for (batch in items.chunked(500)) {
            val existing = readPageEntries(uuid, batch.map { it.threadId }).associateBy { it.threadId }
            val missing = batch.filterNot { existing.containsKey(it.threadId) }
            if (missing.isNotEmpty()) {
                insertAll(missing)
                inserted += missing.size
            }
            for (item in batch) {
                val cached = existing[item.threadId] ?: continue
                if (cached.title != item.title || cached.position != item.position) {
                    updateIndexEntry(uuid, item.threadId, item.title, item.position)
                }
            }
        }
        return inserted
    }

    @Query("SELECT * FROM cloud_feed_threads WHERE feedUuid IN (:uuids) ORDER BY position, savedAt DESC, threadId")
    fun observeIndex(uuids: List<String>): Flow<List<CloudFeedThreadEntity>>

    @Query("SELECT threadId FROM cloud_feed_threads WHERE feedUuid = :uuid")
    suspend fun indexedIds(uuid: String): List<String>

    @Query("SELECT * FROM cloud_feed_sync WHERE feedUuid = :uuid")
    suspend fun syncState(uuid: String): CloudFeedSyncEntity?

    @Query("SELECT feedUuid FROM cloud_feed_sync WHERE lastFullScanAt > 0")
    fun observeFullyIndexedFolders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncState(state: CloudFeedSyncEntity)

    @Query("DELETE FROM cloud_feed_threads WHERE feedUuid = :uuid AND threadId IN (:ids) AND savedAt < :before")
    suspend fun removeMissing(uuid: String, ids: List<String>, before: Long)

    @Transaction
    suspend fun finishFullScan(state: CloudFeedSyncEntity, seen: Set<String>, startedAt: Long) {
        indexedIds(state.feedUuid).filterNot(seen::contains).chunked(500).forEach {
            removeMissing(state.feedUuid, it, startedAt)
        }
        saveSyncState(state)
    }

    @Query("DELETE FROM cloud_feed_threads WHERE feedUuid = :feedUuid AND threadId = :tid")
    suspend fun removeFeedThread(feedUuid: String, tid: String)

    @Query("SELECT feedUuid FROM cloud_feed_threads WHERE threadId = :tid")
    suspend fun feedUuidsFor(tid: String): List<String>

    @Query("SELECT * FROM cloud_feed_threads WHERE feedUuid = :feedUuid")
    suspend fun getThreadsForFeed(feedUuid: String): List<CloudFeedThreadEntity>

    /** 记录一次补抓到的回复总数；同一串可能在多个夹里，按 threadId 全量更新。 */
    @Query("""
        UPDATE cloud_feed_threads SET title = :title, replyCount = :count
        WHERE threadId = :tid AND (title IS NOT :title OR replyCount != :count)
    """)
    suspend fun updateThreadMetadata(tid: String, title: String?, count: Int)

    /** 响应式版 feedUuidsFor：收藏菜单按夹显示"已收藏进该夹"态。 */
    @Query("SELECT feedUuid FROM cloud_feed_threads WHERE threadId = :tid")
    fun observeFeedUuidsFor(tid: String): Flow<List<String>>
}

data class CloudFeedIndexEntry(val threadId: String, val title: String?, val position: Int)
