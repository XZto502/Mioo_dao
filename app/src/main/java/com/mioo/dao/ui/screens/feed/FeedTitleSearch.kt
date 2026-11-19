package com.mioo.dao.ui.screens.feed

import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CloudFeedThreadEntity
import com.mioo.dao.data.local.OfflineThreadSummary
import com.mioo.dao.data.model.FeedFolder
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.ui.components.IslandImage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class FeedTitleMatch(
    val threadId: String,
    val title: String?,
    val sources: Set<String>,
    val userId: String = "",
    val createdAt: String = "",
    val timestamp: Long = 0L,
    val htmlContent: String? = null,
    val plainContent: String? = null,
    val imageUrl: String? = null,
    val coverFile: String? = null,
    val replyCount: Int = 0
)

internal data class FeedTitleCandidate(
    val threadId: String,
    val title: String?,
    val searchableContent: String,
    val source: String,
    val userId: String = "",
    val createdAt: String = "",
    val timestamp: Long = 0L,
    val htmlContent: String? = null,
    val plainContent: String? = null,
    val imageUrl: String? = null,
    val coverFile: String? = null,
    val replyCount: Int = 0
)

private val FEED_SEARCH_BR_REGEX = Regex("(?i)<br\\s*/?>")
private val FEED_SEARCH_TAG_REGEX = Regex("<[^>]*>")
private val FEED_SEARCH_WS_REGEX = Regex("\\s+")

internal fun stripFeedHtmlForSearch(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(FEED_SEARCH_BR_REGEX, " ")
        .replace(FEED_SEARCH_TAG_REGEX, "")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(FEED_SEARCH_WS_REGEX, " ")
        .trim()
}

internal class FeedTitleIndex(private val candidates: List<FeedTitleCandidate>) {
    suspend fun search(query: String): List<FeedTitleMatch> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val context = currentCoroutineContext()
        val matches = linkedMapOf<String, FeedTitleMatch>()
        candidates.forEachIndexed { index, candidate ->
            if (index % 128 == 0) context.ensureActive()
            val titleHit = candidate.title?.contains(needle, ignoreCase = true) == true
            val contentHit = candidate.searchableContent.contains(needle, ignoreCase = true)
            if (!titleHit && !contentHit) return@forEachIndexed
            val existing = matches[candidate.threadId]
            matches[candidate.threadId] = if (existing == null) {
                FeedTitleMatch(
                    threadId = candidate.threadId,
                    title = candidate.title,
                    sources = setOf(candidate.source),
                    userId = candidate.userId,
                    createdAt = candidate.createdAt,
                    timestamp = candidate.timestamp,
                    htmlContent = candidate.htmlContent,
                    plainContent = candidate.plainContent,
                    imageUrl = candidate.imageUrl,
                    coverFile = candidate.coverFile,
                    replyCount = candidate.replyCount
                )
            } else {
                existing.copy(
                    title = existing.title ?: candidate.title,
                    sources = if (candidate.source in existing.sources) existing.sources else existing.sources + candidate.source,
                    userId = existing.userId.ifBlank { candidate.userId },
                    createdAt = existing.createdAt.ifBlank { candidate.createdAt },
                    timestamp = maxOf(existing.timestamp, candidate.timestamp),
                    htmlContent = existing.htmlContent ?: candidate.htmlContent,
                    plainContent = existing.plainContent ?: candidate.plainContent,
                    imageUrl = existing.imageUrl ?: candidate.imageUrl,
                    coverFile = existing.coverFile ?: candidate.coverFile,
                    replyCount = maxOf(existing.replyCount, candidate.replyCount)
                )
            }
        }
        return matches.values.toList()
    }
}

internal fun buildFeedTitleIndex(
    cloudIndex: List<CloudFeedThreadEntity>,
    folders: List<FeedFolder>,
    offline: List<OfflineThreadSummary>,
    bookmarks: List<BookmarkEntity>,
    cachedThreads: Map<String, Thread> = emptyMap()
): FeedTitleIndex {
    val candidates = ArrayList<FeedTitleCandidate>()
    val names = folders.associate { it.uuid to it.name }
    offline.forEach { item ->
        val title = item.title.effectiveTitle()
        val summary = item.contentSummary.trim()
        if (title == null && summary.isEmpty()) return@forEach
        val cached = cachedThreads[item.threadId]
        candidates.add(
            FeedTitleCandidate(
                threadId = item.threadId,
                title = title ?: cached?.title.effectiveTitle(),
                searchableContent = summary.ifEmpty { stripFeedHtmlForSearch(cached?.content.orEmpty()) },
                source = "已离线",
                userId = item.poUserHash.ifBlank { cached?.userHash.orEmpty() },
                createdAt = cached?.now.orEmpty(),
                timestamp = item.downloadedAt,
                htmlContent = cached?.content?.takeIf { it.isNotBlank() },
                plainContent = summary.takeIf { it.isNotEmpty() },
                imageUrl = cached?.imageUrl?.let(IslandImage::url),
                coverFile = item.coverFile,
                replyCount = maxOf(item.replyCount, cached?.replyCount ?: 0)
            )
        )
    }
    bookmarks.forEach { item ->
        val title = item.title.effectiveTitle()
        val plain = stripFeedHtmlForSearch(item.content)
        if (title == null && plain.isEmpty()) return@forEach
        candidates.add(
            FeedTitleCandidate(
                threadId = item.id,
                title = title,
                searchableContent = plain,
                source = "本地收藏",
                userId = item.userid,
                createdAt = item.now,
                timestamp = item.timestamp,
                htmlContent = item.content.takeIf { it.isNotBlank() },
                replyCount = item.lastKnownReplyCount
            )
        )
    }
    cloudIndex.forEach { entry ->
        val folderName = names[entry.feedUuid] ?: return@forEach
        val cached = cachedThreads[entry.threadId]
        val title = entry.title.effectiveTitle() ?: cached?.title.effectiveTitle()
        val plain = stripFeedHtmlForSearch(cached?.content.orEmpty())
        if (title == null && plain.isEmpty()) return@forEach
        candidates.add(
            FeedTitleCandidate(
                threadId = entry.threadId,
                title = title,
                searchableContent = plain,
                source = folderName,
                userId = cached?.userHash.orEmpty(),
                createdAt = cached?.now.orEmpty(),
                timestamp = entry.savedAt,
                htmlContent = cached?.content?.takeIf { it.isNotBlank() },
                imageUrl = cached?.imageUrl?.let(IslandImage::url),
                replyCount = maxOf(entry.replyCount, cached?.replyCount ?: 0)
            )
        )
    }
    return FeedTitleIndex(candidates)
}
