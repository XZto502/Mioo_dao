package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Cloud-feed (X岛云端收藏夹) membership cache.
 *
 * Refreshed whenever a feed list loads (收藏 tab / background poller) or a feed
 * write succeeds, so the thread detail page can show "已收藏" for threads that
 * only live in the cloud feed. Only browsed pages are tracked.
 */
@Entity(
    tableName = "cloud_feed_threads",
    primaryKeys = ["feedUuid", "threadId"],
    indices = [Index(value = ["threadId"])]
)
data class CloudFeedThreadEntity(
    val feedUuid: String,
    val threadId: String,
    val savedAt: Long = System.currentTimeMillis()
)
