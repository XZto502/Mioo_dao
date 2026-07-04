package com.mioo.dao.data.repository

import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.model.ForumGroup
import com.mioo.dao.data.model.XdResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

interface ForumRepository {
    fun getForumList(): Flow<XdResponse<List<ForumGroup>>>
    fun getTimelineList(): Flow<XdResponse<List<ForumGroup>>>
}

@Singleton
class ForumRepositoryImpl @Inject constructor(
    private val apiService: XdApiService
) : ForumRepository {

    override fun getForumList(): Flow<XdResponse<List<ForumGroup>>> = flow {
        try {
            val response = apiService.getForumList()
            if (response.isEmpty()) {
                emit(XdResponse.Success(getFallbackForumList()))
            } else {
                emit(XdResponse.Success(response))
            }
        } catch (e: Exception) {
            emit(XdResponse.Success(getFallbackForumList()))
        }
    }.flowOn(Dispatchers.IO)

    override fun getTimelineList(): Flow<XdResponse<List<ForumGroup>>> = flow {
        try {
            val response = apiService.getTimelineList()
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
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
