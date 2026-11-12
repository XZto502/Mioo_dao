package com.mioo.dao.data.repository

import android.content.Context
import com.mioo.dao.data.local.CacheDao
import com.mioo.dao.data.local.CacheEntity
import com.mioo.dao.data.local.OfflineThreadDao
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.XdResponse
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal fun shouldUseCachedOfflinePage(conservative: Boolean, page: Int, previousReplyCount: Int, pageSize: Int): Boolean =
    conservative && page < (previousReplyCount + pageSize - 1) / pageSize

internal class OfflineThreadDownloader(
    private val cacheDao: CacheDao,
    private val offlineThreadDao: OfflineThreadDao,
    private val context: Context,
    private val threadAdapter: JsonAdapter<Thread>,
    private val stringListAdapter: JsonAdapter<List<String>>
) {
    class Dependencies(
        val fetchThread: suspend (String, Int) -> Thread,
        val saveToHistory: suspend (Thread) -> Unit,
        val addBookmark: suspend (Thread) -> Unit,
        val parseThreadBody: (String) -> Thread,
        val plainSummary: (String) -> String,
        val lookupForumName: suspend (String?) -> String,
        val mapThreadException: (Exception) -> String
    )

    fun download(tid: String, conservative: Boolean, dependencies: Dependencies): Flow<XdResponse<Unit>> = flow {
        try {
            val previousOffline = if (conservative) offlineThreadDao.get(tid) else null
            val page1 = dependencies.fetchThread(tid, 1)
            if (!conservative) {
                dependencies.saveToHistory(page1)
                dependencies.addBookmark(page1)
            }

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
                        val cached = if (shouldUseCachedOfflinePage(conservative, page, previousOffline?.replyCount ?: 0, pageSize))
                            cacheDao.getCache("thread_$tid", page)?.jsonResponse?.let {
                                runCatching { dependencies.parseThreadBody(it) }.getOrNull()
                            } else null
                        if (cached != null) {
                            allPosts.addAll(cached.replies.orEmpty())
                            continue
                        }
                        if (conservative) delay(2000)
                        val threadPage = dependencies.fetchThread(tid, page)
                        val jsonPage = threadAdapter.toJson(threadPage)
                        cacheDao.insertCache(CacheEntity("thread_$tid", page, jsonPage))
                        allPosts.addAll(threadPage.replies.orEmpty())
                        if (!conservative) delay(300)
                    } catch (e: Exception) {
                        if (e is CancellationException || conservative) throw e
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
                val alreadyDownloaded = com.mioo.dao.utils.OfflineImageStore.resolveLocalFile(offlineRoot, url) != null
                if (conservative && !alreadyDownloaded) delay(1000)
                val saved = com.mioo.dao.utils.OfflineImageStore.ensureDownloaded(offlineRoot, url, if (conservative) 128 * 1024 else 0)
                if (saved != null) {
                    imageFiles.add(
                        com.mioo.dao.utils.OfflineImageStore.sanitizeFilename(
                            com.mioo.dao.utils.OfflineImageStore.islandImageName(url) ?: continue
                        )
                    )
                    imageBytes += saved.second
                }
                if (!conservative) delay(60)
            }

            if (conservative && offlineThreadDao.get(tid) == null) {
                emit(XdResponse.Success(Unit))
                return@flow
            }
            offlineThreadDao.upsert(
                com.mioo.dao.data.local.OfflineThreadEntity(
                    threadId = tid,
                    title = page1.title?.takeIf { it.isNotBlank() && it.trim() != "无标题" },
                    contentSummary = dependencies.plainSummary(page1.content),
                    poUserHash = page1.userHash,
                    fid = page1.fidStr,
                    forumName = dependencies.lookupForumName(page1.fidStr),
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
            emit(XdResponse.Error(message = dependencies.mapThreadException(e), throwable = e))
        }
    }.flowOn(Dispatchers.IO)

}
