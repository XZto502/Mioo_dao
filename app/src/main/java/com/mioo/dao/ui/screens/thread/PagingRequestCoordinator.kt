package com.mioo.dao.ui.screens.thread

import kotlinx.coroutines.Job

/** Tracks the request allowed to publish paging state. Called on the main thread. */
internal class PagingRequestCoordinator {
    var generation: Int = 0
        private set
    private var job: Job? = null

    fun attach(requestGeneration: Int, requestJob: Job) {
        if (requestGeneration == generation) job = requestJob else requestJob.cancel()
    }

    fun begin(): Int {
        generation++
        job?.cancel()
        job = null
        return generation
    }
}
