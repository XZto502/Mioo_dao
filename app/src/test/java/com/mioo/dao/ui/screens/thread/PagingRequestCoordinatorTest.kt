package com.mioo.dao.ui.screens.thread

import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class PagingRequestCoordinatorTest {
    @Test fun `new request invalidates the old generation and cancels its job`() {
        val coordinator = PagingRequestCoordinator()
        val first = coordinator.begin()
        val job = Job()
        coordinator.attach(first, job)

        val second = coordinator.begin()

        assertTrue(job.isCancelled)
        assertNotEquals(first, second)
        assertEquals(second, coordinator.generation)

    }
    @Test fun `stale attachment cannot replace the current job`() {
        val coordinator = PagingRequestCoordinator()
        val first = coordinator.begin()
        val second = coordinator.begin()
        val current = Job()
        coordinator.attach(second, current)
        val stale = Job()
        coordinator.attach(first, stale)
        assertTrue(stale.isCancelled)
        assertTrue(current.isActive)
        coordinator.begin()
        assertTrue(current.isCancelled)
    }
}
