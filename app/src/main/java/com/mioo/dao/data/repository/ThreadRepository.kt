package com.mioo.dao.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mioo.dao.data.api.XdApiService
import com.mioo.dao.data.api.CookieInterceptor

import com.mioo.dao.data.api.extractQuotedJsonString
import com.mioo.dao.data.api.isXdBusinessPlainString
import com.mioo.dao.data.model.GithubRelease
import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CacheDao
import com.mioo.dao.data.local.CacheEntity
import com.mioo.dao.data.local.HistoryDao
import com.mioo.dao.data.local.HistoryEntity
import com.mioo.dao.data.local.BrowseHistoryRecord
import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.local.PostActionHistoryDao
import com.mioo.dao.data.local.PostActionHistoryEntity
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
    suspend fun shareForumName(fid: String?): String
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
        cookie: String? = null,
        forumName: String? = null,
        recordHistory: Boolean = true
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
    fun observeBrowsingHistory(query: String): Flow<List<BrowseHistoryRecord>>
    fun observePostActions(type: HistoryType, query: String): Flow<List<PostActionHistoryEntity>>
    suspend fun deleteBrowsingHistory(id: String)
    suspend fun deletePostAction(id: String)
    suspend fun clearPostActions(type: HistoryType)
    suspend fun resolvePostAction(id: String): String?
    suspend fun resolvePendingPostActions()
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
    suspend fun saveThreadProgress(threadId: String, page: Int, firstVisibleIndex: Int, scrollOffset: Int = 0, anchorPostId: String? = null)
    suspend fun getThreadProgress(threadId: String): ThreadProgressEntity?
    suspend fun clearThreadProgress(threadId: String)

    /** Local-only search (no server search API). */
    suspend fun searchLocal(query: String): LocalSearchResult

    // Remote Feed operations
    fun getFeed(uuid: String, page: Int): Flow<XdResponse<List<Thread>>>
    fun addFeed(uuid: String, tid: String, title: String? = null, replyCount: Int = 0): Flow<XdResponse<String>>
    fun observeCloudFeedIndex(uuids: List<String>): Flow<List<com.mioo.dao.data.local.CloudFeedThreadEntity>>
    fun observeFullyIndexedCloudFolders(): Flow<List<String>>
    suspend fun syncCloudFeedIndex(uuid: String)
    fun delFeed(uuid: String, tid: String): Flow<XdResponse<String>>

    /** Cloud-feed membership cache: is this thread in any known 云端收藏夹? */
    fun isInCloudFeed(tid: String): Flow<Boolean>

    /** 该串已缓存所属的云端收藏夹 uuid 集合（收藏菜单按夹显示"已收藏进该夹"态）。 */
    fun observeCloudFolderUuids(tid: String): Flow<Set<String>>

    /** Remove [tid] from every cloud feed it is cached under. Emits removed count. */
    fun removeCloudSubscriptions(tid: String): Flow<XdResponse<Int>>

    /** 该夹内已缓存的回复数（threadId → count，仅含 >0 的已知值）。 */
    suspend fun getCloudFeedReplyCounts(uuid: String): Map<String, Int>

    /**
     * 补抓一个云端订阅串的回复总数并写入缓存。
     * feed 接口不返回回复数，只能按串抓第 1 页；失败返回 null。
     */

    // 发言草稿箱 (draft box): reply drafts keyed by thread, post drafts keyed by board.
    fun getDrafts(): Flow<List<com.mioo.dao.data.local.DraftEntity>>
    suspend fun getReplyDraftContent(threadId: String): String
    suspend fun getReplyDraft(threadId: String, draftId: Long?): com.mioo.dao.data.local.DraftEntity?
    suspend fun saveReplyDraft(threadId: String, threadTitle: String?, content: String, title: String? = null, name: String? = null, draftId: Long? = null): Long?
    suspend fun savePostDraft(boardId: String, boardName: String, name: String?, title: String?, content: String, draftId: Long = 0): Long
    suspend fun deleteDraft(id: Long)
    /** 删除某板块的全部发串草稿（发送成功后清理，防止草稿箱残留已发内容）。 */
    suspend fun deletePostDrafts(boardId: String)

    // Cache management & preloading
    fun clearCache(): Flow<XdResponse<Unit>>
    fun getCacheSize(): Flow<String>
    fun preloadBookmarks(onProgress: (Int, Int) -> Unit): Flow<XdResponse<Unit>>
    fun smartPreloadThreads(threads: List<Thread>)
    fun downloadFullThread(tid: String, conservative: Boolean = false): Flow<XdResponse<Unit>>
    fun checkLatestRelease(): Flow<XdResponse<GithubRelease>>

    // 已离线串 (offline threads)
    /** 管理页列表：按下载时间倒序，体积含离线图片。 */
    fun getOfflineThreadSummaries(): Flow<List<com.mioo.dao.data.local.OfflineThreadSummary>>

    /** 串详情页三点菜单的「已离线」状态。 */
    fun isThreadDownloaded(threadId: String): Flow<Boolean>

    /** 删除单个离线串及其缓存，保留本地收藏。 */
    suspend fun deleteOfflineThread(threadId: String)

    /** 清空全部离线串及其缓存，保留本地收藏和其他串的普通缓存。 */
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
    private val updateRepository: UpdateRepository,
    private val historyDao: HistoryDao,
    private val postActionHistoryDao: PostActionHistoryDao,
    private val cacheDao: CacheDao,
    private val progressDao: ProgressDao,
    private val cloudFeedDao: com.mioo.dao.data.local.CloudFeedDao,
    private val draftStorage: DraftStorage,
    private val offlineThreadDao: com.mioo.dao.data.local.OfflineThreadDao,
    private val forumRepository: ForumRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
    private val moshi: com.squareup.moshi.Moshi
) : ThreadRepository {

    companion object {
        private val HTML_OR_NEWLINE = Regex("<.*?>|\\n")
        private val BR_TAG_REGEX = Regex("(?i)<br\\s*/?>")
        private val MULTI_SPACE_REGEX = Regex("\\s+")
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
    private val cacheSearchAdapter = moshi.adapter(CachedThreadSearchPreview::class.java)
    private val replyAdapter = moshi.adapter(Reply::class.java)
    private val stringListAdapter =
        moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refPostCache = RefPostMemoryCache<Reply>(scope = preloadScope, flowContext = Dispatchers.IO)
    init {
        preloadScope.launch {
            try {
                // Defer periodic expiry/trim past cold-start first paint & initial scroll.
                delay(20_000L)
                while (true) {
                    cacheDao.deleteExpiredNonOfflineCache(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
                    cacheDao.trimNonOfflineBytes(200L * 1024 * 1024)
                    delay(30L * 60 * 1000)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { android.util.Log.w("ThreadRepository", "Cache expiry cleanup failed", e) }
        }
    }

    private fun extractThreadErrorEnvelope(trimmed: String): String? {
        return try {
            val o = org.json.JSONObject(trimmed)
            val hasId = o.has("id")
            val successFalse = o.has("success") && !o.optBoolean("success", true)
            val errorOnly = o.has("error") && !hasId
            if (successFalse || errorOnly) {
                val msg = sequenceOf("error", "msg", "message")
                    .map { o.optString(it) }
                    .firstOrNull { it.isNotBlank() }
                msg ?: "访问被拒绝，请先在设置中添加饼干"
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse raw `thread`/`po` body. Rejects `{success:false,error:...}` without parsing
     * large thread payloads twice via DOM JSONObject + Moshi.
     */
    private fun parseThreadBody(raw: String): Thread {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw ApiBusinessException("空响应")
        extractQuotedJsonString(trimmed)?.let { msg ->
            throw ApiBusinessException(msg.ifBlank { "该串不存在或已被删除" })
        }
        if (trimmed.startsWith("{") && trimmed.length <= 1024 &&
            (trimmed.contains("\"success\":false") || trimmed.contains("\"success\": false"))
        ) {
            extractThreadErrorEnvelope(trimmed)?.let { throw ApiBusinessException(it) }
        }
        val parsed = try {
            threadAdapter.fromJson(trimmed)
        } catch (e: com.squareup.moshi.JsonDataException) {
            if (trimmed.startsWith("{")) {
                extractThreadErrorEnvelope(trimmed)?.let { throw ApiBusinessException(it) }
            }
            throw e
        } catch (e: java.io.IOException) {
            if (trimmed.startsWith("{")) {
                extractThreadErrorEnvelope(trimmed)?.let { throw ApiBusinessException(it) }
            }
            throw e
        }
        return parsed ?: throw ApiBusinessException("解析串失败")
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

    private suspend fun fetchThreadWithRaw(tid: String, page: Int): FetchedThread {
        val raw = apiService.thread(tid, page).string()
        return FetchedThread(parseThreadBody(raw), raw)
    }

    private suspend fun fetchThread(tid: String, page: Int): Thread =
        fetchThreadWithRaw(tid, page).thread

    private suspend fun fetchPoWithRaw(tid: String, page: Int): FetchedThread {
        val raw = apiService.po(tid, page).string()
        return FetchedThread(parseThreadBody(raw), raw)
    }

    private suspend fun fetchPo(tid: String, page: Int): Thread =
        fetchPoWithRaw(tid, page).thread

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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
        var cachedThread: Thread? = null
        try {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                // Never surface a cached error envelope as a thread
                val decoded = runCatching { parseThreadBody(cached.jsonResponse) }.getOrNull()
                if (decoded != null) {
                    cachedThread = decoded
                    emit(XdResponse.Success(decoded))
                    emittedCache = true
                }
            }
            val fetched = fetchThreadWithRaw(tid, page)
            if (!(emittedCache && cachedThread == fetched.thread)) {
                cacheDao.insertCache(CacheEntity(cacheKey, page, fetched.rawJson))
                emit(XdResponse.Success(fetched.thread))
            }
            // Content must reach the collector before history lookup or persistence.
            if (page <= 1) {
                preloadScope.launch {
                    try {
                        saveToHistory(fetched.thread)
                        cloudFeedDao.updateThreadMetadata(tid, fetched.thread.title.effectiveTitle(), fetched.thread.replyCount ?: 0)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("ThreadRepository", "Failed to save browsing history", e)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!emittedCache) {
                emit(XdResponse.Error(message = mapThreadException(e), throwable = e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getPoDetail(tid: String, page: Int): Flow<XdResponse<Thread>> = flow {
        val cacheKey = "po_$tid"
        var emittedCache = false
        var cachedThread: Thread? = null
        try {
            val cached = cacheDao.getCache(cacheKey, page)
            if (cached != null) {
                val decoded = runCatching { parseThreadBody(cached.jsonResponse) }.getOrNull()
                if (decoded != null) {
                    cachedThread = decoded
                    emit(XdResponse.Success(decoded))
                    emittedCache = true
                }
            }
            val fetched = fetchPoWithRaw(tid, page)
            if (!(emittedCache && cachedThread == fetched.thread)) {
                cacheDao.insertCache(CacheEntity(cacheKey, page, fetched.rawJson))
                emit(XdResponse.Success(fetched.thread))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!emittedCache) {
                emit(XdResponse.Error(message = mapThreadException(e), throwable = e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getRefPost(id: String): Flow<XdResponse<Reply>> =
        refPostCache.getOrFetch(id) { fetchRefPostNetwork(it) }

    private suspend fun fetchRefPostNetwork(id: String): XdResponse<Reply> {
        return try {
            val responseString = apiService.ref(id).string()
            extractQuotedJsonString(responseString)?.let { msg ->
                return XdResponse.Error(message = msg.ifBlank { "该引用不存在或已被删除" })
            }
            try {
                val reply = replyAdapter.fromJson(responseString)
                if (reply != null) {
                    XdResponse.Success(reply)
                } else {
                    XdResponse.Error(message = "引用的串解析失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Not a valid JSON reply, likely an HTML error string from the server
                val cleanError = responseString
                    .replace(HTML_OR_NEWLINE, "")
                    .replace("\"", "")
                    .trim()
                val errorMsg = cleanError.ifEmpty { "该引用不存在或已被删除" }
                XdResponse.Error(message = errorMsg)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            XdResponse.Error(message = e.localizedMessage ?: "Network error", throwable = e)
        }
    }

    override fun doPostThread(
        fid: String,
        title: String?,
        name: String?,
        email: String?,
        content: String,
        imageFile: File?,
        cookie: String?,
        forumName: String?,
        recordHistory: Boolean
    ): Flow<XdResponse<String>> = flow {
        try {
            val usedCookie = selectedWritingCookie(cookie)
            val previousMaxId = if (recordHistory) runCatching { apiService.showf(fid, 1).maxOfOrNull { it.id } ?: 0L }.getOrNull() else null
            val fidBody = fid.toRequestBody("text/plain".toMediaTypeOrNull())
            val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
            val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull())
            val emailBody = email?.toRequestBody("text/plain".toMediaTypeOrNull())
            val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())

            val imagePart = imageFile?.asImagePart()

            val response = apiService.doPostThread(CookieInterceptor.extractHash(usedCookie), fidBody, titleBody, nameBody, emailBody, contentBody, imagePart)
            val raw = response.string()
            val result = interpretForumWriteResponse(raw)
            if (result is XdResponse.Success && result.data != "已提交") {
                if (recordHistory) recordSuccessfulAction(PostActionHistoryEntity(
                    type = HistoryType.NEW_THREAD.name, threadId = createdThreadId(raw), forumId = fid, forumName = forumName.orEmpty(),
                    cookieHash = historyCookieIdentity(usedCookie), title = title, authorName = name,
                    content = content, minimumThreadId = previousMaxId
                ), imageFile)
                imageFile?.delete()
            }
            emit(result)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(XdResponse.Error(message = forumWriteNetworkError(e), throwable = e))
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
            val usedCookie = selectedWritingCookie(cookie)
            val response = if (imageFile != null) {
                // Use Multipart when there's an image
                val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
                val nameBody = name?.toRequestBody("text/plain".toMediaTypeOrNull())
                val emailBody = email?.toRequestBody("text/plain".toMediaTypeOrNull())
                val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
                val parentBody = parent.toRequestBody("text/plain".toMediaTypeOrNull())
                val imagePart = imageFile.asImagePart()
                apiService.doReplyThread(CookieInterceptor.extractHash(usedCookie), null, titleBody, nameBody, emailBody, contentBody, parentBody, imagePart)
            } else {
                // Use FormUrlEncoded for text-only replies (more reliable)
                apiService.doReplyThreadText(
                    cookieOverride = CookieInterceptor.extractHash(usedCookie),
                    resto = parent,
                    content = content,
                    name = name,
                    title = title,
                    email = email
                )
            }
            val result = interpretForumWriteResponse(response.string())
            if (result is XdResponse.Success && result.data != "已提交") {
                recordSuccessfulAction(PostActionHistoryEntity(
                    type = HistoryType.REPLY.name, threadId = parent,
                    cookieHash = historyCookieIdentity(usedCookie), title = title, authorName = name, content = content
                ), imageFile)
                imageFile?.delete()
            }
            emit(result)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (com.mioo.dao.BuildConfig.DEBUG) {
                android.util.Log.e("ThreadRepo", "Reply failed", e)
            }
            emit(XdResponse.Error(message = forumWriteNetworkError(e), throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    // --- History & Bookmarks ---

    private val actionResolveMutex = Mutex()

    private suspend fun selectedWritingCookie(override: String?): String? {
        if (!override.isNullOrBlank()) return override
        val cookies = settingsDataStore.cookiesListFlow.first()
        return cookies.getOrNull(settingsDataStore.selectedCookieIndexFlow.first())
            ?: settingsDataStore.userHashFlow.first()?.takeIf { it.isNotBlank() }
    }

    private suspend fun recordSuccessfulAction(record: PostActionHistoryEntity, image: File?) = withContext(NonCancellable) {
        // The server has already accepted the post. Local bookkeeping must never
        // turn that success into a send error (and invite a duplicate submission).
        try {
            val parent = record.threadId?.let { historyDao.getHistoryById(it) }
            val cachedParent = if (parent == null && record.type == HistoryType.REPLY.name) record.threadId?.let { id ->
                cacheDao.getLatestPage("thread_$id")?.jsonResponse?.let { runCatching { parseThreadBody(it) }.getOrNull() }
            } else null
            val fid = record.forumId ?: parent?.fid ?: cachedParent?.fidStr
            val savedImage = image?.let { source -> runCatching {
                actionHistoryImageFile(context, record.id, source.extension).also { source.copyTo(it, overwrite = true) }.absolutePath
            }.getOrNull() }
            val saved = record.copy(forumId = fid,
                forumName = record.forumName.takeIf { it.isNotBlank() }
                    ?: parent?.forumName?.takeIf { it.isNotBlank() } ?: lookupForumName(fid),
                threadTitle = parent?.title ?: cachedParent?.title,
                threadSummary = parent?.content ?: cachedParent?.content.orEmpty(), imagePath = savedImage)
            postActionHistoryDao.upsert(saved)
            if (saved.type == HistoryType.NEW_THREAD.name && saved.threadId == null) {
                preloadScope.launch { resolvePostAction(saved.id) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ActionHistory", "Unable to store successful post", e)
        }
    }

    override fun observeBrowsingHistory(query: String): Flow<List<BrowseHistoryRecord>> = flow {
        historyDao.missingForumIds().forEach { fid -> historyDao.fillForumName(fid, lookupForumName(fid)) }
        emitAll(historyDao.observeBrowsing(query))
    }.flowOn(Dispatchers.IO)

    override fun observePostActions(type: HistoryType, query: String) = postActionHistoryDao.observe(type.name, query)

    override suspend fun deleteBrowsingHistory(id: String) = historyDao.deleteHistoryById(id)

    override suspend fun deletePostAction(id: String) {
        val record = postActionHistoryDao.get(id)
        postActionHistoryDao.delete(id)
        deleteActionHistoryImage(context, record?.imagePath)
    }

    override suspend fun clearPostActions(type: HistoryType) {
        if (type == HistoryType.BROWSE) return
        val records = postActionHistoryDao.observe(type.name).first()
        postActionHistoryDao.clear(type.name)
        records.forEach { deleteActionHistoryImage(context, it.imagePath) }
    }

    override suspend fun resolvePostAction(id: String): String? = actionResolveMutex.withLock {
        var record = postActionHistoryDao.get(id) ?: return@withLock null
        record.threadId?.let { return@withLock it }
        if (record.type != HistoryType.NEW_THREAD.name) return@withLock null
        val fid = record.forumId ?: return@withLock null
        repeat(3) { attempt ->
            if (attempt > 0) delay(1200L * attempt)
            record = postActionHistoryDao.get(id) ?: return@withLock null
            val candidates = try { apiService.showf(fid, 1) } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
            val matched = matchCreatedThread(record, candidates)
            if (matched != null) {
                postActionHistoryDao.resolve(id, matched.idStr, matched.userHash, matched.imageUrl)
                return@withLock matched.idStr
            }
        }
        null
    }

    override suspend fun resolvePendingPostActions() {
        postActionHistoryDao.pendingThreads().forEach { resolvePostAction(it.id) }
    }

    override fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    override suspend fun saveToHistory(thread: Thread) {
        val existing = historyDao.getHistoryById(thread.idStr)
        if (existing != null) {
            // Keep the actual visit time even when the thread content is unchanged.
            val contentSame =
                existing.content == thread.content &&
                    existing.title == thread.title &&
                    existing.userid == thread.userHash && existing.forumName.isNotBlank() &&
                    existing.forumName != "板块 ${thread.fidStr}"
            if (contentSame) {
                historyDao.touchHistory(thread.idStr)
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
                name = thread.name,
                forumName = lookupForumName(thread.fidStr)
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
        require(limit >= 0)
        val bookmarks = historyDao.getRecentBookmarks(limit)
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun pollSubscriptionUpdatesForNotification(limit: Int): List<BookmarkNewReply> {
        val updates = LinkedHashMap<String, BookmarkNewReply>()
        val snapshot = settingsDataStore.getNotifiedReplySnapshot().toMutableMap()

        // 1) Local bookmarks (app-side favorites)
        require(limit >= 0)
        val bookmarks = historyDao.getRecentBookmarks(limit)
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // 2) Official X-island cloud subscriptions (feed?uuid=) — same source as 蓝岛订阅
        val folders = try {
            settingsDataStore.feedFoldersFlow.first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        for (folder in folders.take(5)) {
            try {
                val feedThreads = fetchIndexedFeedPage(folder.uuid, 1).threads
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        settingsDataStore.saveNotifiedReplySnapshot(snapshot)
        return updates.values.toList()
    }

    override suspend fun saveThreadProgress(threadId: String, page: Int, firstVisibleIndex: Int, scrollOffset: Int, anchorPostId: String?) {
        progressDao.upsert(
            ThreadProgressEntity(
                threadId = threadId,
                page = page.coerceAtLeast(1),
                firstVisibleIndex = firstVisibleIndex.coerceAtLeast(0),
                scrollOffset = scrollOffset.coerceAtLeast(0),
                anchorPostId = anchorPostId
            )
        )
    }

    override suspend fun getThreadProgress(threadId: String): ThreadProgressEntity? {
        return progressDao.get(threadId)
    }

    override suspend fun clearThreadProgress(threadId: String) {
        progressDao.delete(threadId)
    }

    override suspend fun searchLocal(query: String): LocalSearchResult = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) {
            return@withContext LocalSearchResult(query = q)
        }

        val digitsOnly = q.removePrefix("No.").removePrefix("no.").removePrefix(">>").trim()
        val directThreadId = digitsOnly.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

        val (historyEntities, bookmarkEntities, caches) = coroutineScope {
            val historyDeferred = async { historyDao.searchHistory(q) }
            val bookmarkDeferred = async { historyDao.searchBookmarks(q) }
            val cacheDeferred = async {
                try {
                    cacheDao.searchThreadCache(q)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
            Triple(historyDeferred.await(), bookmarkDeferred.await(), cacheDeferred.await())
        }

        val historyHits = historyEntities.map { entity ->
            LocalSearchHit(
                threadId = entity.id,
                title = entity.title,
                snippet = entity.content,
                userId = entity.userid,
                source = LocalSearchSource.HISTORY,
                timestamp = entity.timestamp
            )
        }
        val bookmarkHits = bookmarkEntities.map { entity ->
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
        val cacheHits = caches.mapNotNull { cache ->
            ensureActive()
            val thread = try {
                cacheSearchAdapter.fromJson(cache.jsonResponse)
            } catch (_: java.io.IOException) {
                null
            } catch (_: com.squareup.moshi.JsonDataException) {
                null
            } ?: return@mapNotNull null
            LocalSearchHit(
                threadId = thread.id.toString(),
                title = thread.title,
                snippet = thread.content,
                userId = thread.userHash,
                source = LocalSearchSource.CACHE,
                timestamp = cache.cachedAt
            )
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

        ensureActive()
        LocalSearchResult(
            query = q,
            directThreadId = directThreadId,
            hits = merged.values.sortedByDescending { it.timestamp }
        )
    }

    private val cloudFeedPageLock = Mutex()
    private val cloudFeedMutations = mutableMapOf<String, Long>()

    private data class IndexedFeedPage(val threads: List<Thread>, val inserted: Int?)

    private fun List<Thread>.toCloudIndexEntries(uuid: String, page: Int) =
        distinctBy { it.id }.mapIndexed { index, thread ->
            com.mioo.dao.data.local.CloudFeedThreadEntity(
                feedUuid = uuid, threadId = thread.idStr, title = thread.title.effectiveTitle(),
                position = (page - 1) * 1000 + index
            )
        }

    private suspend fun fetchIndexedFeedPage(uuid: String, page: Int): IndexedFeedPage {
        val generation = cloudFeedPageLock.withLock { cloudFeedMutations[uuid] }
        val threads = apiService.feed(uuid, page)
        val inserted = cloudFeedPageLock.withLock {
            // A slow background fetch must not block a save or revive a just-removed item.
            if (generation == cloudFeedMutations[uuid]) {
                cloudFeedDao.indexPage(uuid, threads.toCloudIndexEntries(uuid, page))
            } else null
        }
        return IndexedFeedPage(threads, inserted)
    }

    private fun recordCloudFeedMutation(uuid: String) {
        cloudFeedMutations[uuid] = (cloudFeedMutations[uuid] ?: 0) + 1
    }

    override fun observeCloudFeedIndex(uuids: List<String>) =
        cloudFeedDao.observeIndex(uuids).distinctUntilChanged()

    override fun observeFullyIndexedCloudFolders() = cloudFeedDao.observeFullyIndexedFolders().distinctUntilChanged()

    override suspend fun syncCloudFeedIndex(uuid: String) = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val previous = cloudFeedDao.syncState(uuid)
        if (previous != null && startedAt - previous.lastCheckedAt in 0 until 50L * 60 * 1000) {
            return@withContext
        }
        val fullScan = previous == null || startedAt - previous.lastFullScanAt !in 0 until 24L * 60 * 60 * 1000
        val generation = cloudFeedPageLock.withLock { cloudFeedMutations[uuid] }
        val seen = HashSet<String>()
        val pending = ArrayList<com.mioo.dao.data.local.CloudFeedThreadEntity>()
        var mayRemoveMissing = true
        var page = 1
        while (true) {
            if (settingsDataStore.dataSaverEnabledFlow.first() ||
                settingsDataStore.feedFoldersFlow.first().none { it.uuid == uuid }) return@withContext

            val (threads, inserted) = if (fullScan) {
                val threads = apiService.feed(uuid, page)
                pending.addAll(threads.toCloudIndexEntries(uuid, page))
                // Group background writes so Room does not reload every folder
                // once per page; foreground saves and first-page loads still commit immediately.
                if (page % 5 == 0 || threads.isEmpty()) {
                    cloudFeedPageLock.withLock {
                        if (generation != cloudFeedMutations[uuid]) return@withContext
                        cloudFeedDao.indexPage(uuid, pending.distinctBy { it.threadId })
                        pending.clear()
                    }
                }
                IndexedFeedPage(threads, null)
            } else fetchIndexedFeedPage(uuid, page)
            if (threads.isEmpty()) {
                val state = com.mioo.dao.data.local.CloudFeedSyncEntity(
                    uuid, startedAt, if (fullScan && mayRemoveMissing) startedAt else previous?.lastFullScanAt ?: 0
                )
                if (fullScan && mayRemoveMissing) {
                    cloudFeedPageLock.withLock {
                        if (generation == cloudFeedMutations[uuid]) {
                            cloudFeedDao.finishFullScan(state, seen, startedAt)
                        }
                    }
                } else {
                    cloudFeedDao.saveSyncState(state)
                }
                break
            }
            val ids = threads.map { it.idStr }.distinct()
            val unseen = ids.count { seen.add(it) }
            // An overlapping snapshot cannot safely remove missing rows. A wholly
            // repeated page also means the server may have ignored the page number.
            if (unseen < ids.size) mayRemoveMissing = false
            if (unseen == 0) throw IllegalStateException("云端收藏分页重复")
            if (!fullScan && page >= 2 && inserted == 0) {
                cloudFeedDao.saveSyncState(requireNotNull(previous).copy(lastCheckedAt = startedAt))
                break
            }
            page++
            delay(850)
        }
    }

    override fun getFeed(uuid: String, page: Int): Flow<XdResponse<List<Thread>>> = flow {
        try {
            val response = fetchIndexedFeedPage(uuid, page).threads
            emit(XdResponse.Success(response))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun addFeed(uuid: String, tid: String, title: String?, replyCount: Int): Flow<XdResponse<String>> = flow {
        try {
            val interpreted = cloudFeedPageLock.withLock {
                interpretPlainFeedWrite(apiService.addFeed(uuid, tid).string()).also { result ->
                    if (result is XdResponse.Success) {
                        cloudFeedDao.indexPage(uuid, listOf(com.mioo.dao.data.local.CloudFeedThreadEntity(
                            feedUuid = uuid, threadId = tid, title = title.effectiveTitle(),
                            replyCount = replyCount, position = -1
                        )))
                        recordCloudFeedMutation(uuid)
                    }
                }
            }
            emit(interpreted)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun delFeed(uuid: String, tid: String): Flow<XdResponse<String>> = flow {
        try {
            val interpreted = cloudFeedPageLock.withLock {
                interpretPlainFeedWrite(apiService.delFeed(uuid, tid).string()).also { result ->
                    if (result is XdResponse.Success) {
                        cloudFeedDao.removeFeedThread(uuid, tid)
                        recordCloudFeedMutation(uuid)
                    }
                }
            }
            emit(interpreted)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val msg = e.localizedMessage ?: "Network error"
            emit(XdResponse.Error(message = msg, throwable = e))
        }
    }.flowOn(Dispatchers.IO)

    override fun isInCloudFeed(tid: String): Flow<Boolean> = cloudFeedDao.isMember(tid)

    override fun observeCloudFolderUuids(tid: String): Flow<Set<String>> =
        cloudFeedDao.observeFeedUuidsFor(tid).map { it.toSet() }

    // --- 发言草稿箱 ---

    override fun getDrafts() = draftStorage.getDrafts()

    override suspend fun getReplyDraftContent(threadId: String) = draftStorage.getReplyDraftContent(threadId)

    override suspend fun getReplyDraft(threadId: String, draftId: Long?) = draftStorage.getReplyDraft(threadId, draftId)

    override suspend fun saveReplyDraft(
        threadId: String, threadTitle: String?, content: String, title: String?, name: String?, draftId: Long?
    ) = draftStorage.saveReplyDraft(threadId, threadTitle, content, title, name, draftId)

    override suspend fun savePostDraft(boardId: String, boardName: String, name: String?, title: String?, content: String, draftId: Long) =
        draftStorage.savePostDraft(boardId, boardName, name, title, content, draftId)

    override suspend fun deleteDraft(id: Long) = draftStorage.deleteDraft(id)

    override suspend fun deletePostDrafts(boardId: String) = draftStorage.deletePostDrafts(boardId)

    override fun removeCloudSubscriptions(tid: String): Flow<XdResponse<Int>> = flow {
        try {
            // 本地成员缓存只覆盖浏览过的页（通常第1页）；对排在深页的串，
            // 必须遍历全部已配置云端收藏夹 + 用户自己的订阅 uuid，否则会静默 no-op。
            val configured = try {
                settingsDataStore.feedFoldersFlow.first().map { it.uuid }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            val ownUuid = try {
                // 归一化 JSON 饼干后再当 uuid 用
                com.mioo.dao.data.api.CookieInterceptor.extractHash(
                    settingsDataStore.userHashFlow.first()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val uuids = (cloudFeedDao.feedUuidsFor(tid) + configured + listOfNotNull(ownUuid))
                .filter { it.isNotBlank() }
                .distinct()
            var removed = 0
            for (uuid in uuids) {
                try {
                    cloudFeedPageLock.withLock {
                        if (interpretPlainFeedWrite(apiService.delFeed(uuid, tid).string()) is XdResponse.Success) {
                            cloudFeedDao.removeFeedThread(uuid, tid)
                            recordCloudFeedMutation(uuid)
                            removed++
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            emit(XdResponse.Success(removed))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyMap()
            }
        }

    override fun clearCache(): Flow<XdResponse<Unit>> = flow {
        try {
            cacheDao.clearNonOfflineCache()
            refPostCache.clear()
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                    val fetched = fetchThreadWithRaw(tid, 1)
                    cacheDao.insertCache(CacheEntity("thread_$tid", 1, fetched.rawJson))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                current++
                onProgress(current, total)
            }
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                        val fetched = fetchThreadWithRaw(tid, 1)
                        cacheDao.insertCache(CacheEntity("thread_$tid", 1, fetched.rawJson))
                        // Longer gap so list fling / image decode keep the network slot
                        delay(750)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
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
    override fun downloadFullThread(tid: String, conservative: Boolean): Flow<XdResponse<Unit>> =
        OfflineThreadDownloader(cacheDao, offlineThreadDao, context, stringListAdapter).download(
            tid, conservative, OfflineThreadDownloader.Dependencies(
                fetchThreadWithRaw = ::fetchThreadWithRaw,
                saveToHistory = ::saveToHistory,
                addBookmark = ::addBookmark,
                parseThreadBody = ::parseThreadBody,
                plainSummary = { plainSummary(it) },
                lookupForumName = ::lookupForumName,
                mapThreadException = ::mapThreadException
            )
        )

    // --- 已离线串 ---

    override fun getOfflineThreadSummaries(): Flow<List<com.mioo.dao.data.local.OfflineThreadSummary>> =
        offlineThreadDao.observeSummaryChanges()
            .map { count -> if (count == 0) emptyList() else offlineThreadDao.getSummaries() }
            .distinctUntilChanged()

    override fun isThreadDownloaded(threadId: String): Flow<Boolean> =
        offlineThreadDao.observeDownloaded(threadId)

    override suspend fun deleteOfflineThread(threadId: String) = withContext(Dispatchers.IO) {
        settingsDataStore.excludeAutomaticOffline(listOf(threadId))
        val entity = offlineThreadDao.get(threadId)
        offlineThreadDao.delete(threadId)
        cacheDao.deleteCacheByKey("thread_$threadId")
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
        val offlineIds = offlineThreadDao.getAllOnce().map { it.threadId }
        settingsDataStore.excludeAutomaticOffline(offlineIds + historyDao.getAllBookmarks().first().map { it.id })
        offlineThreadDao.clear()
        offlineIds.forEach { cacheDao.deleteCacheByKey("thread_$it") }
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
                combine(
                    historyDao.observeBookmarkIds().distinctUntilChanged(),
                    settingsDataStore.dataSaverEnabledFlow.distinctUntilChanged(),
                    offlineNetworkAllowed(context),
                    settingsDataStore.offlineExcludedIds.distinctUntilChanged(),
                    settingsDataStore.offlineUpdatePolicy.distinctUntilChanged()
                ) { ids, dataSaver, unmetered, excluded, policy ->
                    Triple(ids, excluded, policy.first && !dataSaver && unmetered)
                }.distinctUntilChanged().collectLatest { (ids, excluded, allowed) ->
                    if (!allowed) return@collectLatest
                    val offlineIds = offlineThreadDao.getAllOnce().mapTo(HashSet()) { it.threadId }
                    for (id in ids) {
                        if (id in offlineIds || id in excluded || (offlineSyncFailures[id] ?: 0) >= 2) continue
                        val response = try {
                            downloadFullThread(id, conservative = true).first()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            XdResponse.Error(message = "network")
                        }
                        if (response is XdResponse.Error) {
                            offlineSyncFailures[id] = (offlineSyncFailures[id] ?: 0) + 1
                        }
                        delay(10_000)
                    }
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
    private val forumNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun shareForumName(fid: String?): String = lookupForumName(fid)

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
            .replace(BR_TAG_REGEX, " ")
            .replace(HTML_OR_NEWLINE, " ")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        return if (plain.length > maxLen) plain.take(maxLen) + "…" else plain
    }

    override fun checkLatestRelease() = updateRepository.checkLatestRelease()
}
