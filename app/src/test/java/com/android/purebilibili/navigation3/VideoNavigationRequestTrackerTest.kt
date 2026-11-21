package com.android.purebilibili.navigation3

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoNavigationRequestTrackerTest {
    @Test
    fun allFourVideoDirectionPairsInvalidateTheParentCallbackAfterPush() {
        for (sourceVertical in listOf(false, true)) {
            for (targetVertical in listOf(false, true)) {
                val tracker = VideoNavigationRequestTracker()
                val parent = BiliPaiNavKey.VideoDetail(
                    bvid = "BVparent", initialVertical = sourceVertical,
                    fullscreen = true, openId = 1L,
                )
                val sourceStack = listOf(BiliPaiNavKey.MainHost, parent)
                val request = tracker.begin(sourceStack, sourceGeneration = 1L)
                assertTrue(tracker.isCurrent(request, sourceStack, sourceGeneration = 1L))

                val child = BiliPaiNavKey.VideoDetail(
                    bvid = "BVchild", initialVertical = targetVertical,
                    fullscreen = true, openId = 2L,
                )
                assertFalse(tracker.isCurrent(request, sourceStack + child, sourceGeneration = 2L))
            }
        }
    }

    @Test
    fun tenThousandOutOfOrderCompletionsOnlyAllowTheLatestRequest() {
        val tracker = VideoNavigationRequestTracker()
        val stack = listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.VideoDetail("BVparent", openId = 1L))
        val requests = List(10_000) { tracker.begin(stack, sourceGeneration = 1L) }
        for (request in requests.shuffled(Random(731))) {
            val allowed = tracker.isCurrent(request, stack, sourceGeneration = 1L)
            if (request === requests.last()) assertTrue(allowed) else assertFalse(allowed)
        }
    }

    @Test
    fun leavingAndReturningToTheSameTabDoesNotReviveThePendingRequest() {
        val tracker = VideoNavigationRequestTracker()
        val stack = listOf(BiliPaiNavKey.MainHost)
        val request = tracker.begin(stack, sourceGeneration = 1L)

        tracker.invalidate()

        assertFalse(tracker.isCurrent(request, stack, sourceGeneration = 1L))
        val next = tracker.begin(stack, sourceGeneration = 1L)
        assertTrue(tracker.isCurrent(next, stack, sourceGeneration = 1L))
    }

    @Test
    fun returningToAnEqualStackDoesNotReviveTheOldDirectionLookup() {
        val tracker = VideoNavigationRequestTracker()
        val stack = listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.VideoDetail("BVparent", openId = 1L))
        val request = tracker.begin(stack, sourceGeneration = 1L)

        assertFalse(tracker.isCurrent(request, listOf(BiliPaiNavKey.MainHost), sourceGeneration = 1L))
        assertFalse(tracker.isCurrent(request, stack, sourceGeneration = 2L))
        assertFalse(tracker.isCurrent(
            request,
            listOf(BiliPaiNavKey.MainHost, BiliPaiNavKey.VideoDetail("BVparent", openId = 3L)),
            sourceGeneration = 1L,
        ))
    }
}
