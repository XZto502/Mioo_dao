package com.mioo.dao.ui.components

/**
 * Common data model representing a post (either a thread header or a reply).
 */
data class PostData(
    val id: String,
    val title: String? = null,
    val userName: String? = null,
    val userId: String,
    val createdAt: String,
    val content: String,
    val imageUrl: String? = null,
    val isPo: Boolean = false,
    val isAdmin: Boolean = false,
    val isSage: Boolean = false,
    val resto: String? = null
)
