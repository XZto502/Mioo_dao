package com.mioo.dao.ui.screens.feed

import com.mioo.dao.data.local.BookmarkEntity
import com.mioo.dao.data.local.CloudFeedThreadEntity
import com.mioo.dao.data.local.OfflineThreadSummary
import com.mioo.dao.data.model.FeedFolder
import com.mioo.dao.data.model.Thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTitleSearchTest {
    @Test fun searchesTitlesAndOpContentAcrossFoldersAndOfflineWithoutMatchingRawIdsOrHtmlTags() = runBlocking {
        val index = buildFeedTitleIndex(
            listOf(
                CloudFeedThreadEntity("a", "100", title = "Manga 讨论"),
                CloudFeedThreadEntity("b", "100", title = "Manga 讨论"),
                CloudFeedThreadEntity("b", "200", title = "Manga 推荐"),
                CloudFeedThreadEntity("removed", "300", title = "Manga"),
                CloudFeedThreadEntity("a", "manga", title = "无标题"),
                CloudFeedThreadEntity("a", "600", title = "无标题")
            ),
            listOf(FeedFolder("日常", "a"), FeedFolder("分享", "b")),
            listOf(offline("100", "Manga 讨论"), offline("101", "离线 Manga")),
            listOf(bookmark("400", "其他标题", "<font color=\"#789922\">Manga 正文</font>"), bookmark("500", "本地 MANGA", "普通内容")),
            mapOf("600" to Thread(id = 600, title = "无标题", content = "来自缓存的<br/>OP 正文关键字", userHash = "po600"))
        )
        val matches = index.search(" manga ")

        assertEquals(listOf("100", "101", "400", "500", "200"), matches.map { it.threadId })
        assertEquals(setOf("已离线", "日常", "分享"), matches.first().sources)
        assertEquals(listOf("200"), index.search("推荐").map { it.threadId })
        assertEquals(listOf("600"), index.search("op 正文").map { it.threadId })
        assertEquals("po600", index.search("op 正文").first().userId)
        assertTrue(index.search("font").isEmpty())
    }

    private fun offline(id: String, title: String) = OfflineThreadSummary(
        id, title, "", "", null, "", 0, 0, 0, null, 0, 0
    )

    private fun bookmark(id: String, title: String, content: String) =
        BookmarkEntity(id, null, content, "", "", title, null)
}
