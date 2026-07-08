package com.mioo.dao.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CacheDao
import com.mioo.dao.data.local.CacheEntity
import com.mioo.dao.data.local.HistoryDao
import com.mioo.dao.data.local.HistoryEntity
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
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

    // Cache management & preloading
    fun clearCache(): Flow<XdResponse<Unit>>
    fun getCacheSize(): Flow<String>
    fun preloadBookmarks(onProgress: (Int, Int) -> Unit): Flow<XdResponse<Unit>>
    fun smartPreloadThreads(threads: List<Thread>)
    fun downloadFullThread(tid: String): Flow<XdResponse<Unit>>
}

@Singleton
class ThreadRepositoryImpl @Inject constructor(
    private val apiService: XdApiService,
    private val historyDao: HistoryDao,
    private val cacheDao: CacheDao,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
    private val moshi: com.squareup.moshi.Moshi
) : ThreadRepository {

    private val threadListType = Types.newParameterizedType(List::class.java, Thread::class.java)
    private val threadListAdapter = moshi.adapter<List<Thread>>(threadListType)
    private val threadAdapter = moshi.adapter(Thread::class.java)
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getThreads(fid: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        val cacheKey = "showf_$fid"
        try {
            val response = apiService.showf(fid, page)
            val json = threadListAdapter.toJson(response)
            cacheDao.insertCache(CacheEntity(cacheKey, page, json))
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                try {
                    val cachedList = threadListAdapter.fromJson(cached.jsonResponse)
                    if (cachedList != null) {
                        emit(XdResponse.Success(cachedList))
                        return@flow
                    }
                } catch (jsonEx: Exception) {
                }
            }
            val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) "该板块或串不存在" else e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getTimeline(id: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        val cacheKey = "timeline_$id"
        try {
            val response = apiService.timeline(id, page)
            val json = threadListAdapter.toJson(response)
            cacheDao.insertCache(CacheEntity(cacheKey, page, json))
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                try {
                    val cachedList = threadListAdapter.fromJson(cached.jsonResponse)
                    if (cachedList != null) {
                        emit(XdResponse.Success(cachedList))
                        return@flow
                    }
                } catch (jsonEx: Exception) {
                }
            }
            val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) "该串不存在或已被删除" else e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getThreadDetail(tid: String, page: Int): Flow<XdResponse<Thread>> = flow {
        val cacheKey = "thread_$tid"
        try {
            val response = apiService.thread(tid, page)
            saveToHistory(response)
            val json = threadAdapter.toJson(response)
            cacheDao.insertCache(CacheEntity(cacheKey, page, json))
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                try {
                    val cachedThread = threadAdapter.fromJson(cached.jsonResponse)
                    if (cachedThread != null) {
                        emit(XdResponse.Success(cachedThread))
                        return@flow
                    }
                } catch (jsonEx: Exception) {
                }
            }
            val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) "该串不存在或已被删除" else e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getPoDetail(tid: String, page: Int): Flow<XdResponse<Thread>> = flow {
        val cacheKey = "po_$tid"
        try {
            val response = apiService.po(tid, page)
            val json = threadAdapter.toJson(response)
            cacheDao.insertCache(CacheEntity(cacheKey, page, json))
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                try {
                    val cachedThread = threadAdapter.fromJson(cached.jsonResponse)
                    if (cachedThread != null) {
                        emit(XdResponse.Success(cachedThread))
                        return@flow
                    }
                } catch (jsonEx: Exception) {
                }
            }
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

    override fun clearCache(): Flow<XdResponse<Unit>> = flow {
        try {
            cacheDao.clearAllCache()
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Clear cache failed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getCacheSize(): Flow<String> = flow {
        val sizeBytes = cacheDao.getTotalCacheSize() ?: 0L
        val sizeStr = when {
            sizeBytes <= 0 -> "0.0 KB"
            sizeBytes < 1024 * 1024 -> String.format("%.2f KB", sizeBytes.toDouble() / 1024.0)
            else -> String.format("%.2f MB", sizeBytes.toDouble() / (1024.0 * 1024.0))
        }
        emit(sizeStr)
    }.flowOn(Dispatchers.IO)

    override fun preloadBookmarks(onProgress: (Int, Int) -> Unit): Flow<XdResponse<Unit>> = flow {
        try {
            val bookmarks = historyDao.getAllBookmarks().first()
            if (bookmarks.isEmpty()) {
                emit(XdResponse.Success(Unit))
                return@flow
            }
            val total = bookmarks.size
            var current = 0
            for (bookmark in bookmarks) {
                val tid = bookmark.id
                try {
                    val response = apiService.thread(tid, 1)
                    val json = threadAdapter.toJson(response)
                    cacheDao.insertCache(CacheEntity("thread_$tid", 1, json))
                } catch (e: Exception) {
                }
                current++
                onProgress(current, total)
            }
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Preloading failed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun smartPreloadThreads(threads: List<Thread>) {
        preloadScope.launch {
            val mode = settingsDataStore.smartPreloadModeFlow.first()
            if (mode == "DISABLED") return@launch

            if (mode == "WIFI_ONLY") {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (!isWifi) return@launch
            }

            val preloadCount = settingsDataStore.preloadCountFlow.first()
            val threadsToPreload = threads.take(preloadCount)

            for (thread in threadsToPreload) {
                val tid = thread.idStr
                val cached = cacheDao.getCache("thread_$tid", 1)
                val isRecentlyCached = cached != null && (System.currentTimeMillis() - cached.cachedAt < 10 * 60 * 1000)
                if (isRecentlyCached) continue

                try {
                    val response = apiService.thread(tid, 1)
                    val json = threadAdapter.toJson(response)
                    cacheDao.insertCache(CacheEntity("thread_$tid", 1, json))
                    delay(500)
                } catch (e: Exception) {
                }
            }
        }
    }

    override fun downloadFullThread(tid: String): Flow<XdResponse<Unit>> = flow {
        try {
            val page1 = apiService.thread(tid, 1)
            saveToHistory(page1)
            addBookmark(page1)
            
            val json1 = threadAdapter.toJson(page1)
            cacheDao.insertCache(CacheEntity("thread_$tid", 1, json1))
            
            val totalReplies = page1.replyCount ?: 0
            val repliesPage1Size = page1.replies?.size ?: 0
            
            if (totalReplies > repliesPage1Size && repliesPage1Size > 0) {
                val pageSize = repliesPage1Size
                val totalPages = (totalReplies + pageSize - 1) / pageSize
                
                for (page in 2..totalPages) {
                    try {
                        val threadPage = apiService.thread(tid, page)
                        val jsonPage = threadAdapter.toJson(threadPage)
                        cacheDao.insertCache(CacheEntity("thread_$tid", page, jsonPage))
                        delay(300)
                    } catch (e: Exception) {
                    }
                }
            }
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Download failed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)
}
