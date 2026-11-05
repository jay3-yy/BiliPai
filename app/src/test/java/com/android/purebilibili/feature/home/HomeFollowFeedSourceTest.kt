package com.android.purebilibili.feature.home

import com.android.purebilibili.data.repository.DynamicFeedFetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class HomeFollowFeedSourceTest {
    @Test
    fun manualProbeWithUpdatesReplacesThePageAndKeepsProbeCount() = runTest {
        val calls = mutableListOf<Pair<Boolean, Boolean>>()
        val fullPage = DynamicFeedFetchResult(emptyList(), updateNum = 99)
        val source = HomeFollowFeedSource(baseline = { "known" }, loadFeed = { refresh, incremental ->
            calls += refresh to incremental
            Result.success(if (incremental) DynamicFeedFetchResult(emptyList(), updateNum = 3, usedUpdateBaseline = true) else fullPage)
        })
        val response = source.load(false, true, false) {}.getOrThrow()
        assertEquals(listOf(true to true, true to false), calls)
        assertTrue(response.forceReplace)
        assertSame(fullPage, response.feed)
        assertEquals(3, response.refreshUpdateNum)
    }

    @Test
    fun unchangedBaselineDoesNotFetchAReplacementPage() = runTest {
        var calls = 0
        val source = HomeFollowFeedSource(baseline = { "known" }, loadFeed = { _, incremental ->
            calls++
            assertTrue(incremental)
            Result.success(DynamicFeedFetchResult(emptyList(), updateNum = 0, usedUpdateBaseline = true))
        })
        val response = source.load(false, true, false) {}.getOrThrow()
        assertEquals(1, calls)
        assertFalse(response.forceReplace)
        assertEquals(0, response.refreshUpdateNum)
    }

    @Test
    fun paginationDoesNotProbeOrResetTheTimeline() = runTest {
        val source = HomeFollowFeedSource(baseline = { fail("pagination must not read the refresh baseline") }, loadFeed = { refresh, incremental ->
            assertFalse(refresh)
            assertFalse(incremental)
            Result.success(DynamicFeedFetchResult(emptyList()))
        })
        assertFalse(source.load(true, false, true) {}.getOrThrow().forceReplace)
    }

    @Test
    fun accountChangeAfterProbePreventsTheFollowupRequest() = runTest {
        var calls = 0
        var checks = 0
        val source = HomeFollowFeedSource(baseline = { "known" }, loadFeed = { _, _ ->
            calls++
            Result.success(DynamicFeedFetchResult(emptyList(), updateNum = 3))
        })
        try {
            source.load(false, true, false) {
                if (++checks == 2) throw CancellationException("account changed")
            }
            fail("expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
        }
    }

    @Test
    fun replacementFailureDoesNotExposeThePartialProbeAsContent() = runTest {
        val failure = IllegalStateException("full page failed")
        val source = HomeFollowFeedSource(baseline = { "known" }, loadFeed = { _, incremental ->
            if (incremental) Result.success(DynamicFeedFetchResult(emptyList(), updateNum = 3)) else Result.failure(failure)
        })
        assertSame(failure, source.load(false, true, false) {}.exceptionOrNull())
    }
}
