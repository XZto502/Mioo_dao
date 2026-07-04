package com.mioo.dao.di

import com.mioo.dao.data.repository.FeedRepository
import com.mioo.dao.data.repository.FeedRepositoryImpl
import com.mioo.dao.data.repository.ForumRepository
import com.mioo.dao.data.repository.ForumRepositoryImpl
import com.mioo.dao.data.repository.ThreadRepository
import com.mioo.dao.data.repository.ThreadRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindForumRepository(
        impl: ForumRepositoryImpl
    ): ForumRepository

    @Binds
    @Singleton
    abstract fun bindThreadRepository(
        impl: ThreadRepositoryImpl
    ): ThreadRepository

    @Binds
    @Singleton
    abstract fun bindFeedRepository(
        impl: FeedRepositoryImpl
    ): FeedRepository
}
