package com.mioo.dao.di

import android.content.Context
import androidx.room.Room
import com.mioo.dao.data.local.AppDatabase
import com.mioo.dao.data.local.HistoryDao
import com.mioo.dao.data.local.CacheDao
import com.mioo.dao.data.local.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mioo_dao.db"
        )
            // Versions 1–3 were never given a real migration. Keep that one-time
            // destructive path. Version 4 (favorites, history, progress) must survive
            // later upgrades, so do not use unconditional fallbackToDestructiveMigration().
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            // First open happens on IO during Application warm — keep main free for Compose.
            .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(2))
            .setTransactionExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideCacheDao(database: AppDatabase): CacheDao {
        return database.cacheDao()
    }

    @Provides
    @Singleton
    fun provideProgressDao(database: AppDatabase): com.mioo.dao.data.local.ProgressDao {
        return database.progressDao()
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }
}
