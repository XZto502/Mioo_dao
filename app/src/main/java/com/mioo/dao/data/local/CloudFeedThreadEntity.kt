package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/**
 * Cloud-feed (X岛云端收藏夹) membership cache.
 *
 * Refreshed whenever a feed list loads (收藏 tab / background poller) or a feed
 * write succeeds, so the thread detail page can show "已收藏" for threads that
 * only live in the cloud feed. Stores title metadata without post bodies.
 *
 * [replyCount] 缓存最近一次抓到的回复总数：feed 接口本身不返回任何回复数字段，
 * 收藏界面显示时需要额外逐串补抓，这里把结果留到下次直接上屏。
 */
@Entity(
    tableName = "cloud_feed_threads",
    primaryKeys = ["feedUuid", "threadId"],
    indices = [Index(value = ["threadId"])]
)
data class CloudFeedThreadEntity(
    val feedUuid: String,
    val threadId: String,
    val savedAt: Long = System.currentTimeMillis(),
    val replyCount: Int = 0,
    val title: String? = null,
    @ColumnInfo(defaultValue = "2147483647")
    val position: Int = Int.MAX_VALUE
)

@Entity(tableName = "cloud_feed_sync")
data class CloudFeedSyncEntity(
    @PrimaryKey val feedUuid: String,
    val lastCheckedAt: Long,
    val lastFullScanAt: Long
)
