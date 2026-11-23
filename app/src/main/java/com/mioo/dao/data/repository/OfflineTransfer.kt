package com.mioo.dao.data.repository

import android.os.SystemClock

data class OfflineTransfer(
    val stage: String = "等待下载",
    val completed: Int = 0,
    val total: Int = 0,
    val bytesPerSecond: Long = 0,
    val secondsRemaining: Long? = null,
    val error: String? = null
)

internal class OfflineTransferMeter(
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
    private val publish: (OfflineTransfer) -> Unit
) {
    private var started = 0L
    private var lastPublished = 0L
    private var bytes = 0L
    private var state = OfflineTransfer()

    fun start(stage: String, total: Int) {
        started = nowMs()
        bytes = 0
        state = OfflineTransfer(stage = stage, total = total)
        update()
    }

    fun setTotal(total: Int) {
        state = state.copy(total = total)
    }

    fun received(count: Long) {
        bytes += count
        if (nowMs() - lastPublished >= 500) update()
    }

    fun completeUnit() {
        state = state.copy(completed = state.completed + 1)
        update()
    }

    private fun update() {
        val now = nowMs()
        val elapsed = now - started
        lastPublished = now
        val rateReady = elapsed >= 500
        state = state.copy(
            bytesPerSecond = if (rateReady) bytes * 1000 / elapsed else 0,
            secondsRemaining = if (rateReady && state.total > 0 && state.completed > 0) {
                val remaining = state.total - state.completed
                if (remaining == 0) 0 else (elapsed * remaining / state.completed + 999) / 1000
            } else null
        )
        publish(state)
    }
}
