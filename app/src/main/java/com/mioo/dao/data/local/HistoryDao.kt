package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("""SELECT h.*, COALESCE(p.page, 1) AS lastReadPage FROM history h
        LEFT JOIN thread_progress p ON p.threadId = h.id
        WHERE :query = '' OR h.content LIKE '%' || :query || '%' OR IFNULL(h.title, '') LIKE '%' || :query || '%'
        OR h.id LIKE '%' || :query || '%' OR h.userid LIKE '%' || :query || '%'
        OR h.forumName LIKE '%' || :query || '%' ORDER BY h.timestamp DESC""")
    fun observeBrowsing(query: String = ""): Flow<List<BrowseHistoryRecord>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)

    @Query("SELECT DISTINCT fid FROM history WHERE forumName = '' AND fid IS NOT NULL")
    suspend fun missingForumIds(): List<String>

    @Query("UPDATE history SET forumName = :name WHERE fid = :fid AND forumName = ''")
    suspend fun fillForumName(fid: String, name: String)

    // --- History operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(histories: List<HistoryEntity>)

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

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: String): HistoryEntity?

    /** Bump recency without rewriting content (avoids REPLACE thrash). */
    @Query("UPDATE history SET timestamp = :timestamp WHERE id = :id")
    suspend fun touchHistory(id: String, timestamp: Long = System.currentTimeMillis())

    // --- Bookmark operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAllBookmarks()

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

    /**
     * Background-refresh metadata WITHOUT touching lastReadReplyCount and without
     * inserting: a full-row REPLACE from a stale snapshot would roll back the read
     * marker (false unread badge) and resurrect a bookmark the user just deleted.
     */
    @Query(
        """
        UPDATE bookmarks
        SET content = :content,
            title = :title,
            now = :now,
            lastKnownReplyCount = :knownCount
        WHERE id = :id
        """
    )
    suspend fun updateBookmarkMetadata(
        id: String,
        content: String,
        title: String?,
        now: String,
        knownCount: Int
    )

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
