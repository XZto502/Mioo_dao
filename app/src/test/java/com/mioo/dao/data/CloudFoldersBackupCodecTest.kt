package com.mioo.dao.data

import com.mioo.dao.data.model.FeedFolder
import com.mioo.dao.data.repository.CloudFoldersBackupCodec
import com.mioo.dao.data.repository.mergeFeedFolders
import com.mioo.dao.data.repository.normalizeFeedFolders
import com.mioo.dao.data.repository.resolveRestoredLastFeedFolder
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CloudFoldersBackupCodecTest {
    private val jsonAdapter = Moshi.Builder().build().adapter(Any::class.java)

    @Test
    fun roundTripPreservesCustomNamesUuidsAndLastSelectedFolder() {
        val folders = listOf(
            FeedFolder(name = "跑团记录", uuid = "1234567890abcdef"),
            FeedFolder(name = "工作存档", uuid = "fedcba0987654321")
        )
        val encoded = jsonAdapter.toJson(CloudFoldersBackupCodec.encodeMap(folders, "fedcba0987654321"))
        val decoded = CloudFoldersBackupCodec.decodeJson(encoded)

        assertNotNull(decoded)
        assertEquals(folders, decoded!!.folders)
        assertEquals("fedcba0987654321", decoded.lastFeedFolder)
    }

    @Test
    fun normalizeDropsBlankUuidAndDeduplicatesKeepingFirst() {
        val raw = listOf(
            FeedFolder(name = "空白", uuid = "   "),
            FeedFolder(name = " 主收藏 ", uuid = " uuid-1 "),
            FeedFolder(name = "重复名称", uuid = "uuid-1"),
            FeedFolder(name = "次收藏", uuid = "uuid-2")
        )
        assertEquals(
            listOf(
                FeedFolder(name = "主收藏", uuid = "uuid-1"),
                FeedFolder(name = "次收藏", uuid = "uuid-2")
            ),
            normalizeFeedFolders(raw)
        )
    }

    @Test
    fun mergeOverridesCustomNameForMatchingUuidAndKeepsExtraLocalFolders() {
        val existing = listOf(
            FeedFolder(name = "旧名字", uuid = "uuid-1"),
            FeedFolder(name = "本地独有", uuid = "uuid-local")
        )
        val imported = listOf(
            FeedFolder(name = "新名字", uuid = "uuid-1"),
            FeedFolder(name = "导入新增", uuid = "uuid-2")
        )
        assertEquals(
            listOf(
                FeedFolder(name = "新名字", uuid = "uuid-1"),
                FeedFolder(name = "导入新增", uuid = "uuid-2"),
                FeedFolder(name = "本地独有", uuid = "uuid-local")
            ),
            mergeFeedFolders(existing, imported)
        )
    }

    @Test
    fun resolveRestoredLastFeedFolderIgnoresUnknownOrBlank() {
        val merged = listOf(FeedFolder(name = "A", uuid = "uuid-1"))
        assertEquals("uuid-1", resolveRestoredLastFeedFolder(" uuid-1 ", merged))
        assertNull(resolveRestoredLastFeedFolder("", merged))
        assertNull(resolveRestoredLastFeedFolder("missing-uuid", merged))
    }

    @Test
    fun decodeSupportsPlainArrayFallback() {
        val rawJson = """[{"name":"桌面收藏","uuid":"abc123"},{"name":"无效","uuid":""}]"""
        val decoded = CloudFoldersBackupCodec.decodeJson(rawJson)
        assertNotNull(decoded)
        assertEquals(listOf(FeedFolder(name = "桌面收藏", uuid = "abc123")), decoded!!.folders)
        assertEquals("", decoded.lastFeedFolder)
    }
}
