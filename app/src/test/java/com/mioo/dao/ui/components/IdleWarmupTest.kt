package com.mioo.dao.ui.components

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleWarmupTest {

    @Test
    fun returnsAfterIdleMsWhenAlreadyIdle() = runBlocking {
        val busy = MutableStateFlow(false)
        val job = async { awaitContinuousIdle(busy, 40L) }
        delay(80L)
        assertTrue(job.isCompleted)
        job.await()
    }

    @Test
    fun doesNotReturnWhileBusy() = runBlocking {
        val busy = MutableStateFlow(true)
        val job = async { awaitContinuousIdle(busy, 40L) }
        delay(80L)
        assertFalse(job.isCompleted)
        busy.value = false
        delay(80L)
        assertTrue(job.isCompleted)
        job.await()
    }

    @Test
    fun resetsIdleTimerWhenBusyFlipsTrueBeforeIdleMs() = runBlocking {
        val busy = MutableStateFlow(false)
        val job = async { awaitContinuousIdle(busy, 60L) }
        delay(30L)
        busy.value = true
        delay(20L)
        assertFalse(job.isCompleted)
        busy.value = false
        delay(30L)
        assertFalse(job.isCompleted)
        delay(60L)
        assertTrue(job.isCompleted)
        job.await()
    }
}
