package com.mioo.dao.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        BookmarkEntity::class,
        CacheEntity::class,
        ThreadProgressEntity::class,
        CloudFeedThreadEntity::class,
        DraftEntity::class,
        OfflineThreadEntity::class,
        PostActionHistoryEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postActionHistoryDao(): PostActionHistoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun cacheDao(): CacheDao
    abstract fun progressDao(): ProgressDao
    abstract fun cloudFeedDao(): CloudFeedDao
    abstract fun draftDao(): DraftDao
    abstract fun offlineThreadDao(): OfflineThreadDao
}
