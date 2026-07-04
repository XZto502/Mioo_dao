package com.mioo.dao.data.repository

import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.HistoryDao
import com.mioo.dao.data.local.HistoryEntity
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ThreadRepository {
    fun getThreads(fid: String, page: Int): Flow<XdResponse<List<Thread>>>
    fun getTimeline(id: String, page: Int): Flow<XdResponse<List<Thread>>>
    fun getThreadDetail(tid: String, page: Int): Flow<XdResponse<Thread>>
    fun getPoDetail(tid: String, page: Int): Flow<XdResponse<Thread>>
    fun getRefPost(id: String): Flow<XdResponse<Reply>>

    fun doPostThread(
        fid: String,
        title: String?,
        name: String?,
        email: String?,
        content: String,
        imageFile: File?
    ): Flow<XdResponse<String>>

    fun doReplyThread(
        parent: String,
        title: String?,
        name: String?,
        email: String?,
        content: String,
        imageFile: File?
    ): Flow<XdResponse<String>>

    // Local DB operations
    fun getHistory(): Flow<List<HistoryEntity>>
    suspend fun saveToHistory(thread: Thread)
    suspend fun clearHistory()
    fun getBookmarks(): Flow<List<BookmarkEntity>>
    fun isBookmarked(id: String): Flow<Boolean>
    suspend fun addBookmark(thread: Thread)
    suspend fun removeBookmark(id: String)
    suspend fun insertBookmarks(threads: List<Thread>)

    // Remote Feed operations
    fun getFeed(uuid: String, page: Int): Flow<XdResponse<List<Thread>>>
    fun addFeed(uuid: String, tid: String): Flow<XdResponse<String>>
    fun delFeed(uuid: String, tid: String): Flow<XdResponse<String>>
}

@Singleton
class ThreadRepositoryImpl @Inject constructor(
    private val apiService: XdApiService,
    private val historyDao: HistoryDao,
    private val moshi: com.squareup.moshi.Moshi
) : ThreadRepository {

    override fun getThreads(fid: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        try {
            val response = apiService.showf(fid, page)
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) "该板块或串不存在" else e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getTimeline(id: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        try {
            val response = apiService.timeline(id, page)
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) "该串不存在或已被删除" else e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getThreadDetail(tid: String, page: Int): Flow<XdResponse<Thread>> = flow {
        try {
            val response = apiService.thread(tid, page)
            // Log to history asynchronously
            saveToHistory(response)
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) "该串不存在或已被删除" else e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getPoDetail(tid: String, page: Int): Flow<XdResponse<Thread>> = flow {
        try {
            val response = apiService.po(tid, page)
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) "该串不存在或已被删除" else e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRefPost(id: String): Flow<XdResponse<Reply>> = flow {
        try {
            val responseString = apiService.ref(id).string()
            val adapter = moshi.adapter(Reply::class.java)
            try {
                val reply = adapter.fromJson(responseString)
                if (reply != null) {
                    emit(XdResponse.Success(reply))
                } else {
                    emit(XdResponse.Error(message = "引用的串解析失败"))
                }
            } catch (e: Exception) {
                // Not a valid JSON reply, likely an HTML error string from the server (e.g., "该引用内容不存在")
                val cleanError = responseString.replace(Regex("<.*?>|\\n"), "").replace("\"", "").trim()
                val errorMsg = cleanError.ifEmpty { "该引用不存在或已被删除" }
                emit(XdResponse.Error(message = errorMsg))
            }
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Network error", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun doPostThread(
        fid: String,
        title: String?,
        name: String?,
        email: String?,
        content: String,
        imageFile: File?
    ): Flow<XdResponse<String>> = flow {
        try {
            val fidBody = fid.toRequestBody("text/plain".toMediaTypeOrNull())
            val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
            val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull())
            val emailBody = email?.toRequestBody("text/plain".toMediaTypeOrNull())
            val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())

            val imagePart = imageFile?.let {
                val requestFile = it.asRequestBody("image/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", it.name, requestFile)
            }

            val response = apiService.doPostThread(fidBody, titleBody, nameBody, emailBody, contentBody, imagePart)
            emit(XdResponse.Success(response.string()))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Posting thread failed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun doReplyThread(
        parent: String,
        title: String?,
        name: String?,
        email: String?,
        content: String,
        imageFile: File?
    ): Flow<XdResponse<String>> = flow {
        try {
            val response = if (imageFile != null) {
                // Use Multipart when there's an image
                val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
                val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull())
                val emailBody = email?.toRequestBody("text/plain".toMediaTypeOrNull())
                val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
                val parentBody = parent.toRequestBody("text/plain".toMediaTypeOrNull())
                val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
                apiService.doReplyThread(null, titleBody, nameBody, emailBody, contentBody, parentBody, imagePart)
            } else {
                // Use FormUrlEncoded for text-only replies (more reliable)
                apiService.doReplyThreadText(
                    resto = parent,
                    content = content,
                    name = name,
                    title = title,
                    email = email
                )
            }
            val responseStr = response.string()
            android.util.Log.d("ThreadRepo", "Reply response: $responseStr")
            emit(XdResponse.Success(responseStr))
        } catch (e: Exception) {
            android.util.Log.e("ThreadRepo", "Reply failed", e)
            emit(XdResponse.Error(message = e.localizedMessage ?: "Replying thread failed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    // --- History & Bookmarks ---

    override fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    override suspend fun saveToHistory(thread: Thread) {
        historyDao.insertHistory(
            HistoryEntity(
                id = thread.idStr,
                fid = thread.fidStr,
                content = thread.content,
                userid = thread.userHash,
                now = thread.now,
                title = thread.title,
                name = thread.name
            )
        )
    }

    override suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    override fun getBookmarks(): Flow<List<BookmarkEntity>> = historyDao.getAllBookmarks()

    override fun isBookmarked(id: String): Flow<Boolean> = historyDao.isBookmarked(id)

    override suspend fun addBookmark(thread: Thread) {
        historyDao.insertBookmark(
            BookmarkEntity(
                id = thread.idStr,
                fid = thread.fidStr,
                content = thread.content,
                userid = thread.userHash,
                now = thread.now,
                title = thread.title,
                name = thread.name
            )
        )
    }

    override suspend fun removeBookmark(id: String) {
        historyDao.deleteBookmarkById(id)
    }

    override suspend fun insertBookmarks(threads: List<Thread>) {
        threads.forEach { thread ->
            val entity = BookmarkEntity(
                id = thread.idStr,
                fid = thread.fidStr,
                content = thread.content,
                userid = thread.userHash,
                now = thread.now,
                title = thread.title,
                name = thread.name
            )
            historyDao.insertBookmark(entity)
        }
    }

    override fun getFeed(uuid: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        try {
            val response = apiService.feed(uuid, page)
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun addFeed(uuid: String, tid: String): Flow<XdResponse<String>> = flow {
        try {
            val response = apiService.addFeed(uuid, tid)
            emit(XdResponse.Success(response.string()))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun delFeed(uuid: String, tid: String): Flow<XdResponse<String>> = flow {
        try {
            val response = apiService.delFeed(uuid, tid)
            emit(XdResponse.Success(response.string()))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)
}
