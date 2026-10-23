package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 发言草稿箱 entry: a reply draft (keyed by thread) or a new-thread draft
 * (keyed by board). One draft per (type, targetId) — re-saving replaces it.
 */
@Entity(
    tableName = "drafts",
    indices = [Index(value = ["type", "targetId"], unique = true)]
)
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "REPLY" (回复串内) or "POST" (发新串). */
    val type: String,
    /** Thread id for REPLY, board id for POST. */
    val targetId: String,
    /** Thread title / board name for display. */
    val targetName: String = "",
    /** POST only: thread title field. */
    val title: String? = null,
    /** POST only: name field (饼干名/昵称). */
    val name: String? = null,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_REPLY = "REPLY"
        const val TYPE_POST = "POST"
    }
}
