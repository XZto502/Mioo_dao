package com.mioo.dao.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes the one-time migration and retries a failed flag write on the next call. */
internal class DraftMigrationCoordinator(
    private val isComplete: suspend () -> Boolean,
    private val migrate: suspend () -> Unit,
    private val markComplete: suspend () -> Unit
) {
    private val mutex = Mutex()
    private var complete = false

    suspend fun ensureMigrated() = mutex.withLock {
        if (complete) return@withLock
        if (!isComplete()) {
            migrate()
            markComplete()
        }
        complete = true
    }
}
