package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 发言草稿箱 entry: a reply draft (keyed by thread) or a new-thread draft
 * (keyed by board). Posts can have several drafts per board.
 */
@Entity(
    tableName = "drafts",
    indices = [Index(value = ["type", "targetId"])]
)
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "REPLY" (回复串内) or "POST" (发新串). */
    val type: String,
    /** Thread id for REPLY, board id for POST. */
    val targetId: String,
    /** Thread title / board name for display. */
    val targetName: String = "",
    /** Optional title entered in the editor. */
    val title: String? = null,
    /** Optional name entered in the editor. */
    val name: String? = null,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isReply: Boolean get() = type == TYPE_REPLY || type == TYPE_ARCHIVED_REPLY

    companion object {
        const val TYPE_REPLY = "REPLY"
        const val TYPE_ARCHIVED_REPLY = "ARCHIVED_REPLY"
        const val TYPE_POST = "POST"
    }
}
