package com.mioo.dao.data.repository

import com.mioo.dao.data.local.DraftDao
import com.mioo.dao.data.local.DraftEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DraftMigrationCoordinatorTest {
    private class FakeDraftDao : DraftDao {
        val rows = mutableListOf<DraftEntity>()
        override fun getAll(): Flow<List<DraftEntity>> = flowOf(rows.filter { it.type != "_LEGACY_MIGRATION" })
        override suspend fun find(type: String, targetId: String) = rows.lastOrNull { it.type == type && it.targetId == targetId }
        override suspend fun findById(id: Long) = rows.find { it.id == id }
        override suspend fun archiveOtherReplies(threadId: String, keepId: Long) = Unit
        override suspend fun findIdentical(type: String, targetId: String, title: String?, name: String?, content: String, updatedAt: Long) =
            rows.find { it.type == type && it.targetId == targetId && it.title == title && it.name == name && it.content == content && it.updatedAt == updatedAt }
        override suspend fun upsert(draft: DraftEntity): Long {
            val id = if (draft.id == 0L) (rows.maxOfOrNull { it.id } ?: 0) + 1 else draft.id
            rows.removeAll { it.id == id }
            rows.add(draft.copy(id = id))
            return id
        }
        override suspend fun upsertAll(drafts: List<DraftEntity>) { drafts.forEach { upsert(it) } }
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun deleteFor(type: String, targetId: String) { rows.removeAll { it.type == type && it.targetId == targetId } }
    }

    @Test fun concurrentCallsPerformOneMigration() = runBlocking {
        var writes = 0
        var active = 0
        var maxActive = 0
        var flag = false
        val coordinator = DraftMigrationCoordinator({ flag }, {
            writes++
            active++
            maxActive = maxOf(maxActive, active)
            yield()
            active--
        }, { flag = true })
        (1..30).map { async { coordinator.ensureMigrated() } }.awaitAll()
        assertEquals(1, writes)
        assertEquals(1, maxActive)
        assertTrue(flag)
    }

    @Test fun retryAfterFlagFailureDoesNotRestoreDeletedDraftOrReplaceExistingDraft() = runBlocking {
        val dao = FakeDraftDao()
        val legacy = listOf(
            DraftEntity(type = DraftEntity.TYPE_REPLY, targetId = "1", content = "old"),
            DraftEntity(type = DraftEntity.TYPE_REPLY, targetId = "2", content = "legacy")
        )
        dao.upsert(legacy[0].copy(content = "new"))
        var flag = false
        var failFlagWrite = true
        val coordinator = DraftMigrationCoordinator({ flag }, { dao.migrateLegacyDrafts(legacy) }, {
            if (failFlagWrite) { failFlagWrite = false; throw IllegalStateException("flag write failed") }
            flag = true
        })
        try { coordinator.ensureMigrated(); fail("expected failure") } catch (_: IllegalStateException) { }
        assertEquals("new", dao.find(DraftEntity.TYPE_REPLY, "1")?.content)
        dao.deleteFor(DraftEntity.TYPE_REPLY, "2")
        val restarted = DraftMigrationCoordinator({ flag }, { dao.migrateLegacyDrafts(legacy) }, { flag = true })
        restarted.ensureMigrated()
        assertNull(dao.find(DraftEntity.TYPE_REPLY, "2"))
        assertEquals("new", dao.find(DraftEntity.TYPE_REPLY, "1")?.content)
        assertTrue(flag)
    }

    @Test fun cancellationIsPropagatedAndCanBeRetried() = runBlocking {
        var attempts = 0
        val coordinator = DraftMigrationCoordinator({ false }, {
            if (++attempts == 1) throw CancellationException("cancelled")
        }, {})
        try { coordinator.ensureMigrated(); fail("expected cancellation") } catch (_: CancellationException) { }
        coordinator.ensureMigrated()
        assertEquals(2, attempts)
    }
}
