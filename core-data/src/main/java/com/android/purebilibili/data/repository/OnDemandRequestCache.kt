package com.android.purebilibili.data.repository

import androidx.collection.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class OnDemandRequestCache<Key : Any, Value>(
    private val maxEntries: Int,
    private val maxAgeMs: Long,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val canCache: (Value) -> Boolean = { true }
) {
    private data class Cached<Value>(val value: Value, val expiresAtMs: Long)
    private class InFlight<Value> {
        lateinit var deferred: Deferred<Value>
        var waiters: Int = 0
    }

    private val lock = Any()
    private val cached = LruCache<Key, Cached<Value>>(maxEntries)
    private val inFlight = mutableMapOf<Key, InFlight<Value>>()
    private var cacheGeneration = 0L

    init {
        require(maxEntries > 0)
        require(maxAgeMs >= 0L)
    }

    fun peek(key: Key): Value? = synchronized(lock) {
        val entry = cached.get(key) ?: return@synchronized null
        if (nowMs() >= entry.expiresAtMs) {
            cached.remove(key)
            null
        } else entry.value
    }

    suspend fun getOrLoad(key: Key, load: suspend () -> Value): Value {
        currentCoroutineContext().ensureActive()
        val request = synchronized(lock) {
            val entry = cached.get(key)
            if (entry != null && nowMs() < entry.expiresAtMs) return entry.value
            cached.remove(key)
            val existing = inFlight[key]?.takeIf { !it.deferred.isCompleted }
            val selected = existing ?: InFlight<Value>().also { created ->
                val generation = cacheGeneration
                created.deferred = scope.async(start = CoroutineStart.LAZY) {
                    try {
                        val value = load()
                        currentCoroutineContext().ensureActive()
                        synchronized(lock) {
                            if (inFlight[key] === created) {
                                if (maxAgeMs > 0L && canCache(value) && cacheGeneration == generation) {
                                    cached.put(key, Cached(value, nowMs() + maxAgeMs))
                                }
                                inFlight.remove(key)
                            }
                        }
                        value
                    } finally {
                        synchronized(lock) {
                            if (inFlight[key] === created) {
                                inFlight.remove(key)
                            }
                        }
                    }
                }
                inFlight[key] = created
            }
            selected.waiters += 1
            selected
        }
        return try {
            val value = request.deferred.await()
            currentCoroutineContext().ensureActive()
            value
        } finally {
            synchronized(lock) {
                request.waiters -= 1
                if (request.waiters == 0) {
                    if (inFlight[key] === request) inFlight.remove(key)
                    if (!request.deferred.isCompleted) request.deferred.cancel()
                }
            }
        }
    }

    fun invalidate(key: Key) {
        synchronized(lock) {
            cached.remove(key)
            inFlight.remove(key)?.deferred?.cancel()
        }
    }

    fun clearCompleted() {
        synchronized(lock) {
            cached.evictAll()
            cacheGeneration += 1L
        }
    }

    fun invalidateWhere(matches: (Key) -> Boolean) {
        synchronized(lock) {
            cached.snapshot().keys.filter(matches).forEach { key -> cached.remove(key) }
            inFlight.keys.filter(matches).forEach { key ->
                inFlight.remove(key)?.deferred?.cancel()
            }
        }
    }
}
