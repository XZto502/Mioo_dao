package com.mioo.dao.ui.components

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Suspends until [isBusy] remains false continuously for at least [idleMs]. */
internal suspend fun awaitContinuousIdle(isBusy: Flow<Boolean>, idleMs: Long) {
    while (true) {
        isBusy.first { !it }
        val becameBusy = withTimeoutOrNull(idleMs) {
            isBusy.first { it }
            true
        }
        if (becameBusy == null) return
    }
}
