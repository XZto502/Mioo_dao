package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionWatchDao {
    @Query("SELECT * FROM subscription_watch")
    suspend fun getAll(): List<SubscriptionWatchEntity>

    @Query("SELECT threadId FROM subscription_watch")
    suspend fun getAllThreadIds(): List<String>

    @Query("SELECT * FROM subscription_watch WHERE threadId = :threadId LIMIT 1")
    suspend fun getById(threadId: String): SubscriptionWatchEntity?

    @Query("SELECT notifyMode FROM subscription_watch WHERE threadId = :threadId LIMIT 1")
    fun observeNotifyMode(threadId: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SubscriptionWatchEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SubscriptionWatchEntity)

    @Query("UPDATE subscription_watch SET notifyMode = :mode WHERE threadId = :threadId")
    suspend fun updateNotifyMode(threadId: String, mode: Int)

    @Query("UPDATE subscription_watch SET nextCheckAt = :maxNextCheckAt WHERE nextCheckAt > :maxNextCheckAt")
    suspend fun clampNextCheckAt(maxNextCheckAt: Long)

    @Query(
        """
        UPDATE subscription_watch
        SET lastReadReplyCount = MAX(lastReadReplyCount, :replyCount),
            lastNotifiedReplyCount = MAX(lastNotifiedReplyCount, :replyCount),
            lastKnownReplyCount = MAX(lastKnownReplyCount, :replyCount),
            lastNotifiedReplyId = MAX(lastNotifiedReplyId, :latestReplyId),
            lastActivityAt = CASE
                WHEN :replyCount > lastKnownReplyCount THEN :nowMs
                ELSE lastActivityAt
            END
        WHERE threadId = :threadId
        """
    )
    suspend fun markRead(
        threadId: String,
        replyCount: Int,
        latestReplyId: Long,
        nowMs: Long
    )

    @Query(
        """
        UPDATE subscription_watch
        SET lastActivityAt = CASE
                WHEN :replyCount > lastKnownReplyCount THEN :nowMs
                ELSE lastActivityAt
            END,
            lastKnownReplyCount = MAX(lastKnownReplyCount, :replyCount),
            title = COALESCE(:title, title),
            poUserHash = CASE
                WHEN :poUserHash IS NOT NULL AND :poUserHash != '' THEN :poUserHash
                ELSE poUserHash
            END,
            consecutiveIdleChecks = CASE
                WHEN :replyCount > lastKnownReplyCount THEN 0
                ELSE consecutiveIdleChecks
            END
        WHERE threadId = :threadId
        """
    )
    suspend fun updateObservedCount(
        threadId: String,
        replyCount: Int,
        title: String?,
        poUserHash: String?,
        nowMs: Long
    )

    @Query("DELETE FROM subscription_watch WHERE threadId = :threadId")
    suspend fun deleteById(threadId: String)
}
