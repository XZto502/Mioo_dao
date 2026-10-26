package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: CacheEntity)

    @Query("SELECT * FROM api_cache WHERE cacheKey = :cacheKey AND page = :page LIMIT 1")
    suspend fun getCache(cacheKey: String, page: Int): CacheEntity?

    @Query("DELETE FROM api_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteCacheByKey(cacheKey: String)

    /** 只清串详情离线 JSON（thread_<id>），保留板块/时间线/引用等常规 SWR 缓存。 */
    @Query("DELETE FROM api_cache WHERE cacheKey LIKE 'thread_%'")
    suspend fun deleteAllThreadCache()

    @Query("DELETE FROM api_cache")
    suspend fun clearAllCache()

    @Query("SELECT SUM(LENGTH(jsonResponse)) FROM api_cache")
    suspend fun getTotalCacheSize(): Long?

    /**
     * Best-effort full-text scan over cached thread JSON (no server search API).
     * Limited to keep UI responsive.
     */
    @Query(
        """
        SELECT * FROM api_cache
        WHERE cacheKey LIKE 'thread_%'
          AND page = 1
          AND jsonResponse LIKE '%' || :query || '%'
        ORDER BY cachedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchThreadCache(query: String, limit: Int = 30): List<CacheEntity>
}
