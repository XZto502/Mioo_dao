package com.mioo.dao.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        BookmarkEntity::class,
        CacheEntity::class,
        ThreadProgressEntity::class,
        CloudFeedThreadEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun cacheDao(): CacheDao
    abstract fun progressDao(): ProgressDao
    abstract fun cloudFeedDao(): CloudFeedDao
}
