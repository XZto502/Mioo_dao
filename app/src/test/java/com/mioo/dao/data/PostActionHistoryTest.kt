package com.mioo.dao.data

import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.local.PostActionHistoryEntity
import com.mioo.dao.data.model.Thread
import com.mioo.dao.data.repository.PostActionBackupCodec
import com.mioo.dao.data.repository.createdThreadId
import com.mioo.dao.data.repository.historyCookieIdentity
import com.mioo.dao.data.repository.matchCreatedThread
import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Test

class PostActionHistoryTest {
    private val original = PostActionHistoryEntity(
        id = "stable-action", type = HistoryType.NEW_THREAD.name, forumId = "4", forumName = "综合版1",
        cookieHash = "Ki6deii", title = "标题", authorName = "无名氏", content = "我的正文\n第二行 🐱",
        minimumThreadId = 100, timestamp = 123456789L
    )
    private fun candidate(id: Long, hash: String = "Ki6deii", body: String = "我的正文<br>第二行 🐱") =
        Thread(id = id, fid = 4, userHash = hash, title = "标题", name = "无名氏", content = body)

    @Test fun explicitResultIdsAndRedirectsAreRecognized() {
        assertEquals("123", createdThreadId("""{"success":true,"data":{"threadId":"123"}}"""))
        assertEquals("456", createdThreadId("<script>window.location.href='/t/456';</script>"))
        assertEquals("789", createdThreadId("""{"status":1,"url":"https://nmbxd.com/t/789"}"""))
        assertEquals("111", createdThreadId("https://nmbxd.com/t/111"))
    }

    @Test fun unrelatedLinksAndReplyQuotesAreNeverTreatedAsTheCreatedThread() {
        assertNull(createdThreadId("<html><a href='/t/999'>另一个串</a> >>No.888</html>"))
        assertNull(createdThreadId("发送成功"))
        assertNull(createdThreadId("""{"status":1,"id":0}"""))
    }

    @Test fun fallbackRequiresNewIdOwnerAndExactContent() {
        assertEquals("101", matchCreatedThread(original, listOf(candidate(99), candidate(101)))?.idStr)
        assertNull(matchCreatedThread(original, listOf(candidate(101, hash = "someoneElse"))))
        assertNull(matchCreatedThread(original, listOf(candidate(101, body = "我的正文"))))
        assertNull(matchCreatedThread(original, listOf(candidate(101).copy(fid = 14))))
    }

    @Test fun ambiguousOrOldIdenticalPostsDoNotProduceAFalseHistoryLink() {
        assertNull(matchCreatedThread(original, listOf(candidate(90))))
        assertNull(matchCreatedThread(original, listOf(candidate(101), candidate(102))))
        assertNull(matchCreatedThread(original.copy(minimumThreadId = null, cookieHash = "饼干-abc"), listOf(candidate(101))))
    }

    @Test fun missingPublicCookieCanUseUniqueNewPostButNotAnAmbiguousMatch() {
        val privateCookie = original.copy(cookieHash = "饼干-1234")
        assertEquals("101", matchCreatedThread(privateCookie, listOf(candidate(101)))?.idStr)
        assertNull(matchCreatedThread(privateCookie, listOf(candidate(101), candidate(102))))
    }

    @Test fun authenticationTokensAreNotCopiedIntoHistory() {
        val token = "secret-authentication-cookie-that-is-not-a-public-user-id"
        val raw = """{"name":"Ki6deii","cookie":"$token"}"""
        assertEquals("Ki6deii", historyCookieIdentity(raw))
        assertFalse(historyCookieIdentity(token).contains(token))
        assertEquals(historyCookieIdentity(token), historyCookieIdentity("""{"cookie":"$token"}"""))
    }

    @Test fun escapedApiHtmlMatchesLiteralSubmittedTextExactlyOnce() {
        val source = original.copy(content = "<b> &amp; 猫 🐱")
        assertEquals("101", matchCreatedThread(source, listOf(candidate(101, body = "&lt;b&gt; &amp;amp; 猫 &#x1f431;")))?.idStr)
        val imageOnly = original.copy(cookieHash = "饼干-abc", content = "", title = null)
        assertNull(matchCreatedThread(imageOnly, listOf(candidate(101, body = "").copy(title = null))))
    }

    @Test fun actionBackupRoundTripsBothTypesAndKeepsStableIds() {
        for (type in listOf(HistoryType.REPLY, HistoryType.NEW_THREAD)) {
            val source = original.copy(type = type.name, threadId = "101", threadTitle = "主串", threadSummary = "原串摘要", imageUrl = "2026/image.jpg")
            val json = Moshi.Builder().build().adapter(Any::class.java).toJson(PostActionBackupCodec.encode(source))
            val restored = PostActionBackupCodec.decode(json, type) { _, _, _ -> error("No local attachment") }
            assertEquals(source, restored)
        }
    }

    @Test fun attachmentBytesArePortableAndRebasedOnTheNewDevice() {
        val bytes = byteArrayOf(1, 2, 3, 0, -1)
        val source = original.copy(imagePath = "/old/device/photo.png")
        val payload = PostActionBackupCodec.encode(source) { path -> assertEquals(source.imagePath, path); bytes }
        val json = Moshi.Builder().build().adapter(Any::class.java).toJson(payload)
        assertFalse(json.contains("/old/device"))
        val restored = PostActionBackupCodec.decode(json, HistoryType.NEW_THREAD) { id, ext, image ->
            assertEquals(source.id, id); assertEquals("png", ext); assertArrayEquals(bytes, image)
            "/new/device/photo.png"
        }
        assertEquals(source.copy(imagePath = "/new/device/photo.png"), restored)
    }

    @Test fun pendingThreadSurvivesBackupAndInvalidReplyIdsAreRejected() {
        val json = Moshi.Builder().build().adapter(Any::class.java).toJson(PostActionBackupCodec.encode(original))
        assertEquals(original, PostActionBackupCodec.decode(json, HistoryType.NEW_THREAD) { _, _, _ -> error("No image") })
        assertNull(PostActionBackupCodec.decode(json, HistoryType.REPLY) { _, _, _ -> error("No image") })
        val invalid = json.replace("NEW_THREAD", "REPLY")
        assertNull(PostActionBackupCodec.decode(invalid, HistoryType.REPLY) { _, _, _ -> error("No image") })
    }
}
