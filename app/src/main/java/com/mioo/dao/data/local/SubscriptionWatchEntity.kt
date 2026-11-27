package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object SubscriptionNotifyMode {
    const val MUTE = 0
    const val ALL = 1
    const val PO_ONLY = 2
}

object SubscriptionWatchSource {
    const val LOCAL_BOOKMARK = "LOCAL_BOOKMARK"
    const val CLOUD_FEED = "CLOUD_FEED"
    const val BOTH = "BOTH"
}

@Entity(tableName = "subscription_watch")
data class SubscriptionWatchEntity(
    @PrimaryKey val threadId: String,
    val title: String? = null,
    val poUserHash: String = "",
    val source: String = SubscriptionWatchSource.LOCAL_BOOKMARK,
    val notifyMode: Int = SubscriptionNotifyMode.ALL,
    val lastReadReplyCount: Int = 0,
    val lastNotifiedReplyCount: Int = 0,
    val lastKnownReplyCount: Int = 0,
    val lastNotifiedReplyId: Long = 0L,
    val lastActivityAt: Long = 0L,
    val lastCheckedAt: Long = 0L,
    val nextCheckAt: Long = 0L,
    val consecutiveIdleChecks: Int = 0
)
