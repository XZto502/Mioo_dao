package com.mioo.dao.data.repository

import com.mioo.dao.data.model.XdResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Bounded access-ordered in-memory LRU cache and in-flight request deduplicator for fetched
 * reference posts (`Success` responses only).
 */
internal class RefPostMemoryCache<T>(
    private val scope: CoroutineScope,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val flowContext: CoroutineContext = EmptyCoroutineContext
) {
    init {
        require(maxEntries >= 0) { "maxEntries must be non-negative" }
    }

    private val lock = Any()
    private var generation = 0L
    private val cache = object : LinkedHashMap<String, T>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?): Boolean {
            return size > maxEntries
        }
    }
    private val inFlight = HashMap<String, Deferred<XdResponse<T>>>()

    fun getOrFetch(
        id: String,
        fetcher: suspend (String) -> XdResponse<T>
    ): Flow<XdResponse<T>> = flow {
        val (hit, deferred) = synchronized(lock) {
            val cached = cache[id]
            if (cached != null) {
                cached to null
            } else {
                val existing = inFlight[id]
                if (existing != null) {
                    null to existing
                } else {
                    val fetchGeneration = generation
                    lateinit var job: Deferred<XdResponse<T>>
                    job = scope.async(start = CoroutineStart.LAZY) {
                        try {
                            val result = fetcher(id)
                            if (result is XdResponse.Success) {
                                synchronized(lock) {
                                    if (generation == fetchGeneration && maxEntries > 0) {
                                        cache[id] = result.data
                                    }
                                }
                            }
                            result
                        } finally {
                            synchronized(lock) {
                                if (inFlight[id] === job) {
                                    inFlight.remove(id)
                                }
                            }
                        }
                    }
                    inFlight[id] = job
                    null to job
                }
            }
        }

        if (hit != null) {
            emit(XdResponse.Success(hit))
            return@flow
        }
        emit(deferred!!.await())
    }.let { flow ->
        if (flowContext == EmptyCoroutineContext) flow else flow.flowOn(flowContext)
    }

    fun clear() {
        synchronized(lock) {
            generation++
            cache.clear()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
