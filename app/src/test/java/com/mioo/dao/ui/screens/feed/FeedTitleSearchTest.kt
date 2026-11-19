package com.mioo.dao.ui.screens.feed

import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CloudFeedThreadEntity
import com.mioo.dao.data.local.OfflineThreadSummary
import com.mioo.dao.data.model.FeedFolder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedTitleSearchTest {
    @Test fun searchesTitlesAcrossFoldersAndOfflineWithoutMatchingContentOrIds() = runBlocking {
        val index = buildFeedTitleIndex(
            listOf(
                CloudFeedThreadEntity("a", "100", title = "Manga 讨论"),
                CloudFeedThreadEntity("b", "100", title = "Manga 讨论"),
                CloudFeedThreadEntity("b", "200", title = "Manga 推荐"),
                CloudFeedThreadEntity("removed", "300", title = "Manga"),
                CloudFeedThreadEntity("a", "manga", title = "无标题")
            ),
            listOf(FeedFolder("日常", "a"), FeedFolder("分享", "b")),
            listOf(offline("100", "Manga 讨论"), offline("101", "离线 Manga")),
            listOf(bookmark("400", "其他标题"), bookmark("500", "本地 MANGA"))
        )
        val matches = index.search(" manga ")

        assertEquals(listOf("100", "101", "500", "200"), matches.map { it.threadId })
        assertEquals(setOf("已离线", "日常", "分享"), matches.first().sources)
        assertEquals(listOf("200"), index.search("推荐").map { it.threadId })
    }

    private fun offline(id: String, title: String) = OfflineThreadSummary(
        id, title, "", "", null, "", 0, 0, 0, null, 0, 0
    )

    private fun bookmark(id: String, title: String) =
        BookmarkEntity(id, null, "Manga 正文", "", "", title, null)
}
