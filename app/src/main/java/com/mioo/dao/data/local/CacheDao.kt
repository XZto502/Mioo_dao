package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mioo.dao.data.model.Thread
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    @Query("SELECT rowid AS rowId, LENGTH(CAST(jsonResponse AS BLOB)) AS bytes FROM api_cache WHERE cacheKey NOT IN (SELECT 'thread_' || threadId FROM offline_threads) ORDER BY cachedAt DESC, cacheKey, page")
    suspend fun nonOfflineSizes(): List<CacheRowSize>

    @Query("DELETE FROM api_cache WHERE rowid IN (:ids) AND cacheKey NOT IN (SELECT 'thread_' || threadId FROM offline_threads)")
    suspend fun deleteNonOfflineRows(ids: List<Long>)

    @androidx.room.Transaction
    suspend fun trimNonOfflineBytes(maxBytes: Long) {
        var used = 0L
        val removed = mutableListOf<Long>()
        nonOfflineSizes().forEach { row ->
            if (row.bytes > maxBytes - used) removed.add(row.rowId) else used += row.bytes
        }
        removed.chunked(500).forEach { deleteNonOfflineRows(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: CacheEntity)

    @Query("SELECT * FROM api_cache WHERE cacheKey = :cacheKey AND page = :page LIMIT 1")
    suspend fun getCache(cacheKey: String, page: Int): CacheEntity?

    @Query("UPDATE api_cache SET feedStale = 1 WHERE cacheKey = :key AND feedStale = 0")
    suspend fun markFeedPagesStale(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostPreviews(previews: List<CachePostPreview>)

    @Transaction
    suspend fun insertIndexedCache(cache: CacheEntity, threads: List<Thread>) {
        insertCache(cache.copy(searchIndexed = true))
        insertPostPreviews(threads.map { CachePostPreview(cache, it) })
    }

    @Query("""
        SELECT rowid AS rowId, * FROM api_cache
        WHERE rowid > :afterRowId AND searchIndexed = 0 AND (
            cacheKey GLOB 'feed_*' OR (cacheKey GLOB 'thread_*' AND page = 1)
        )
        ORDER BY rowid LIMIT 1
    """)
    suspend fun nextUnindexedPostCache(afterRowId: Long): UnindexedPostCache?

    @Query("""
        UPDATE api_cache SET searchIndexed = 1
        WHERE cacheKey = :key AND page = :page AND searchIndexed = 0
          AND cachedAt = :cachedAt AND jsonResponse = :json
    """)
    suspend fun markPostCacheIndexed(key: String, page: Int, cachedAt: Long, json: String): Int

    @Transaction
    suspend fun indexExistingCaches(batch: List<Pair<CacheEntity, List<CachePostPreview>>>) {
        for ((cache, previews) in batch) {
            if (markPostCacheIndexed(cache.cacheKey, cache.page, cache.cachedAt, cache.jsonResponse) != 0) {
                insertPostPreviews(previews)
            }
        }
    }

    @Query("""
        SELECT p.* FROM cache_post_previews p
        WHERE p.cacheKey IN (:feedKeys)
        UNION ALL
        SELECT p.* FROM cache_post_previews p
        WHERE p.cacheKey GLOB 'thread_*' AND p.page = 1
          AND (p.threadId IN (
              SELECT threadId FROM cloud_feed_threads WHERE feedUuid IN (:uuids)
          ) OR p.threadId IN (SELECT threadId FROM offline_threads))
        ORDER BY page
    """)
    fun observeFeedPostPreviews(uuids: List<String>, feedKeys: List<String>): Flow<List<CachePostPreview>>

    @Query("SELECT * FROM api_cache WHERE cacheKey = :cacheKey ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getLatestPage(cacheKey: String): CacheEntity?

    @Query("DELETE FROM api_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteCacheByKey(cacheKey: String)

    /** 只清串详情离线 JSON（thread_<id>），保留板块/时间线/引用等常规 SWR 缓存。 */
    @Query("DELETE FROM api_cache WHERE cacheKey LIKE 'thread_%'")
    suspend fun deleteAllThreadCache()

    @Query("DELETE FROM api_cache")
    suspend fun clearAllCache()

    @Query("DELETE FROM api_cache WHERE cacheKey NOT IN (SELECT 'thread_' || threadId FROM offline_threads)")
    suspend fun clearNonOfflineCache()

    @Query("DELETE FROM api_cache WHERE cachedAt < :expireTime AND cacheKey NOT IN (SELECT 'thread_' || threadId FROM offline_threads)")
    suspend fun deleteExpiredNonOfflineCache(expireTime: Long)

    @Query("SELECT SUM(LENGTH(CAST(jsonResponse AS BLOB))) FROM api_cache")
    suspend fun getTotalCacheSize(): Long?

    /**
     * Best-effort full-text scan over cached thread JSON (no server search API).
     * Limited to keep UI responsive.
     */
    @Query(
        """
        SELECT * FROM api_cache
        WHERE cacheKey GLOB 'thread_*'
          AND page = 1
          AND jsonResponse LIKE '%' || :query || '%'
        ORDER BY cachedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchThreadCache(query: String, limit: Int = 30): List<CacheEntity>
}

data class CacheRowSize(val rowId: Long, val bytes: Long)

data class UnindexedPostCache(val rowId: Long, @androidx.room.Embedded val cache: CacheEntity)
