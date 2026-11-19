package com.mioo.dao.ui.screens.feed

import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CloudFeedThreadEntity
import com.mioo.dao.data.local.OfflineThreadSummary
import com.mioo.dao.data.model.FeedFolder
import com.mioo.dao.data.model.effectiveTitle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class FeedTitleMatch(val threadId: String, val title: String, val sources: Set<String>)

internal data class FeedTitleCandidate(val threadId: String, val title: String, val source: String)

internal class FeedTitleIndex(private val candidates: List<FeedTitleCandidate>) {
    suspend fun search(query: String): List<FeedTitleMatch> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val context = currentCoroutineContext()
        val matches = linkedMapOf<String, FeedTitleMatch>()
        candidates.forEachIndexed { index, candidate ->
            if (index % 128 == 0) context.ensureActive()
            if (!candidate.title.contains(needle, ignoreCase = true)) return@forEachIndexed
            val existing = matches[candidate.threadId]
            matches[candidate.threadId] = if (existing == null) {
                FeedTitleMatch(candidate.threadId, candidate.title, setOf(candidate.source))
            } else if (candidate.source !in existing.sources) {
                existing.copy(sources = existing.sources + candidate.source)
            } else existing
        }
        return matches.values.toList()
    }
}

internal fun buildFeedTitleIndex(
    cloudIndex: List<CloudFeedThreadEntity>,
    folders: List<FeedFolder>,
    offline: List<OfflineThreadSummary>,
    bookmarks: List<BookmarkEntity>
): FeedTitleIndex {
    val candidates = ArrayList<FeedTitleCandidate>()
    val names = folders.associate { it.uuid to it.name }
    fun add(threadId: String, rawTitle: String?, source: String) {
        val title = rawTitle.effectiveTitle() ?: return
        candidates.add(FeedTitleCandidate(threadId, title, source))
    }
    offline.forEach { add(it.threadId, it.title, "已离线") }
    bookmarks.forEach { add(it.id, it.title, "本地收藏") }
    cloudIndex.forEach { entry ->
        names[entry.feedUuid]?.let { name -> add(entry.threadId, entry.title, name) }
    }
    return FeedTitleIndex(candidates)
}
