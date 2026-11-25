package com.mioo.dao.di

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    @Test
    fun migration12To13AddsMissingColumnsAndRebuildsOrphanPreviewTable() {
        val db = FakeSupportDatabase(
            tableColumns = mapOf(
                "api_cache" to listOf("cacheKey", "page", "jsonResponse", "cachedAt")
            )
        )

        DatabaseModule.MIGRATION_12_13.migrate(db.asProxy())

        assertTrue(db.executedSql.any { it.contains("ALTER TABLE `api_cache` ADD COLUMN `searchIndexed`") })
        assertTrue(db.executedSql.any { it.contains("ALTER TABLE `api_cache` ADD COLUMN `feedStale`") })

        val dropIdx = db.executedSql.indexOfFirst { it == "DROP TABLE IF EXISTS `cache_post_previews`" }
        val createIdx = db.executedSql.indexOfFirst { it.contains("CREATE TABLE IF NOT EXISTS `cache_post_previews`") }
        val indexIdx = db.executedSql.indexOfFirst {
            it == "CREATE INDEX IF NOT EXISTS `index_cache_post_previews_threadId` ON `cache_post_previews` (`threadId`)"
        }
        assertTrue("Expected DROP TABLE before CREATE TABLE", dropIdx in 0 until createIdx)
        assertTrue("Expected CREATE INDEX after CREATE TABLE", indexIdx > createIdx)
        assertTrue(
            db.executedSql[createIdx].contains("ON UPDATE NO ACTION ON DELETE CASCADE")
        )
    }

    @Test
    fun migration12To13SkipsExistingColumnsWhenRebuildingPreviewTable() {
        val db = FakeSupportDatabase(
            tableColumns = mapOf(
                "api_cache" to listOf("cacheKey", "page", "jsonResponse", "cachedAt", "searchIndexed", "feedStale")
            )
        )

        DatabaseModule.MIGRATION_12_13.migrate(db.asProxy())

        assertFalse(db.executedSql.any { it.startsWith("ALTER TABLE `api_cache`") })
        assertEquals(
            listOf(
                "DROP TABLE IF EXISTS `cache_post_previews`",
                "CREATE INDEX IF NOT EXISTS `index_cache_post_previews_threadId` ON `cache_post_previews` (`threadId`)"
            ),
            listOf(db.executedSql.first(), db.executedSql.last())
        )
    }

    @Test
    fun migration11To12GuardsColumnsAndRebuildsCloudFeedSync() {
        val missingColsDb = FakeSupportDatabase(
            tableColumns = mapOf(
                "cloud_feed_threads" to listOf("feedUuid", "threadId", "savedAt", "replyCount")
            )
        )
        DatabaseModule.MIGRATION_11_12.migrate(missingColsDb.asProxy())

        assertTrue(missingColsDb.executedSql.any { it.contains("ALTER TABLE `cloud_feed_threads` ADD COLUMN `title`") })
        assertTrue(missingColsDb.executedSql.any { it.contains("ALTER TABLE `cloud_feed_threads` ADD COLUMN `position`") })
        val dropIdx = missingColsDb.executedSql.indexOf("DROP TABLE IF EXISTS `cloud_feed_sync`")
        val createIdx = missingColsDb.executedSql.indexOfFirst { it.contains("CREATE TABLE IF NOT EXISTS `cloud_feed_sync`") }
        assertTrue(dropIdx in 0 until createIdx)

        val existingColsDb = FakeSupportDatabase(
            tableColumns = mapOf(
                "cloud_feed_threads" to listOf("feedUuid", "threadId", "savedAt", "replyCount", "title", "position")
            )
        )
        DatabaseModule.MIGRATION_11_12.migrate(existingColsDb.asProxy())
        assertFalse(existingColsDb.executedSql.any { it.startsWith("ALTER TABLE `cloud_feed_threads`") })
        assertTrue(existingColsDb.executedSql.contains("DROP TABLE IF EXISTS `cloud_feed_sync`"))
    }

    private class FakeSupportDatabase(
        private val tableColumns: Map<String, List<String>>
    ) {
        val executedSql = mutableListOf<String>()

        fun asProxy(): SupportSQLiteDatabase = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    executedSql.add(args[0] as String)
                    null
                }
                "query" -> {
                    val sql = args[0] as String
                    val match = Regex("""PRAGMA table_info\(`?([^`)]+)`?\)""").find(sql)
                    val cols = match?.groupValues?.get(1)?.let { tableColumns[it] }.orEmpty()
                    createColumnCursor(cols)
                }
                "close" -> null
                else -> error("Unexpected SupportSQLiteDatabase call: ${method.name}")
            }
        } as SupportSQLiteDatabase

        private fun createColumnCursor(columns: List<String>): Cursor {
            var index = -1
            return Proxy.newProxyInstance(
                Cursor::class.java.classLoader,
                arrayOf(Cursor::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "getColumnIndexOrThrow" -> {
                        assertEquals("name", args[0])
                        1
                    }
                    "moveToNext" -> {
                        index++
                        index < columns.size
                    }
                    "getString" -> columns[index]
                    "close" -> null
                    else -> error("Unexpected Cursor call: ${method.name}")
                }
            } as Cursor
        }
    }
}
