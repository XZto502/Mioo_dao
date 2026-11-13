package com.mioo.dao.ui.screens.thread

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import com.mioo.dao.data.model.Thread

internal fun Thread.isUsefulJumpContext(threadId: String): Boolean =
    idStr == threadId && !replies.isNullOrEmpty()

/** Neighbour pages improve the reading window, but must not hold the target hostage. */
internal suspend fun <T> jumpPageContext(source: Flow<T>, timeoutMillis: Long = 400): T? =
    withTimeoutOrNull(timeoutMillis) { source.firstOrNull() }
