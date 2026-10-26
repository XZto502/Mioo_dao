package com.mioo.dao.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.api.GithubApiService
import com.mioo.dao.data.api.extractQuotedJsonString
import com.mioo.dao.data.api.isXdBusinessPlainString
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CacheDao
import com.mioo.dao.data.local.CacheEntity
import com.mioo.dao.data.local.HistoryDao
import com.mioo.dao.data.local.HistoryEntity
import com.mioo.dao.data.local.ProgressDao
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.local.ThreadProgressEntity
import com.mioo.dao.data.model.LocalSearchHit
import com.mioo.dao.data.model.LocalSearchResult
import com.mioo.dao.data.model.LocalSearchSource
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.data.model.effectiveTitle
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One bookmarked thread that has unread new replies after a poll. */
data class BookmarkNewReply(
    val threadId: String,
    val title: String?,
    val newReplyDelta: Int,
    val totalReplies: Int
)

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
        imageFile: File?,
        cookie: String? = null
    ): Flow<XdResponse<String>>

    fun doReplyThread(
        parent: String,
        title: String?,
        name: String?,
        email: String?,
        content: String,
        imageFile: File?,
        cookie: String? = null
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
    suspend fun markBookmarkRead(threadId: String, replyCount: Int)
    suspend fun refreshBookmarkReplyCounts(limit: Int = 20)

    /**
     * Poll X-island subscription sources for new replies (background notification).
     * Sources: local bookmarks + cloud feed folders (official feed/uuid API).
     */
    suspend fun pollSubscriptionUpdatesForNotification(limit: Int = 30): List<BookmarkNewReply>

    // Reading progress
    suspend fun saveThreadProgress(threadId: String, page: Int, firstVisibleIndex: Int)
    suspend fun getThreadProgress(threadId: String): ThreadProgressEntity?
    suspend fun clearThreadProgress(threadId: String)

    /** Local-only search (no server search API). */
    suspend fun searchLocal(query: String): LocalSearchResult

    // Remote Feed operations
    fun getFeed(uuid: String, page: Int): Flow<XdResponse<List<Thread>>>
    fun addFeed(uuid: String, tid: String): Flow<XdResponse<String>>
    fun delFeed(uuid: String, tid: String): Flow<XdResponse<String>>

    /** Cloud-feed membership cache: is this thread in any known 云端收藏夹? */
    fun isInCloudFeed(tid: String): Flow<Boolean>

    /** Remove [tid] from every cloud feed it is cached under. Emits removed count. */
    fun removeCloudSubscriptions(tid: String): Flow<XdResponse<Int>>

    /** 该夹内已缓存的回复数（threadId → count，仅含 >0 的已知值）。 */
    suspend fun getCloudFeedReplyCounts(uuid: String): Map<String, Int>

    /**
     * 补抓一个云端订阅串的回复总数并写入缓存。
     * feed 接口不返回回复数，只能按串抓第 1 页；失败返回 null。
     */
    suspend fun refreshCloudFeedReplyCount(tid: String): Int?

    // 发言草稿箱 (draft box): reply drafts keyed by thread, post drafts keyed by board.
    fun getDrafts(): Flow<List<com.mioo.dao.data.local.DraftEntity>>
    suspend fun getReplyDraftContent(threadId: String): String
    suspend fun saveReplyDraft(threadId: String, threadTitle: String?, content: String)
    suspend fun savePostDraft(boardId: String, boardName: String, name: String?, title: String?, content: String)
    suspend fun deleteDraft(id: Long)
    /** 删除某板块的全部发串草稿（发送成功后清理，防止草稿箱残留已发内容）。 */
    suspend fun deletePostDrafts(boardId: String)

    // Cache management & preloading
    fun clearCache(): Flow<XdResponse<Unit>>
    fun getCacheSize(): Flow<String>
    fun preloadBookmarks(onProgress: (Int, Int) -> Unit): Flow<XdResponse<Unit>>
    fun smartPreloadThreads(threads: List<Thread>)
    fun downloadFullThread(tid: String): Flow<XdResponse<Unit>>
    fun checkLatestRelease(): Flow<XdResponse<GithubRelease>>

    // 已离线串 (offline threads)
    /** 管理页列表：按下载时间倒序，体积含离线图片。 */
    fun getOfflineThreadSummaries(): Flow<List<com.mioo.dao.data.local.OfflineThreadSummary>>

    /** 串详情页三点菜单的「已离线」状态。 */
    fun isThreadDownloaded(threadId: String): Flow<Boolean>

    /** 删除单个离线串：标记行 + 离线 JSON + 仅属于该串的离线图片，并同步取消本地收藏。 */
    suspend fun deleteOfflineThread(threadId: String)

    /** 清空全部离线串并释放空间（含对应本地收藏；不动板块/时间线等常规 SWR 缓存）。 */
    suspend fun clearAllOfflineThreads()

    /**
     * 后台巡检：本地收藏 ⇒ 已离线。
     * 收藏页「已离线」标签直接展示离线串，星标新增的收藏由本巡检逐串补离线下载
     * （限速 2s/串），保证「收藏 = 已离线」同一批串。进程级单例循环。
     */
    fun startBookmarkOfflineSync()
}

