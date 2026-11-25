package com.mioo.dao.ui.screens.thread

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withTimeoutOrNull
import com.mioo.dao.data.model.Thread

internal fun Thread.isUsefulJumpContext(threadId: String): Boolean =
    idStr == threadId && !replies.isNullOrEmpty()

/** Only publish settled neighbours: cache alone may predate new replies on the old tail. */
internal suspend fun <T> jumpPageContext(source: Flow<T>, timeoutMillis: Long = 400): T? =
    withTimeoutOrNull(timeoutMillis) { source.lastOrNull() }
