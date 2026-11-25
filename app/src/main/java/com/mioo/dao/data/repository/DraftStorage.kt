package com.mioo.dao.data.repository

import com.mioo.dao.data.local.DraftDao
import com.mioo.dao.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Persists draft-box entries and migrates the legacy DataStore drafts. */
@Singleton
class DraftStorage @Inject constructor(
    private val draftDao: DraftDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val migration = DraftMigrationCoordinator(
        isComplete = { settingsDataStore.isDraftsMigrated() },
        migrate = {
            val now = System.currentTimeMillis()
            val drafts = settingsDataStore.getAllThreadDrafts().map { (tid, text) ->
                com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_REPLY,
                    targetId = tid,
                    content = text,
                    updatedAt = now
                )
            }.toMutableList()
            val postDraft = settingsDataStore.getNewThreadDraft()
            if (postDraft.isNotBlank()) {
                drafts += com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_POST,
                    targetId = "new_thread",
                    targetName = "草稿",
                    content = postDraft,
                    updatedAt = now
                )
            }
            draftDao.migrateLegacyDrafts(drafts)
        },
        markComplete = { settingsDataStore.markDraftsMigrated() }
    )

    private suspend fun ensureDraftsMigrated() = migration.ensureMigrated()

    fun getDrafts(): Flow<List<com.mioo.dao.data.local.DraftEntity>> = flow {
        ensureDraftsMigrated()
        emitAll(draftDao.getAll())
    }.flowOn(Dispatchers.IO)

    suspend fun getReplyDraftContent(threadId: String): String {
        ensureDraftsMigrated()
        return try {
            draftDao.find(com.mioo.dao.data.local.DraftEntity.TYPE_REPLY, threadId)?.content ?: ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun getReplyDraft(threadId: String, draftId: Long?): com.mioo.dao.data.local.DraftEntity? {
        ensureDraftsMigrated()
        return if (draftId == null) draftDao.find(com.mioo.dao.data.local.DraftEntity.TYPE_REPLY, threadId)
        else draftDao.findById(draftId)?.takeIf { it.isReply && it.targetId == threadId }
    }

    suspend fun saveReplyDraft(
        threadId: String, threadTitle: String?, content: String, title: String?, name: String?, draftId: Long?
    ): Long? {
        ensureDraftsMigrated()
        return draftDao.writeReply(
            com.mioo.dao.data.local.DraftEntity(
                type = com.mioo.dao.data.local.DraftEntity.TYPE_REPLY,
                targetId = threadId,
                targetName = threadTitle.orEmpty(),
                title = title,
                name = name,
                content = content
            ), draftId
        )
    }

    suspend fun savePostDraft(boardId: String, boardName: String, name: String?, title: String?, content: String, draftId: Long): Long {
        ensureDraftsMigrated()
        val fields = com.mioo.dao.data.local.PostDraftContent(title.orEmpty(), name.orEmpty(), content)
        require(fields.hasContent)
        return draftDao.upsert(fields.toDraft(boardId, boardName).copy(id = draftId))
    }

    suspend fun deleteDraft(id: Long) {
        ensureDraftsMigrated()
        draftDao.removeDraft(id)
    }

    suspend fun deletePostDrafts(boardId: String) {
        ensureDraftsMigrated()
        draftDao.deleteFor(com.mioo.dao.data.local.DraftEntity.TYPE_POST, boardId)
    }

}
