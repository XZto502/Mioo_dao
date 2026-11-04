package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostActionHistoryDao {
    @Query("""SELECT * FROM post_action_history WHERE type = :type AND
        (:query = '' OR content LIKE '%' || :query || '%' OR IFNULL(title, '') LIKE '%' || :query || '%'
        OR threadSummary LIKE '%' || :query || '%' OR IFNULL(threadTitle, '') LIKE '%' || :query || '%'
        OR IFNULL(threadId, '') LIKE '%' || :query || '%' OR cookieHash LIKE '%' || :query || '%'
        OR forumName LIKE '%' || :query || '%') ORDER BY timestamp DESC""")
    fun observe(type: String, query: String = ""): Flow<List<PostActionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PostActionHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<PostActionHistoryEntity>)

    @Query("SELECT * FROM post_action_history WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PostActionHistoryEntity?

    @Query("SELECT * FROM post_action_history WHERE type = 'NEW_THREAD' AND threadId IS NULL")
    suspend fun pendingThreads(): List<PostActionHistoryEntity>

    @Query("DELETE FROM post_action_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM post_action_history WHERE type = :type")
    suspend fun clear(type: String)

    // Resolve in place: a late network response must not resurrect a deleted record.
    @Query("""UPDATE post_action_history SET threadId = :threadId, cookieHash = :cookieHash,
        imageUrl = :imageUrl WHERE id = :id AND threadId IS NULL""")
    suspend fun resolve(id: String, threadId: String, cookieHash: String, imageUrl: String?)
}
