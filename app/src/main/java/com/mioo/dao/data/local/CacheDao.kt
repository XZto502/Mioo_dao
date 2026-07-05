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

    @Query("DELETE FROM api_cache")
    suspend fun clearAllCache()

    @Query("SELECT SUM(LENGTH(jsonResponse)) FROM api_cache")
    suspend fun getTotalCacheSize(): Long?
}
