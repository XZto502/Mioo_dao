package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    // --- History operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    /** Cap history rows so the history screen never materializes an unbounded list. */
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun getAllHistory(limit: Int = 300): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT * FROM history
        WHERE id LIKE '%' || :query || '%'
           OR IFNULL(title, '') LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
           OR userid LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchHistory(query: String, limit: Int = 50): List<HistoryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM history WHERE id = :id LIMIT 1)")
    fun isInHistory(id: String): Flow<Boolean>

    // --- Bookmark operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE id LIKE '%' || :query || '%'
           OR IFNULL(title, '') LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
           OR userid LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchBookmarks(query: String, limit: Int = 50): List<BookmarkEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE id = :id LIMIT 1)")
    fun isBookmarked(id: String): Flow<Boolean>

    @Query(
        """
        UPDATE bookmarks
        SET lastKnownReplyCount = :knownCount
        WHERE id = :id
        """
    )
    suspend fun updateKnownReplyCount(id: String, knownCount: Int)

    @Query(
        """
        UPDATE bookmarks
        SET lastReadReplyCount = :readCount,
            lastKnownReplyCount = CASE
                WHEN lastKnownReplyCount < :readCount THEN :readCount
                ELSE lastKnownReplyCount
            END
        WHERE id = :id
        """
    )
    suspend fun markBookmarkRead(id: String, readCount: Int)
}
