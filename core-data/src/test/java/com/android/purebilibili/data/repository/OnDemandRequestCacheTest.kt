package com.android.purebilibili.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandRequestCacheTest {
    @Test
    fun concurrentConsumersShareOneRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val cache = OnDemandRequestCache<String, String>(2, 1000L, backgroundScope)
        val first = async {
            cache.getOrLoad("video") {
                calls += 1
                gate.await()
                "details"
            }
        }
        val second = async {
            cache.getOrLoad("video") {
                calls += 1
                "duplicate"
            }
        }
        runCurrent()
        assertEquals(1, calls)
        gate.complete(Unit)
        assertEquals("details", first.await())
        assertEquals("details", second.await())
    }

    @Test
    fun cancelingOneConsumerKeepsTheOtherRequestAlive() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val cache = OnDemandRequestCache<String, String>(2, 1000L, backgroundScope)
        val first = async {
            cache.getOrLoad("video") {
                calls += 1
                gate.await()
                "details"
            }
        }
        val second = async {
            cache.getOrLoad("video") {
                calls += 1
                "duplicate"
            }
        }
        runCurrent()
        first.cancelAndJoin()
        gate.complete(Unit)
        assertEquals("details", second.await())
        assertEquals(1, calls)
    }

    @Test
    fun cancelingTheLastConsumerCancelsNetworkWork() = runTest {
        val stopped = CompletableDeferred<Unit>()
        val cache = OnDemandRequestCache<String, String>(2, 1000L, backgroundScope)
        val request = async {
            cache.getOrLoad("video") {
                try {
                    awaitCancellation()
                } finally {
                    stopped.complete(Unit)
                }
            }
        }
        runCurrent()
        request.cancelAndJoin()
        runCurrent()
        assertTrue(stopped.isCompleted)
        assertEquals("retry", cache.getOrLoad("video") { "retry" })
    }

    @Test
    fun onlySuccessfulResultsAreCached() = runTest {
        var calls = 0
        val cache = OnDemandRequestCache<String, Result<String>>(
            maxEntries = 2,
            maxAgeMs = 1000L,
            scope = backgroundScope,
            canCache = { it.isSuccess }
        )
        val failed = cache.getOrLoad("video") {
            calls += 1
            Result.failure(IllegalStateException())
        }
        assertTrue(failed.isFailure)
        val success = cache.getOrLoad("video") {
            calls += 1
            Result.success("details")
        }
        assertEquals("details", success.getOrThrow())
        assertEquals("details", cache.getOrLoad("video") { error("Must use cache") }.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun entriesExpireAtTheirDeadline() = runTest {
        val cache = OnDemandRequestCache<String, String>(
            maxEntries = 2,
            maxAgeMs = 1000L,
            scope = backgroundScope,
            nowMs = { testScheduler.currentTime }
        )
        assertEquals("old", cache.getOrLoad("video") { "old" })
        advanceTimeBy(999L)
        assertEquals("old", cache.peek("video"))
        advanceTimeBy(1L)
        assertNull(cache.peek("video"))
        assertEquals("new", cache.getOrLoad("video") { "new" })
    }

    @Test
    fun leastRecentlyUsedEntryIsEvicted() = runTest {
        val cache = OnDemandRequestCache<String, String>(2, 1000L, backgroundScope)
        cache.getOrLoad("first") { "first" }
        cache.getOrLoad("second") { "second" }
        assertEquals("first", cache.peek("first"))
        cache.getOrLoad("third") { "third" }
        assertNull(cache.peek("second"))
        assertEquals("first", cache.peek("first"))
        assertEquals("third", cache.peek("third"))
    }

    @Test
    fun differentAccountsNeverShareAnEntry() = runTest {
        val cache = OnDemandRequestCache<String, String>(2, 1000L, backgroundScope)
        assertEquals("first-user", cache.getOrLoad("account-one:video") { "first-user" })
        assertEquals("second-user", cache.getOrLoad("account-two:video") { "second-user" })
    }

    @Test
    fun zeroTtlSharesOnlyInFlightWork() = runTest {
        var calls = 0
        val cache = OnDemandRequestCache<String, String>(2, 0L, backgroundScope)
        cache.getOrLoad("video") {
            calls += 1
            "first"
        }
        cache.getOrLoad("video") {
            calls += 1
            "second"
        }
        assertEquals(2, calls)
        assertNull(cache.peek("video"))
    }

    @Test
    fun clearingCacheDoesNotCancelAnActiveRequestOrRepopulateIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val cache = OnDemandRequestCache<String, String>(2, 1000L, backgroundScope)
        val request = async { cache.getOrLoad("video") { gate.await(); "details" } }
        runCurrent()
        cache.clearCompleted()
        gate.complete(Unit)
        assertEquals("details", request.await())
        assertNull(cache.peek("video"))
    }

    @Test
    fun completedUncachedEntryIsNotReusedBeforePreviousWaiterFinalizes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val unconfined = UnconfinedTestDispatcher(testScheduler)
        var calls = 0
        val cache = OnDemandRequestCache<String, String>(
            maxEntries = 2,
            maxAgeMs = 0L,
            scope = CoroutineScope(backgroundScope.coroutineContext + unconfined),
        )
        val first = async {
            cache.getOrLoad("video") {
                calls += 1
                gate.await()
                "first"
            }
        }
        runCurrent()
        assertEquals(1, calls)

        // Completing the gate finishes the shared Deferred immediately on UnconfinedTestDispatcher
        // while `first` is still queued on StandardTestDispatcher (its `finally` has not run yet).
        gate.complete(Unit)
        assertFalse(first.isCompleted)

        val second = async(unconfined) {
            cache.getOrLoad("video") {
                calls += 1
                "second"
            }
        }

        assertEquals("second", second.await())
        assertEquals("first", first.await())
        assertEquals(2, calls)
    }

    @Test
    fun invalidationCancelsAnActiveRequestBeforeReload() = runTest {
        val gate = CompletableDeferred<Unit>()
        val cache = OnDemandRequestCache<String, String>(2, 1000L, backgroundScope)
        val request = async { cache.getOrLoad("video") { gate.await(); "old" } }
        runCurrent()
        cache.invalidate("video")
        runCurrent()
        assertTrue(request.isCancelled)
        assertEquals("new", cache.getOrLoad("video") { "new" })
    }
}
