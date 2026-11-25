package com.mioo.dao.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts WHERE type != '_LEGACY_MIGRATION' ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<DraftEntity>>

    /** Commit the marker with the rows so retries never restore a deleted draft. */
    @Transaction
    suspend fun migrateLegacyDrafts(drafts: List<DraftEntity>) {
        if (find("_LEGACY_MIGRATION", "drafts") != null) return
        for (draft in drafts) {
            val exists = find(draft.type, draft.targetId) != null ||
                (draft.type == DraftEntity.TYPE_REPLY &&
                    find(DraftEntity.TYPE_ARCHIVED_REPLY, draft.targetId) != null)
            if (!exists) upsert(draft)
        }
        upsert(DraftEntity(type = "_LEGACY_MIGRATION", targetId = "drafts", content = ""))
    }

    @Query("SELECT * FROM drafts WHERE type = :type AND targetId = :targetId ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun find(type: String, targetId: String): DraftEntity?

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun findById(id: Long): DraftEntity?

    @Query("UPDATE drafts SET type = 'ARCHIVED_REPLY' WHERE type = 'REPLY' AND targetId = :threadId AND id != :keepId")
    suspend fun archiveOtherReplies(threadId: String, keepId: Long)

    @Transaction
    suspend fun writeReply(draft: DraftEntity, selectedId: Long?): Long? {
        val active = find(DraftEntity.TYPE_REPLY, draft.targetId)
        val selected = if (selectedId == null) active
        else findById(selectedId)?.takeIf { it.isReply && it.targetId == draft.targetId } ?: return null
        val archived = selected != null && selected.id != active?.id
        val type = if (archived) DraftEntity.TYPE_ARCHIVED_REPLY else DraftEntity.TYPE_REPLY
        archiveOtherReplies(draft.targetId, active?.id ?: 0)
        if (draft.content.isBlank() && draft.title.isNullOrBlank() && draft.name.isNullOrBlank()) {
            if (archived) return selected?.id
            selected?.let { deleteById(it.id) }
            return null
        }
        return upsert(draft.copy(id = selected?.id ?: 0, type = type))
    }

    @Transaction
    suspend fun removeDraft(id: Long) {
        val draft = findById(id) ?: return
        if (draft.type == DraftEntity.TYPE_REPLY && find(DraftEntity.TYPE_REPLY, draft.targetId)?.id == id) {
            archiveOtherReplies(draft.targetId, id)
        }
        deleteById(id)
    }

    @Transaction
    suspend fun importReply(draft: DraftEntity) {
        val identical = listOf(DraftEntity.TYPE_REPLY, DraftEntity.TYPE_ARCHIVED_REPLY).any { type ->
            findIdentical(type, draft.targetId, draft.title, draft.name, draft.content, draft.updatedAt) != null
        }
        if (identical) return
        val active = find(DraftEntity.TYPE_REPLY, draft.targetId)
        if (draft.type == DraftEntity.TYPE_REPLY && active != null) {
            if (active.updatedAt >= draft.updatedAt) {
                upsert(draft.copy(type = DraftEntity.TYPE_ARCHIVED_REPLY))
                return
            }
            archiveOtherReplies(draft.targetId, 0)
        }
        upsert(draft)
    }

    @Query("SELECT * FROM drafts WHERE type = :type AND targetId = :targetId AND title IS :title AND name IS :name AND content = :content AND updatedAt = :updatedAt LIMIT 1")
    suspend fun findIdentical(type: String, targetId: String, title: String?, name: String?, content: String, updatedAt: Long): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(drafts: List<DraftEntity>)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM drafts WHERE type = :type AND targetId = :targetId")
    suspend fun deleteFor(type: String, targetId: String)
}
