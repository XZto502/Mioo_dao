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
    /** In-process mirror of the DataStore one-time flag. */
    private var draftsMigrated = false

    /**
     * One-time sweep of legacy DataStore drafts (per-thread reply map + global
     * new-thread draft) into the Room draft box. Runs before any draft read/write.
     */
    private suspend fun ensureDraftsMigrated() {
        if (draftsMigrated) return
        if (settingsDataStore.isDraftsMigrated()) {
            draftsMigrated = true
            return
        }
        val now = System.currentTimeMillis()
        settingsDataStore.getAllThreadDrafts().forEach { (tid, text) ->
            draftDao.upsert(
                com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_REPLY,
                    targetId = tid,
                    content = text,
                    updatedAt = now
                )
            )
        }
        val postDraft = try {
            settingsDataStore.getNewThreadDraft()
        } catch (_: Exception) {
            ""
        }
        if (postDraft.isNotBlank()) {
            draftDao.upsert(
                com.mioo.dao.data.local.DraftEntity(
                    type = com.mioo.dao.data.local.DraftEntity.TYPE_POST,
                    targetId = "new_thread",
                    targetName = "草稿",
                    content = postDraft,
                    updatedAt = now
                )
            )
        }
        settingsDataStore.markDraftsMigrated()
        draftsMigrated = true
    }

    fun getDrafts(): Flow<List<com.mioo.dao.data.local.DraftEntity>> = flow {
        ensureDraftsMigrated()
        emitAll(draftDao.getAll())
    }.flowOn(Dispatchers.IO)

    suspend fun getReplyDraftContent(threadId: String): String {
        ensureDraftsMigrated()
        return try {
            draftDao.find(com.mioo.dao.data.local.DraftEntity.TYPE_REPLY, threadId)?.content ?: ""
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
        draftDao.removeDraft(id)
    }

    suspend fun deletePostDrafts(boardId: String) {
        ensureDraftsMigrated()
        draftDao.deleteFor(com.mioo.dao.data.local.DraftEntity.TYPE_POST, boardId)
    }

}
