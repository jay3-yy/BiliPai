package com.android.purebilibili.feature.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedRequestCoordinatorTest {
    private val recommend = homeFeedRequestTarget(HomeCategory.RECOMMEND, PopularSubCategory.COMPREHENSIVE)
    private val following = homeFeedRequestTarget(HomeCategory.FOLLOW, PopularSubCategory.COMPREHENSIVE)

    @Test
    fun memoryRecoveryWaitsForCancelledStartupCleanupBeforePublishingSavedContent() = runTest {
        val coordinator = HomeFeedRequestCoordinator()
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        coordinator.start(recommend, HomeFeedRequestKind.INITIAL, { block -> launch { block() } }) {
            try { awaitCancellation() } finally {
                withContext(NonCancellable) { cleanup.await(); events += "startup cleaned" }
            }
        }
        runCurrent()
        // 新请求已替换旧请求的登记项，旧请求仍在取消清理中。
        coordinator.start(recommend, HomeFeedRequestKind.INITIAL, { block -> launch { block() } }) {
            error("replaced request must be cancelled before recovery")
        }
        runCurrent()
        val recovery = launch {
            coordinator.cancelAndJoinForMemoryRecovery()
            events += "saved content published"
        }
        runCurrent()
        assertFalse(recovery.isCompleted)
        cleanup.complete(Unit)
        recovery.join()
        assertEquals(listOf("startup cleaned", "saved content published"), events)
    }

    @Test
    fun refreshWaitsForInitialCancellationCleanupBeforeSettingItsLoadingState() = runTest {
        val coordinator = HomeFeedRequestCoordinator()
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        coordinator.start(recommend, HomeFeedRequestKind.INITIAL, { block -> launch { block() } }) {
            events += "initial loading"
            try { awaitCancellation() } finally {
                withContext(NonCancellable) {
                    cleanup.await()
                    events += "initial cleared"
                }
            }
        }
        runCurrent()
        assertTrue(coordinator.start(recommend, HomeFeedRequestKind.REFRESH, { block -> launch { block() } }) {
            events += "refresh loading"
        })
        runCurrent()
        assertEquals(listOf("initial loading"), events)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("initial loading", "initial cleared", "refresh loading"), events)
    }

    @Test
    fun rapidInitialSwitchSkipsCancelledQueuedCategoryAndWaitsForSameCategoryCleanup() = runTest {
        val coordinator = HomeFeedRequestCoordinator()
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        coordinator.start(recommend, HomeFeedRequestKind.INITIAL, { block -> launch { block() } }) {
            events += "old recommend"
            try { awaitCancellation() } finally {
                withContext(NonCancellable) { cleanup.await(); events += "old cleared" }
            }
        }
        runCurrent()
        coordinator.start(following, HomeFeedRequestKind.INITIAL, { block -> launch { block() } }) { events += "following" }
        coordinator.start(recommend, HomeFeedRequestKind.INITIAL, { block -> launch { block() } }) { events += "new recommend" }
        runCurrent()
        assertEquals(listOf("old recommend"), events)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("old recommend", "old cleared", "new recommend"), events)
    }

    @Test
    fun refreshAndPaginationCannotDuplicateAnActiveRequestForTheSameTarget() = runTest {
        val coordinator = HomeFeedRequestCoordinator()
        val complete = CompletableDeferred<Unit>()
        val launcher: (suspend () -> Unit) -> Job = { block -> launch { block() } }
        assertTrue(coordinator.start(recommend, HomeFeedRequestKind.INITIAL, launcher) { complete.await() })
        assertFalse(coordinator.start(recommend, HomeFeedRequestKind.APPEND, launcher) { error("duplicate pagination") })
        assertTrue(coordinator.start(recommend, HomeFeedRequestKind.REFRESH, launcher) { complete.await() })
        assertFalse(coordinator.start(recommend, HomeFeedRequestKind.INITIAL, launcher) { error("duplicate initial load") })
        assertFalse(coordinator.start(recommend, HomeFeedRequestKind.REFRESH, launcher) { error("duplicate refresh") })
        assertFalse(coordinator.start(recommend, HomeFeedRequestKind.APPEND, launcher) { error("duplicate pagination") })
        complete.complete(Unit)
        runCurrent()
        assertTrue(coordinator.start(recommend, HomeFeedRequestKind.APPEND, launcher) {})
    }

    @Test
    fun cancelledBeforeSchedulingReleasesTheInteractionAndNeverRunsItsBody() = runTest {
        val coordinator = HomeFeedRequestCoordinator()
        lateinit var cancelled: Job
        coordinator.start(recommend, HomeFeedRequestKind.REFRESH, { block ->
            launch(start = CoroutineStart.LAZY) { block() }.also { cancelled = it }
        }) { error("cancelled request executed") }
        cancelled.cancel()
        cancelled.join()
        assertTrue(coordinator.start(recommend, HomeFeedRequestKind.REFRESH, { block -> launch { block() } }) {})
    }

    @Test
    fun loadingAnotherCategoryDoesNotWaitForAnUnrelatedRefreshOrCancelIt() = runTest {
        val coordinator = HomeFeedRequestCoordinator()
        val complete = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        coordinator.start(recommend, HomeFeedRequestKind.REFRESH, { block -> launch { block() } }) {
            events += "refresh started"
            complete.await()
            events += "refresh finished"
        }
        runCurrent()
        coordinator.start(following, HomeFeedRequestKind.INITIAL, { block -> launch { block() } }) { events += "following loaded" }
        runCurrent()
        assertEquals(listOf("refresh started", "following loaded"), events)
        complete.complete(Unit)
        runCurrent()
        assertEquals("refresh finished", events.last())
    }

    @Test
    fun ordinaryCategoryIdentityIgnoresPopularSelectionWhilePopularSubcategoriesStayIndependent() {
        assertEquals(recommend, homeFeedRequestTarget(HomeCategory.RECOMMEND, PopularSubCategory.RANKING))
        assertFalse(
            homeFeedRequestTarget(HomeCategory.POPULAR, PopularSubCategory.COMPREHENSIVE) ==
                homeFeedRequestTarget(HomeCategory.POPULAR, PopularSubCategory.RANKING),
        )
    }
}
