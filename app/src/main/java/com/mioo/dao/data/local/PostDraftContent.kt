package com.mioo.dao.data.local

/** All editable text fields travel together when saving or restoring a new thread. */
data class PostDraftContent(val title: String, val name: String, val content: String) {
    val hasContent: Boolean
        get() = title.isNotBlank() || name.isNotBlank() || content.isNotBlank()

    fun toDraft(boardId: String, boardName: String): DraftEntity = DraftEntity(
        type = DraftEntity.TYPE_POST, targetId = boardId, targetName = boardName,
        title = title, name = name, content = content
    )

    companion object {
        fun from(draft: DraftEntity) = PostDraftContent(draft.title.orEmpty(), draft.name.orEmpty(), draft.content)
    }
}
