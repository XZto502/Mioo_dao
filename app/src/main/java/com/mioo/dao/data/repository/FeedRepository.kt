package com.mioo.dao.data.repository

import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

interface FeedRepository {
    fun getFeeds(page: Int): Flow<XdResponse<List<Thread>>>
    fun addFeed(tid: String): Flow<XdResponse<String>>
    fun delFeed(tid: String): Flow<XdResponse<String>>
}

@Singleton
class FeedRepositoryImpl @Inject constructor(
    private val apiService: XdApiService,
    private val settingsDataStore: SettingsDataStore
) : FeedRepository {

    private suspend fun getUserHash(): String? {
        return settingsDataStore.userHashFlow.first()
    }

    override fun getFeeds(page: Int): Flow<XdResponse<List<Thread>>> = flow {
        try {
            val uuid = getUserHash()
            val response = apiService.feed(uuid, page)
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Failed to retrieve feeds", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun addFeed(tid: String): Flow<XdResponse<String>> = flow {
        try {
            val uuid = getUserHash()
            val response = apiService.addFeed(uuid, tid)
            emit(XdResponse.Success(response.string()))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Failed to add feed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun delFeed(tid: String): Flow<XdResponse<String>> = flow {
        try {
            val uuid = getUserHash()
            val response = apiService.delFeed(uuid, tid)
            emit(XdResponse.Success(response.string()))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Failed to delete feed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)
}