@Singleton
class ThreadRepositoryImpl @Inject constructor(
    private val apiService: XdApiService,
    private val githubApiService: GithubApiService,
    private val historyDao: HistoryDao,
    private val cacheDao: CacheDao,
    private val progressDao: ProgressDao,
    private val cloudFeedDao: com.mioo.dao.data.local.CloudFeedDao,
    private val draftDao: com.mioo.dao.data.local.DraftDao,
    private val offlineThreadDao: com.mioo.dao.data.local.OfflineThreadDao,
    private val forumRepository: ForumRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
    private val moshi: com.squareup.moshi.Moshi
) : ThreadRepository {

    companion object {
        private val HTML_OR_NEWLINE = Regex("<.*?>|\\n")
        private val RELEASE_MANIFEST_URLS = listOf(
            "https://ghproxy.net/https://raw.githubusercontent.com/XZto502/Mioo_dao/main/version.json",
            "https://ghfast.top/https://raw.githubusercontent.com/XZto502/Mioo_dao/main/version.json",
            "https://cdn.jsdelivr.net/gh/XZto502/Mioo_dao@main/version.json",
            "https://raw.githubusercontent.com/XZto502/Mioo_dao/main/version.json",
            "https://api.github.com/repos/XZto502/Mioo_dao/releases/latest"
        )
    }

    /** X-island business error (auth / deleted / rate limit) — not a transport failure. */
    private class ApiBusinessException(message: String) : Exception(message)

    private fun interpretPlainFeedWrite(raw: String): XdResponse<String> {
        val quoted = extractQuotedJsonString(raw.trim())
        val text = (quoted ?: raw.trim().trim('"')).ifBlank { raw }
        return if (quoted != null && isXdBusinessPlainString(quoted)) {
            XdResponse.Error(message = quoted)
        } else {
            XdResponse.Success(text)
        }
    }

    private fun File.asImagePart(): MultipartBody.Part {
        val mime = when (extension.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return MultipartBody.Part.createFormData(
            "image",
            name,
            asRequestBody(mime.toMediaTypeOrNull())
        )
    }

    private val threadListType = Types.newParameterizedType(List::class.java, Thread::class.java)
    private val threadListAdapter = moshi.adapter<List<Thread>>(threadListType)
    private val threadAdapter = moshi.adapter(Thread::class.java)
    private val replyAdapter = moshi.adapter(Reply::class.java)
    private val stringListAdapter =
        moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Parse raw `thread`/`po` body. Rejects `{success:false,error:...}` before Moshi
     * so we don't thrash on "Required value 'id' missing" and never cache error JSON.
     */
    private fun parseThreadBody(raw: String): Thread {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw ApiBusinessException("空响应")
        extractQuotedJsonString(trimmed)?.let { msg ->
            throw ApiBusinessException(msg.ifBlank { "该串不存在或已被删除" })
        }
        if (trimmed.startsWith("{")) {
            try {
                val o = org.json.JSONObject(trimmed)
                val hasId = o.has("id")
                val successFalse = o.has("success") && !o.optBoolean("success", true)
                val errorOnly = o.has("error") && !hasId
                if (successFalse || errorOnly) {
                    val msg = sequenceOf("error", "msg", "message")
                        .map { o.optString(it) }
                        .firstOrNull { it.isNotBlank() }
                    throw ApiBusinessException(msg ?: "访问被拒绝，请先在设置中添加饼干")
                }
            } catch (e: ApiBusinessException) {
                throw e
            } catch (_: Exception) {
                // fall through to Moshi
            }
        }
        return threadAdapter.fromJson(raw)
            ?: throw ApiBusinessException("解析串失败")
    }

    private fun mapThreadException(e: Exception): String = when (e) {
        is ApiBusinessException -> e.message ?: "请求失败"
        is com.squareup.moshi.JsonDataException -> when {
            e.message?.contains("Expected BEGIN_") == true -> "该串不存在或已被删除"
            e.message?.contains("id", ignoreCase = true) == true ->
                "需要登录饼干后才能查看串详情"
            else -> e.localizedMessage ?: "解析失败"
        }
        else -> e.localizedMessage ?: "Network error"
    }

    private suspend fun fetchThread(tid: String, page: Int): Thread {
        val raw = apiService.thread(tid, page).string()
        return parseThreadBody(raw)
    }

    private suspend fun fetchPo(tid: String, page: Int): Thread {
        val raw = apiService.po(tid, page).string()
        return parseThreadBody(raw)
    }

    private suspend fun hasUserCookie(): Boolean {
        val list = runCatching { settingsDataStore.cookiesListFlow.first() }.getOrNull()
        if (list != null && list.any { it.isNotBlank() }) return true
        val hash = runCatching { settingsDataStore.userHashFlow.first() }.getOrNull()
        return !hash.isNullOrBlank()
    }

    override fun getThreads(fid: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        val cacheKey = "showf_$fid"
        var emittedCache = false
        var cachedJson: String? = null
        try {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                cachedJson = cached.jsonResponse
                val cachedList = runCatching { threadListAdapter.fromJson(cached.jsonResponse) }.getOrNull()
                if (cachedList != null) {
                    emit(XdResponse.Success(cachedList))
                    emittedCache = true
                }
            }
            val response = apiService.showf(fid, page)
            val networkJson = threadListAdapter.toJson(response)
            // SWR: skip identical network payload so UI does not rebuild twice
            if (!(emittedCache && cachedJson == networkJson)) {
                cacheDao.insertCache(CacheEntity(cacheKey, page, networkJson))
                emit(XdResponse.Success(response))
            }
        } catch (e: Exception) {
            if (!emittedCache) {
                val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) {
                    "该板块或串不存在"
                } else {
                    e.localizedMessage ?: "Network error"
                }
                emit(XdResponse.Error(message = msg, throwable = e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getTimeline(id: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        val cacheKey = "timeline_$id"
        var emittedCache = false
        var cachedJson: String? = null
        try {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                cachedJson = cached.jsonResponse
                val cachedList = runCatching { threadListAdapter.fromJson(cached.jsonResponse) }.getOrNull()
                if (cachedList != null) {
                    emit(XdResponse.Success(cachedList))
                    emittedCache = true
                }
            }
            val response = apiService.timeline(id, page)
            val networkJson = threadListAdapter.toJson(response)
            if (!(emittedCache && cachedJson == networkJson)) {
                cacheDao.insertCache(CacheEntity(cacheKey, page, networkJson))
                emit(XdResponse.Success(response))
            }
        } catch (e: Exception) {
            if (!emittedCache) {
                val msg = if (e is com.squareup.moshi.JsonDataException && e.message?.contains("Expected BEGIN_") == true) {
                    "该串不存在或已被删除"
                } else {
                    e.localizedMessage ?: "Network error"
                }
                emit(XdResponse.Error(message = msg, throwable = e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getThreadDetail(tid: String, page: Int): Flow<XdResponse<Thread>> = flow {
        val cacheKey = "thread_$tid"
        var emittedCache = false
        var cachedJson: String? = null
        try {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                // Never surface a cached error envelope as a thread
                val cachedThread = runCatching { parseThreadBody(cached.jsonResponse) }.getOrNull()
                if (cachedThread != null) {
                    cachedJson = cached.jsonResponse
                    emit(XdResponse.Success(cachedThread))
                    emittedCache = true
                }
            }
            val response = fetchThread(tid, page)
            // First page only; saveToHistory no-ops thrash when content/timestamp are fresh
            if (page <= 1) {
                saveToHistory(response)
            }
            val networkJson = threadAdapter.toJson(response)
            if (!(emittedCache && cachedJson == networkJson)) {
                cacheDao.insertCache(CacheEntity(cacheKey, page, networkJson))
                emit(XdResponse.Success(response))
            }
        } catch (e: Exception) {
            if (!emittedCache) {
                emit(XdResponse.Error(message = mapThreadException(e), throwable = e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getPoDetail(tid: String, page: Int): Flow<XdResponse<Thread>> = flow {
        val cacheKey = "po_$tid"
        var emittedCache = false
        var cachedJson: String? = null
        try {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                val cachedThread = runCatching { parseThreadBody(cached.jsonResponse) }.getOrNull()
                if (cachedThread != null) {
                    cachedJson = cached.jsonResponse
                    emit(XdResponse.Success(cachedThread))
                    emittedCache = true
                }
            }
            val response = fetchPo(tid, page)
            val networkJson = threadAdapter.toJson(response)
            if (!(emittedCache && cachedJson == networkJson)) {
                cacheDao.insertCache(CacheEntity(cacheKey, page, networkJson))
                emit(XdResponse.Success(response))
            }
        } catch (e: Exception) {
            if (!emittedCache) {
                emit(XdResponse.Error(message = mapThreadException(e), throwable = e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getRefPost(id: String): Flow<XdResponse<Reply>> = flow {
        try {
            val responseString = apiService.ref(id).string()
            extractQuotedJsonString(responseString)?.let { msg ->
                emit(XdResponse.Error(message = msg.ifBlank { "该引用不存在或已被删除" }))
                return@flow
            }
            try {
                val reply = replyAdapter.fromJson(responseString)
                if (reply != null) {
                    emit(XdResponse.Success(reply))
                } else {
                    emit(XdResponse.Error(message = "引用的串解析失败"))
                }
            } catch (e: Exception) {
                // Not a valid JSON reply, likely an HTML error string from the server
                val cleanError = responseString
                    .replace(HTML_OR_NEWLINE, "")
                    .replace("\"", "")
                    .trim()
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
        imageFile: File?,
        cookie: String?
    ): Flow<XdResponse<String>> = flow {
        try {
            val fidBody = fid.toRequestBody("text/plain".toMediaTypeOrNull())
            val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
            val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull())
            val emailBody = email?.toRequestBody("text/plain".toMediaTypeOrNull())
            val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())

            val imagePart = imageFile?.asImagePart()

            val response = apiService.doPostThread(cookie, fidBody, titleBody, nameBody, emailBody, contentBody, imagePart)
            emit(interpretForumWriteResponse(response.string()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(XdResponse.Error(message = e.localizedMessage ?: "发串失败", throwable = e))
        } finally {
            imageFile?.delete()
        }
    }.flowOn(Dispatchers.IO)

    override fun doReplyThread(
        parent: String,
        title: String?,
        name: String?,
        email: String?,
        content: String,
        imageFile: File?,
        cookie: String?
    ): Flow<XdResponse<String>> = flow {
        try {
            val response = if (imageFile != null) {
                // Use Multipart when there's an image
                val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
                val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull())
                val emailBody = email?.toRequestBody("text/plain".toMediaTypeOrNull())
                val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
                val parentBody = parent.toRequestBody("text/plain".toMediaTypeOrNull())
                val imagePart = imageFile.asImagePart()
                apiService.doReplyThread(cookie, null, titleBody, nameBody, emailBody, contentBody, parentBody, imagePart)
            } else {
                // Use FormUrlEncoded for text-only replies (more reliable)
                apiService.doReplyThreadText(
                    cookieOverride = cookie,
                    resto = parent,
                    content = content,
                    name = name,
                    title = title,
                    email = email
                )
            }
            emit(interpretForumWriteResponse(response.string()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (com.mioo.dao.BuildConfig.DEBUG) {
                android.util.Log.e("ThreadRepo", "Reply failed", e)
            }
            emit(XdResponse.Error(message = e.localizedMessage ?: "回复失败", throwable = e))
        } finally {
            imageFile?.delete()
        }
    }.flowOn(Dispatchers.IO)

    // --- History & Bookmarks ---

    override fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    override suspend fun saveToHistory(thread: Thread) {
        val existing = historyDao.getHistoryById(thread.idStr)
        if (existing != null) {
            // Content unchanged: only bump timestamp if older than 2 minutes
            val contentSame =
                existing.content == thread.content &&
                    existing.title == thread.title &&
                    existing.userid == thread.userHash
            if (contentSame) {
                if (System.currentTimeMillis() - existing.timestamp > 120_000L) {
                    historyDao.touchHistory(thread.idStr)
                }
                return
            }
        }
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
        val count = thread.replyCount ?: 0
        historyDao.insertBookmark(
            BookmarkEntity(
                id = thread.idStr,
                fid = thread.fidStr,
                content = thread.content,
                userid = thread.userHash,
                now = thread.now,
                title = thread.title,
                name = thread.name,
                lastReadReplyCount = count,
                lastKnownReplyCount = count
            )
        )
    }

    override suspend fun removeBookmark(id: String) {
        historyDao.deleteBookmarkById(id)
    }

    override suspend fun insertBookmarks(threads: List<Thread>) {
        if (threads.isEmpty()) return
        val entities = threads.map { thread ->
            val count = thread.replyCount ?: 0
            BookmarkEntity(
                id = thread.idStr,
                fid = thread.fidStr,
                content = thread.content,
                userid = thread.userHash,
                now = thread.now,
                title = thread.title,
                name = thread.name,
                lastReadReplyCount = count,
                lastKnownReplyCount = count
            )
        }
        historyDao.insertBookmarks(entities)
    }

    override suspend fun markBookmarkRead(threadId: String, replyCount: Int) {
        historyDao.markBookmarkRead(threadId, replyCount)
    }

    override suspend fun refreshBookmarkReplyCounts(limit: Int) {
        val bookmarks = historyDao.getAllBookmarks().first().take(limit)
        for (bookmark in bookmarks) {
            try {
                val thread = fetchThread(bookmark.id, 1)
                val count = thread.replyCount ?: 0
                // Skip Room write when badge count unchanged
                if (count != bookmark.lastKnownReplyCount) {
                    // 定向元数据更新：不覆盖 lastReadReplyCount，也不复活刚删除的收藏
                    historyDao.updateBookmarkMetadata(
                        id = bookmark.id,
                        content = thread.content,
                        title = thread.title,
                        now = thread.now,
                        knownCount = count
                    )
                }
                delay(200)
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun pollSubscriptionUpdatesForNotification(limit: Int): List<BookmarkNewReply> {
        val updates = LinkedHashMap<String, BookmarkNewReply>()
        val snapshot = settingsDataStore.getNotifiedReplySnapshot().toMutableMap()

        // 1) Local bookmarks (app-side favorites)
        val bookmarks = historyDao.getAllBookmarks().first().take(limit)
        for (bookmark in bookmarks) {
            try {
                val thread = fetchThread(bookmark.id, 1)
                val count = thread.replyCount ?: 0
                historyDao.updateBookmarkMetadata(
                    id = bookmark.id,
                    content = thread.content,
                    title = thread.title,
                    now = thread.now,
                    knownCount = count
                )
                val baseline = snapshot[bookmark.id]
                    ?: bookmark.lastReadReplyCount.coerceAtLeast(bookmark.lastKnownReplyCount)
                if (count > baseline) {
                    val delta = (count - baseline).coerceAtLeast(1)
                    updates[bookmark.id] = BookmarkNewReply(
                        threadId = bookmark.id,
                        title = thread.title.effectiveTitle()
                            ?: bookmark.title.effectiveTitle()
                            ?: "No.${bookmark.id}",
                        newReplyDelta = delta,
                        totalReplies = count
                    )
                    snapshot[bookmark.id] = count
                } else if (!snapshot.containsKey(bookmark.id)) {
                    snapshot[bookmark.id] = count
                }
                delay(150)
            } catch (_: Exception) {
            }
        }

        // 2) Official X-island cloud subscriptions (feed?uuid=) — same source as 蓝岛订阅
        val folders = try {
            settingsDataStore.feedFoldersFlow.first()
        } catch (_: Exception) {
            emptyList()
        }
        for (folder in folders.take(5)) {
            try {
                val feedThreads = apiService.feed(folder.uuid, 1)
                cloudFeedDao.replaceAll(
                    folder.uuid,
                    feedThreads.map { com.mioo.dao.data.local.CloudFeedThreadEntity(folder.uuid, it.idStr) }
                )
                for (thread in feedThreads.take(limit)) {
                    val id = thread.idStr
                    val count = thread.replyCount ?: 0
                    val baseline = snapshot[id]
                    if (baseline == null) {
                        // First sighting: record baseline without notifying
                        snapshot[id] = count
                    } else if (count > baseline) {
                        val delta = (count - baseline).coerceAtLeast(1)
                        updates[id] = BookmarkNewReply(
                            threadId = id,
                            title = thread.title.effectiveTitle() ?: "No.$id",
                            newReplyDelta = delta,
                            totalReplies = count
                        )
                        snapshot[id] = count
                    }
                }
                delay(200)
            } catch (_: Exception) {
            }
        }

        settingsDataStore.saveNotifiedReplySnapshot(snapshot)
        return updates.values.toList()
    }

    override suspend fun saveThreadProgress(threadId: String, page: Int, firstVisibleIndex: Int) {
        progressDao.upsert(
            ThreadProgressEntity(
                threadId = threadId,
                page = page.coerceAtLeast(1),
                firstVisibleIndex = firstVisibleIndex.coerceAtLeast(0)
            )
        )
    }

    override suspend fun getThreadProgress(threadId: String): ThreadProgressEntity? {
        return progressDao.get(threadId)
    }

    override suspend fun clearThreadProgress(threadId: String) {
        progressDao.delete(threadId)
    }

    override suspend fun searchLocal(query: String): LocalSearchResult {
        val q = query.trim()
        if (q.isEmpty()) {
            return LocalSearchResult(query = q)
        }

        val digitsOnly = q.removePrefix("No.").removePrefix("no.").removePrefix(">>").trim()
        val directThreadId = digitsOnly.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

        val historyHits = historyDao.searchHistory(q).map { entity ->
            LocalSearchHit(
                threadId = entity.id,
                title = entity.title,
                snippet = entity.content,
                userId = entity.userid,
                source = LocalSearchSource.HISTORY,
                timestamp = entity.timestamp
            )
        }
        val bookmarkHits = historyDao.searchBookmarks(q).map { entity ->
            LocalSearchHit(
                threadId = entity.id,
                title = entity.title,
                snippet = entity.content,
                userId = entity.userid,
                source = LocalSearchSource.BOOKMARK,
                timestamp = entity.timestamp,
                newReplyCount = entity.newReplyCount
            )
        }

        val cacheHits = mutableListOf<LocalSearchHit>()
        try {
            val caches = cacheDao.searchThreadCache(q)
            for (cache in caches) {
                val thread = runCatching { threadAdapter.fromJson(cache.jsonResponse) }.getOrNull()
                    ?: continue
                cacheHits.add(
                    LocalSearchHit(
                        threadId = thread.idStr,
                        title = thread.title,
                        snippet = thread.content,
                        userId = thread.userHash,
                        source = LocalSearchSource.CACHE,
                        timestamp = cache.cachedAt
                    )
                )
            }
        } catch (_: Exception) {
        }

        // Deduplicate by threadId, prefer bookmark > history > cache
        val merged = LinkedHashMap<String, LocalSearchHit>()
        fun putPrefer(hit: LocalSearchHit) {
            val existing = merged[hit.threadId]
            if (existing == null || hit.source.priority < existing.source.priority) {
                merged[hit.threadId] = hit
            }
        }
        bookmarkHits.forEach(::putPrefer)
        historyHits.forEach(::putPrefer)
        cacheHits.forEach(::putPrefer)

        return LocalSearchResult(
            query = q,
            directThreadId = directThreadId,
            hits = merged.values.sortedByDescending { it.timestamp }
        )
    }

    override fun getFeed(uuid: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        try {
            val response = apiService.feed(uuid, page)
            // Cache membership so detail pages can show cloud-subscribed state.
            if (page == 1) {
                cloudFeedDao.replaceAll(
                    uuid,
                    response.map { com.mioo.dao.data.local.CloudFeedThreadEntity(uuid, it.idStr) }
                )
            } else {
                cloudFeedDao.insertAll(
                    response.map { com.mioo.dao.data.local.CloudFeedThreadEntity(uuid, it.idStr) }
                )
            }
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun addFeed(uuid: String, tid: String): Flow<XdResponse<String>> = flow {
        try {
            val response = apiService.addFeed(uuid, tid)
            val interpreted = interpretPlainFeedWrite(response.string())
            if (interpreted is XdResponse.Success) {
                cloudFeedDao.insert(com.mioo.dao.data.local.CloudFeedThreadEntity(uuid, tid))
            }
            emit(interpreted)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun delFeed(uuid: String, tid: String): Flow<XdResponse<String>> = flow {
        try {
            val response = apiService.delFeed(uuid, tid)
            val interpreted = interpretPlainFeedWrite(response.string())
            if (interpreted is XdResponse.Success) {
                cloudFeedDao.removeFeedThread(uuid, tid)
            }
            emit(interpreted)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun isInCloudFeed(tid: String): Flow<Boolean> = cloudFeedDao.isMember(tid)

    // --- 发言草稿箱 ---

    /** In-process mirror of the DataStore one-time flag. */
    private var draftsMigrated = false

    /**
     * One-time sweep of legacy DataStore drafts (per-thread reply map + global
     * new-thread draft) into the Room draft box. Runs before any draft read/write.
     */
    private suspend fun ensureDraftsMigrated() {
        if (draftsMigrated) return
        if (settingsDataStore.isDraftsMigrated()) {
            draftsMigrated = true
            return
        }
        val now = System.currentTimeMillis()
        settingsDataStore.getAllThreadDrafts().forEach { (tid, text) ->
            draftDao.upsert(
                com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_REPLY,
                    targetId = tid,
                    content = text,
                    updatedAt = now
                )
            )
        }
        val postDraft = try {
            settingsDataStore.getNewThreadDraft()
        } catch (_: Exception) {
            ""
        }
        if (postDraft.isNotBlank()) {
            draftDao.upsert(
                com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_POST,
                    targetId = "new_thread",
                    targetName = "草稿",
                    content = postDraft,
                    updatedAt = now
                )
            )
        }
        settingsDataStore.markDraftsMigrated()
        draftsMigrated = true
    }

    override fun getDrafts(): Flow<List<com.mioo.dao.data.local.DraftEntity>> = flow {
        ensureDraftsMigrated()
        emitAll(draftDao.getAll())
    }.flowOn(Dispatchers.IO)

    override suspend fun getReplyDraftContent(threadId: String): String {
        ensureDraftsMigrated()
        return try {
            draftDao.find(com.mioo.dao.data.local.DraftEntity.TYPE_REPLY, threadId)?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun saveReplyDraft(threadId: String, threadTitle: String?, content: String) {
        ensureDraftsMigrated()
        if (content.isBlank()) {
            draftDao.deleteFor(com.mioo.dao.data.local.DraftEntity.TYPE_REPLY, threadId)
        } else {
            draftDao.upsert(
                com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_REPLY,
                    targetId = threadId,
                    targetName = threadTitle ?: "",
                    content = content
                )
            )
        }
    }

    override suspend fun savePostDraft(boardId: String, boardName: String, name: String?, title: String?, content: String) {
        ensureDraftsMigrated()
        if (content.isBlank() && title.isNullOrBlank()) {
            draftDao.deleteFor(com.mioo.dao.data.local.DraftEntity.TYPE_POST, boardId)
        } else {
            draftDao.upsert(
                com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_POST,
                    targetId = boardId,
                    targetName = boardName,
                    name = name?.takeIf { it.isNotBlank() },
                    title = title?.takeIf { it.isNotBlank() },
                    content = content
                )
            )
        }
    }

    override suspend fun deleteDraft(id: Long) {
        draftDao.deleteById(id)
    }

    override suspend fun deletePostDrafts(boardId: String) {
        ensureDraftsMigrated()
        draftDao.deleteFor(com.mioo.dao.data.local.DraftEntity.TYPE_POST, boardId)
    }

    override fun removeCloudSubscriptions(tid: String): Flow<XdResponse<Int>> = flow {
        try {
            // 本地成员缓存只覆盖浏览过的页（通常第1页）；对排在深页的串，
            // 必须遍历全部已配置云端收藏夹 + 用户自己的订阅 uuid，否则会静默 no-op。
            val configured = try {
                settingsDataStore.feedFoldersFlow.first().map { it.uuid }
            } catch (_: Exception) {
                emptyList()
            }
            val ownUuid = try {
                // 归一化 JSON 饼干后再当 uuid 用
                com.mioo.dao.data.api.CookieInterceptor.extractHash(
                    settingsDataStore.userHashFlow.first()
                )
            } catch (_: Exception) {
                null
            }
            val uuids = (cloudFeedDao.feedUuidsFor(tid) + configured + listOfNotNull(ownUuid))
                .filter { it.isNotBlank() }
                .distinct()
            var removed = 0
            for (uuid in uuids) {
                try {
                    val response = apiService.delFeed(uuid, tid)
                    if (interpretPlainFeedWrite(response.string()) is XdResponse.Success) {
                        cloudFeedDao.removeFeedThread(uuid, tid)
                        removed++
                    }
                } catch (_: Exception) {
                }
            }
            emit(XdResponse.Success(removed))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getCloudFeedReplyCounts(uuid: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            try {
                cloudFeedDao.getThreadsForFeed(uuid)
                    .mapNotNull { entity ->
                        entity.replyCount.takeIf { it > 0 }?.let { entity.threadId to it }
                    }
                    .toMap()
            } catch (_: Exception) {
                emptyMap()
            }
        }

    override suspend fun refreshCloudFeedReplyCount(tid: String): Int? =
        withContext(Dispatchers.IO) {
            try {
                val count = fetchThread(tid, 1).replyCount?.takeIf { it > 0 } ?: return@withContext null
                cloudFeedDao.updateReplyCount(tid, count)
                count
            } catch (_: Exception) {
                null
            }
        }

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
                    val response = fetchThread(tid, 1)
                    val json = threadAdapter.toJson(response)
                    cacheDao.insertCache(CacheEntity("thread_$tid", 1, json))
                } catch (_: Exception) {
                }
                current++
                onProgress(current, total)
            }
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            emit(XdResponse.Error(message = e.localizedMessage ?: "Preloading failed", throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    /** Single in-flight smart-preload job — newer schedule cancels older. */
    @Volatile
    private var smartPreloadJob: Job? = null

    override fun smartPreloadThreads(threads: List<Thread>) {
        smartPreloadJob?.cancel()
        if (threads.isEmpty()) return
        smartPreloadJob = preloadScope.launch {
            try {
                val mode = settingsDataStore.smartPreloadModeFlow.first()
                if (mode == "DISABLED") return@launch

                // Without cookie, thread API returns error envelope — skip entirely
                if (!hasUserCookie()) return@launch

                if (mode == "WIFI_ONLY") {
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val activeNetwork = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    if (!isWifi) return@launch
                }

                // Hard cap: settings preloadCount can be large; more than ~6 thrash mid-scroll.
                val preloadCount = settingsDataStore.preloadCountFlow.first().coerceIn(1, 6)
                val threadsToPreload = threads.take(preloadCount)

                for (thread in threadsToPreload) {
                    ensureActive()
                    val tid = thread.idStr
                    if (tid.isBlank()) continue
                    val cached = cacheDao.getCache("thread_$tid", 1)
                    val isRecentlyCached = cached != null &&
                        (System.currentTimeMillis() - cached.cachedAt < 15 * 60 * 1000)
                    if (isRecentlyCached) continue

                    try {
                        val response = fetchThread(tid, 1)
                        val json = threadAdapter.toJson(response)
                        cacheDao.insertCache(CacheEntity("thread_$tid", 1, json))
                        // Longer gap so list fling / image decode keep the network slot
                        delay(750)
                    } catch (e: Exception) {
                        // Auth / deleted: stop the whole preload wave (no point hammering)
                        if (e is ApiBusinessException) return@launch
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (com.mioo.dao.BuildConfig.DEBUG) {
                    android.util.Log.e("ThreadRepo", "Error preloading threads", e)
                }
            }
        }
    }

    /**
     * 离线下载整串：逐页 JSON 落 api_cache（SWR 断网可直接渲染），
     * 配图落 filesDir/offline_images（Coil 渲染时本地优先），
     * 最后写 offline_threads 标记。重复调用即「更新离线」——
     * 已有页/图按 key 覆盖或跳过，只补新增。
     */
    override fun downloadFullThread(tid: String): Flow<XdResponse<Unit>> = flow {
        try {
            val page1 = fetchThread(tid, 1)
            saveToHistory(page1)
            addBookmark(page1)

            val json1 = threadAdapter.toJson(page1)
            cacheDao.insertCache(CacheEntity("thread_$tid", 1, json1))

            val totalReplies = page1.replyCount ?: 0
            val repliesPage1Size = page1.replies?.size ?: 0
            val allPosts = ArrayList<Reply>()
            allPosts.addAll(page1.replies.orEmpty())

            if (totalReplies > repliesPage1Size && repliesPage1Size > 0) {
                val pageSize = repliesPage1Size
                val totalPages = (totalReplies + pageSize - 1) / pageSize

                for (page in 2..totalPages) {
                    try {
                        val threadPage = fetchThread(tid, page)
                        val jsonPage = threadAdapter.toJson(threadPage)
                        cacheDao.insertCache(CacheEntity("thread_$tid", page, jsonPage))
                        allPosts.addAll(threadPage.replies.orEmpty())
                        delay(300)
                    } catch (_: Exception) {
                    }
                }
            }

            // 离线图片：主楼优先的封面 + 各楼配图（缩略图优先，缺缩略图回落原图）
            val imageUrls = LinkedHashSet<String>()
            page1.imageUrl?.let(imageUrls::add)
            for (post in allPosts) post.imageUrl?.let(imageUrls::add)

            val imageFiles = LinkedHashSet<String>()
            var imageBytes = 0L
            val offlineRoot = com.mioo.dao.utils.OfflineImageStore.dir(context)
            var processed = 0
            for (url in imageUrls) {
                currentCoroutineContext().ensureActive()
                if (processed >= com.mioo.dao.utils.OfflineImageStore.MAX_IMAGES_PER_THREAD) break
                processed++
                val saved = com.mioo.dao.utils.OfflineImageStore.ensureDownloaded(offlineRoot, url)
                if (saved != null) {
                    imageFiles.add(
                        com.mioo.dao.utils.OfflineImageStore.sanitizeFilename(
                            com.mioo.dao.utils.OfflineImageStore.islandImageName(url) ?: continue
                        )
                    )
                    imageBytes += saved.second
                }
                delay(60)
            }

            offlineThreadDao.upsert(
                com.mioo.dao.data.local.OfflineThreadEntity(
                    threadId = tid,
                    title = page1.title?.takeIf { it.isNotBlank() && it.trim() != "无标题" },
                    contentSummary = plainSummary(page1.content),
                    poUserHash = page1.userHash,
                    fid = page1.fidStr,
                    forumName = lookupForumName(page1.fidStr),
                    replyCount = totalReplies,
                    imageFilesJson = stringListAdapter.toJson(imageFiles.toList()),
                    imageBytes = imageBytes,
                    coverFile = imageFiles.firstOrNull(),
                    downloadedAt = System.currentTimeMillis()
                )
            )
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(XdResponse.Error(message = mapThreadException(e), throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    // --- 已离线串 ---

    override fun getOfflineThreadSummaries(): Flow<List<com.mioo.dao.data.local.OfflineThreadSummary>> =
        offlineThreadDao.observeSummaries()

    override fun isThreadDownloaded(threadId: String): Flow<Boolean> =
        offlineThreadDao.observeDownloaded(threadId)

    override suspend fun deleteOfflineThread(threadId: String) = withContext(Dispatchers.IO) {
        val entity = offlineThreadDao.get(threadId)
        offlineThreadDao.delete(threadId)
        cacheDao.deleteCacheByKey("thread_$threadId")
        historyDao.deleteBookmarkById(threadId)
        if (entity != null) {
            val own = runCatching { stringListAdapter.fromJson(entity.imageFilesJson) }
                .getOrNull().orEmpty().toSet()
            val stillReferenced = offlineThreadDao.getAllOnce()
                .flatMap { other ->
                    runCatching { stringListAdapter.fromJson(other.imageFilesJson) }
                        .getOrNull().orEmpty()
                }
                .toSet()
            com.mioo.dao.utils.OfflineImageStore.deleteFiles(
                com.mioo.dao.utils.OfflineImageStore.dir(context),
                own - stillReferenced
            )
        }
    }

    override suspend fun clearAllOfflineThreads() = withContext(Dispatchers.IO) {
        offlineThreadDao.clear()
        cacheDao.deleteAllThreadCache()
        historyDao.clearAllBookmarks()
        com.mioo.dao.utils.OfflineImageStore.deleteAll(
            com.mioo.dao.utils.OfflineImageStore.dir(context)
        )
    }

    /** 收藏 ⇒ 离线巡检的失败退避：同串失败两次后本轮进程内跳过（已删除/风控串）。 */
    private val offlineSyncFailures = HashMap<String, Int>()

    @Volatile
    private var offlineSyncRunning = false

    override fun startBookmarkOfflineSync() {
        if (offlineSyncRunning) return
        offlineSyncRunning = true
        preloadScope.launch {
            try {
                while (true) {
                    val bookmarks = historyDao.getAllBookmarks().first()
                    val offlineIds = offlineThreadDao.getAllOnce()
                        .mapTo(HashSet()) { it.threadId }
                    val pending = bookmarks.filter { it.id !in offlineIds }
                    if (pending.isEmpty()) {
                        // 全部离线后低频巡检，兜住之后新星的收藏
                        delay(15_000)
                        continue
                    }
                    var attempts = 0
                    for (bookmark in pending) {
                        if ((offlineSyncFailures[bookmark.id] ?: 0) >= 2) continue
                        val response = try {
                            downloadFullThread(bookmark.id).first()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            XdResponse.Error(message = "network")
                        }
                        if (response is XdResponse.Error) {
                            offlineSyncFailures[bookmark.id] =
                                (offlineSyncFailures[bookmark.id] ?: 0) + 1
                        }
                        delay(2_000)
                        attempts++
                    }
                    // 本轮一个都没试（全部处于失败退避）→ 长睡，别空转扫库
                    delay(if (attempts == 0) 60_000 else 3_000)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (com.mioo.dao.BuildConfig.DEBUG) {
                    android.util.Log.e("ThreadRepo", "bookmark offline sync stopped", e)
                }
            } finally {
                offlineSyncRunning = false
            }
        }
    }

    /** fid → 板块名。列表在线抓一次后内存缓存；离线/失败退化为「板块 <fid>」。 */
    private val forumNameCache = LinkedHashMap<String, String>()

    private suspend fun lookupForumName(fid: String?): String {
        if (fid.isNullOrBlank()) return "板块"
        forumNameCache[fid]?.let { return it }
        runCatching {
            forumRepository.getForumList().first().let { response ->
                if (response is XdResponse.Success) {
                    response.data.forEach { group ->
                        group.forums.forEach { forum -> forumNameCache[forum.id] = forum.name }
                    }
                }
            }
        }
        return forumNameCache[fid] ?: "板块 $fid"
    }

    /** 主楼正文纯文本摘要（去标签/实体、压空白、截断），离线列表与角标用。 */
    private fun plainSummary(html: String, maxLen: Int = 300): String {
        val plain = html
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(HTML_OR_NEWLINE, " ")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (plain.length > maxLen) plain.take(maxLen) + "…" else plain
    }

    override fun checkLatestRelease(): Flow<XdResponse<GithubRelease>> = flow {
        var lastError: Exception? = null
        for (url in RELEASE_MANIFEST_URLS) {
            try {
                val release = githubApiService.getLatestRelease(url)
                if (release.tagName.isNotBlank()) {
                    emit(XdResponse.Success(release))
                    return@flow
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
            }
        }
        emit(
            XdResponse.Error(
                message = lastError?.localizedMessage ?: "检查更新失败",
                throwable = lastError
            )
        )
    }.flowOn(Dispatchers.IO)
}
