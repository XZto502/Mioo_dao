package com.mioo.dao.data.repository

import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.model.ForumGroup
import com.mioo.dao.data.model.XdResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mioo.dao.data.local.CacheDao
import com.mioo.dao.data.local.CacheEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton

interface ForumRepository {
    fun getForumList(): Flow<XdResponse<List<ForumGroup>>>
    fun getTimelineList(): Flow<XdResponse<List<ForumGroup>>>
}

@Singleton
class ForumRepositoryImpl @Inject constructor(
    private val apiService: XdApiService,
    private val cacheDao: CacheDao,
    moshi: Moshi
) : ForumRepository {
    private val listAdapter = moshi.adapter<List<ForumGroup>>(Types.newParameterizedType(List::class.java, ForumGroup::class.java))
    private val mutex = Mutex()
    private var memory: Pair<Long, List<ForumGroup>>? = null

    override fun getForumList(): Flow<XdResponse<List<ForumGroup>>> = flow {
        try {
            val result = mutex.withLock {
                val now = System.currentTimeMillis()
                memory?.takeIf { now - it.first in 0..21_600_000L }?.let { return@withLock it.second }
                val disk = cacheDao.getCache("forum_list", 1)
                val cached = memory?.second ?: disk?.let { runCatching { listAdapter.fromJson(it.jsonResponse) }.getOrNull() }
                if (!cached.isNullOrEmpty() && disk != null && now - disk.cachedAt in 0..21_600_000L) {
                    memory = disk.cachedAt to cached
                    return@withLock cached
                }
                try {
                    val response = apiService.getForumList()
                    check(response.isNotEmpty()) { "板块列表为空，请稍后重试" }
                    memory = now to response
                    cacheDao.insertCache(CacheEntity("forum_list", 1, listAdapter.toJson(response)))
                    response
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    cached?.takeIf { it.isNotEmpty() } ?: throw e
                }
            }
            emit(XdResponse.Success(result))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(XdResponse.Error(message = e.localizedMessage ?: "无法获取板块列表", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getTimelineList(): Flow<XdResponse<List<ForumGroup>>> = flow {
        try {
            val response = apiService.getTimelineList()
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(XdResponse.Error(message = e.localizedMessage ?: "Unknown network error", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    private fun getFallbackForumList(): List<ForumGroup> {
        return listOf(
            ForumGroup(
                id = "1",
                name = "综合版块",
                forums = listOf(
                    com.mioo.dao.data.model.Forum(id = "4", name = "综合版1"),
                    com.mioo.dao.data.model.Forum(id = "40", name = "欢乐恶搞"),
                    com.mioo.dao.data.model.Forum(id = "20", name = "绝密版")
                )
            ),
            ForumGroup(
                id = "2",
                name = "二次元",
                forums = listOf(
                    com.mioo.dao.data.model.Forum(id = "14", name = "动漫"),
                    com.mioo.dao.data.model.Forum(id = "11", name = "轻小说"),
                    com.mioo.dao.data.model.Forum(id = "103", name = "VTuber"),
                    com.mioo.dao.data.model.Forum(id = "10", name = "中二")
                )
            ),
            ForumGroup(
                id = "3",
                name = "游戏",
                forums = listOf(
                    com.mioo.dao.data.model.Forum(id = "17", name = "游戏"),
                    com.mioo.dao.data.model.Forum(id = "32", name = "手游"),
                    com.mioo.dao.data.model.Forum(id = "31", name = "网游"),
                    com.mioo.dao.data.model.Forum(id = "110", name = "跑团")
                )
            ),
            ForumGroup(
                id = "4",
                name = "生活",
                forums = listOf(
                    com.mioo.dao.data.model.Forum(id = "35", name = "影视"),
                    com.mioo.dao.data.model.Forum(id = "36", name = "音乐"),
                    com.mioo.dao.data.model.Forum(id = "98", name = "技术")
                )
            )
        )
    }
}
