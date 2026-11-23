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

internal data class FetchedThread(val thread: Thread, val rawJson: String)

internal fun shouldUseCachedOfflinePage(conservative: Boolean, page: Int, previousReplyCount: Int, pageSize: Int): Boolean =
    conservative && page < (previousReplyCount + pageSize - 1) / pageSize

internal class OfflineThreadDownloader(
    private val cacheDao: CacheDao,
    private val offlineThreadDao: OfflineThreadDao,
    private val context: Context,
    private val stringListAdapter: JsonAdapter<List<String>>
) {
    class Dependencies(
        val fetchThreadWithRaw: suspend (String, Int) -> FetchedThread,
        val addBookmark: suspend (Thread) -> Unit,
        val parseThreadBody: (String) -> Thread,
        val plainSummary: (String) -> String,
        val lookupForumName: suspend (String?) -> String,
        val mapThreadException: (Exception) -> String
    )

    fun download(tid: String, conservative: Boolean, dependencies: Dependencies, onProgress: (OfflineTransfer) -> Unit = {}): Flow<XdResponse<Unit>> = flow {
        val meter = OfflineTransferMeter(onProgress)
        try {
            meter.start("读取串信息", 0)
            val previousOffline = if (conservative) offlineThreadDao.get(tid) else null
            val page1Fetched = dependencies.fetchThreadWithRaw(tid, 1)
            val page1 = page1Fetched.thread

            cacheDao.insertIndexedCache(CacheEntity("thread_$tid", 1, page1Fetched.rawJson), listOf(page1))

            val totalReplies = page1.replyCount ?: 0
            val repliesPage1Size = page1.replies?.size ?: 0
            val allPosts = ArrayList<Reply>()
            allPosts.addAll(page1.replies.orEmpty())

            check(totalReplies == 0 || repliesPage1Size > 0) { "第 1 页回复缺失，请重试离线下载" }
            val pageCount = if (totalReplies == 0) 1 else (totalReplies - 1) / repliesPage1Size + 1
            meter.start("下载正文", pageCount)
            meter.received(page1Fetched.rawJson.toByteArray(Charsets.UTF_8).size.toLong())
            meter.completeUnit()
            if (totalReplies > repliesPage1Size) {
                val pageSize = repliesPage1Size
                val totalPages = (totalReplies + pageSize - 1) / pageSize

                for (page in 2..totalPages) {
                    try {
                        val expectedReplies = minOf(pageSize, totalReplies - (page - 1) * pageSize)
                        val cached = if (shouldUseCachedOfflinePage(conservative, page, previousOffline?.replyCount ?: 0, pageSize))
                            cacheDao.getCache("thread_$tid", page)?.jsonResponse?.let {
                                runCatching { dependencies.parseThreadBody(it) }.getOrNull()
                            } else null
                        val cachedReplies = cached?.replies.orEmpty()
                        if (cachedReplies.size >= expectedReplies) {
                            allPosts.addAll(cachedReplies)
                            meter.completeUnit()
                            continue
                        }
                        if (conservative) delay(2000)
                        val pageFetched = dependencies.fetchThreadWithRaw(tid, page)
                        val threadPage = pageFetched.thread
                        check(threadPage.replies.orEmpty().size >= expectedReplies) { "回复数量不足" }
                        cacheDao.insertCache(CacheEntity("thread_$tid", page, pageFetched.rawJson))
                        allPosts.addAll(threadPage.replies.orEmpty())
                        meter.received(pageFetched.rawJson.toByteArray(Charsets.UTF_8).size.toLong())
                        meter.completeUnit()
                        if (!conservative) delay(300)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        throw IllegalStateException("第 ${page} 页下载失败，请重试", e)
                    }
                }
            }
            check(allPosts.distinctBy { it.id }.size >= totalReplies) { "回复分页不完整，请重试离线下载" }

            // 离线图片：主楼优先的封面 + 各楼配图（缩略图优先，缺缩略图回落原图）
            val imageUrls = LinkedHashSet<String>()
            page1.imageUrl?.let(imageUrls::add)
            for (post in allPosts) post.imageUrl?.let(imageUrls::add)

            val imageFiles = LinkedHashSet<String>()
            var imageBytes = 0L
            val offlineRoot = com.mioo.dao.utils.OfflineImageStore.dir(context)
            meter.start("下载图片", minOf(imageUrls.size, com.mioo.dao.utils.OfflineImageStore.MAX_IMAGES_PER_THREAD))
            var processed = 0
            for (url in imageUrls) {
                currentCoroutineContext().ensureActive()
                if (processed >= com.mioo.dao.utils.OfflineImageStore.MAX_IMAGES_PER_THREAD) break
                processed++
                val alreadyDownloaded = com.mioo.dao.utils.OfflineImageStore.resolveLocalFile(offlineRoot, url) != null
                if (conservative && !alreadyDownloaded) delay(1000)
                val saved = com.mioo.dao.utils.OfflineImageStore.ensureDownloaded(offlineRoot, url, if (conservative) 128 * 1024 else 0, meter::received)
                if (saved != null) {
                    imageFiles.add(
                        com.mioo.dao.utils.OfflineImageStore.sanitizeFilename(
                            com.mioo.dao.utils.OfflineImageStore.islandImageName(url) ?: continue
                        )
                    )
                    imageBytes += saved.second
                }
                meter.completeUnit()
                if (!conservative) delay(60)
            }

            if (conservative && offlineThreadDao.get(tid) == null) {
                emit(XdResponse.Success(Unit))
                return@flow
            }
            meter.start("保存离线缓存", 0)
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
            if (!conservative) {
                try {
                    dependencies.addBookmark(page1)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("OfflineThread", "Could not add the offline thread to bookmarks", e)
                }
            }
            emit(XdResponse.Success(Unit))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(XdResponse.Error(message = dependencies.mapThreadException(e), throwable = e))
        }
    }.flowOn(Dispatchers.IO)

}
