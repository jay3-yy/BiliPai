package com.android.purebilibili.feature.video.playback.policy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineCountPollingTest {
    private val first = OnlineCountRequest(OnlineCountMediaKey("BV_first", 1L), 1L)
    private val second = OnlineCountRequest(OnlineCountMediaKey("BV_second", 2L), 2L)

    @Test
    fun requestRequiresVisibleCurrentMediaAndGeneration_inForeground() {
        assertEquals(first, visibleOnlineCountRequest(first, first, listOf(first.media), false))
        assertNull(visibleOnlineCountRequest(first, first, emptyList(), false))
        assertNull(visibleOnlineCountRequest(first, first, listOf(first.media), true))
        assertNull(visibleOnlineCountRequest(first, second, listOf(first.media), false))
        assertNull(visibleOnlineCountRequest(first, first.copy(requestToken = 2L), listOf(first.media), false))
        // 旧页面移除自身消费者，当前页面仍可继续刷新相同内容。
        assertEquals(first, visibleOnlineCountRequest(first, first, listOf(second.media, first.media), false))
    }

    @Test
    fun hiddenPageHasNoRequests_andRestoringVisibilityRefreshesImmediately() = runTest {
        val requests = MutableStateFlow<OnlineCountRequest?>(null)
        val loaded = mutableListOf<OnlineCountRequest>()
        val displayed = mutableListOf<String>()
        val job = launch {
            observeOnlineCountRequests(requests,
                load = { loaded += it; "0人正在看" },
                onCount = { _, count -> displayed += count },
                onFailure = { throw it },
            )
        }
        runCurrent()
        advanceTimeBy(90_000L)
        runCurrent()
        assertTrue(loaded.isEmpty())

        requests.value = first
        runCurrent()
        assertEquals(listOf(first), loaded)
        assertEquals(listOf("0人正在看"), displayed)
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(2, loaded.size)

        requests.value = null
        runCurrent()
        advanceTimeBy(90_000L)
        runCurrent()
        assertEquals(2, loaded.size)
        assertEquals(2, displayed.size)

        requests.value = first
        runCurrent()
        assertEquals(3, loaded.size)
        job.cancelAndJoin()
    }

    @Test
    fun contentSwitchDiscardsLateOldResponse_evenWhenTransportDoesNotCancelImmediately() = runTest {
        val requests = MutableStateFlow<OnlineCountRequest?>(first)
        val oldResponse = CompletableDeferred<String>()
        val displayed = mutableListOf<Pair<OnlineCountRequest, String>>()
        val job = launch {
            observeOnlineCountRequests(requests,
                load = { request ->
                    if (request == first) withContext(NonCancellable) { oldResponse.await() }
                    else "20人正在看"
                },
                onCount = { request, count -> displayed += request to count },
                onFailure = { throw it },
            )
        }
        runCurrent()
        requests.value = second
        runCurrent()
        oldResponse.complete("旧人数")
        runCurrent()
        assertEquals(listOf(second to "20人正在看"), displayed)
        job.cancelAndJoin()
    }

    @Test
    fun disablingDemandCancelsInflightRequest_withoutTurningCancellationIntoFailure() = runTest {
        val requests = MutableStateFlow<OnlineCountRequest?>(first)
        var cancelled = false
        var failures = 0
        val job = launch {
            observeOnlineCountRequests(requests,
                load = { try { awaitCancellation() } finally { cancelled = true } },
                onCount = { _, _ -> error("cancelled response must not publish") },
                onFailure = { failures++ },
            )
        }
        runCurrent()
        requests.value = null
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0, failures)
        job.cancelAndJoin()
    }

    @Test
    fun failedRefreshRetainsKnownCount_andNextRegularRefreshCanRecover() = runTest {
        val requests = MutableStateFlow<OnlineCountRequest?>(first)
        var calls = 0
        var failures = 0
        var displayed: String? = null
        val job = launch {
            observeOnlineCountRequests(requests,
                load = {
                    when (++calls) {
                        1 -> "9.4万+人正在看"
                        2 -> throw IllegalStateException("offline")
                        3 -> null
                        else -> "0人正在看"
                    }
                },
                onCount = { _, count -> displayed = count },
                onFailure = { failures++ },
            )
        }
        runCurrent()
        repeat(2) {
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals("9.4万+人正在看", displayed)
        }
        assertEquals(1, failures)
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals("0人正在看", displayed)
        job.cancelAndJoin()
    }
}
