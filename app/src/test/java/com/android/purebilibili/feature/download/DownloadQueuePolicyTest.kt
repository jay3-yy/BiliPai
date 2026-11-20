package com.android.purebilibili.feature.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadQueuePolicyTest {

    @Test
    fun staleUrlRefresh_triggersAfterThreshold() {
        val createdAt = 1_000_000L
        val threshold = STALE_DOWNLOAD_URL_REFRESH_THRESHOLD_MS

        assertEquals(
            true,
            shouldRefreshStaleDownloadUrls(
                createdAtMs = createdAt,
                nowMs = createdAt + threshold
            )
        )
        assertEquals(
            false,
            shouldRefreshStaleDownloadUrls(
                createdAtMs = createdAt,
                nowMs = createdAt + threshold - 1L
            )
        )
        // 刚入队/时钟异常时不刷新
        assertEquals(
            false,
            shouldRefreshStaleDownloadUrls(createdAtMs = createdAt, nowMs = createdAt)
        )
        assertEquals(
            false,
            shouldRefreshStaleDownloadUrls(createdAtMs = createdAt, nowMs = createdAt - 1L)
        )
    }

    @Test
    fun activeSlots_fillUpToConcurrencyLimit() {
        val tasks = listOf(
            baseTask.copy(cid = 1L, status = DownloadStatus.DOWNLOADING, createdAt = 1L),
            baseTask.copy(cid = 2L, status = DownloadStatus.QUEUED, createdAt = 2L),
            baseTask.copy(cid = 3L, status = DownloadStatus.QUEUED, createdAt = 3L)
        )

        // 1 个活跃 + 并发 2 → 只补最老的 1 个
        val dispatched = resolveNextQueuedDownloadTaskIds(tasks)
        assertEquals(1, dispatched.size)
        assertEquals(baseTask.copy(cid = 2L).id, dispatched.first())
    }

    @Test
    fun idleQueue_dispatchesUpToLimitOldestFirst() {
        val tasks = listOf(
            baseTask.copy(cid = 1L, status = DownloadStatus.QUEUED, createdAt = 30L),
            baseTask.copy(cid = 2L, status = DownloadStatus.QUEUED, createdAt = 10L),
            baseTask.copy(cid = 3L, status = DownloadStatus.QUEUED, createdAt = 20L)
        )

        val dispatched = resolveNextQueuedDownloadTaskIds(tasks)
        assertEquals(DEFAULT_MAX_CONCURRENT_DOWNLOADS, dispatched.size)
        assertEquals(
            listOf(baseTask.copy(cid = 2L).id, baseTask.copy(cid = 3L).id),
            dispatched
        )
    }

    @Test
    fun atConcurrencyCapacity_returnsEmpty() {
        val tasks = listOf(
            baseTask.copy(cid = 1L, status = DownloadStatus.DOWNLOADING, createdAt = 1L),
            baseTask.copy(cid = 2L, status = DownloadStatus.MERGING, createdAt = 2L),
            baseTask.copy(cid = 3L, status = DownloadStatus.QUEUED, createdAt = 3L)
        )

        assertTrue(resolveNextQueuedDownloadTaskIds(tasks).isEmpty())
    }

    @Test
    fun pausedTasks_freeConcurrencySlots() {
        val tasks = listOf(
            baseTask.copy(cid = 1L, status = DownloadStatus.PAUSED, createdAt = 1L),
            baseTask.copy(cid = 2L, status = DownloadStatus.QUEUED, createdAt = 2L)
        )

        assertEquals(
            listOf(baseTask.copy(cid = 2L).id),
            resolveNextQueuedDownloadTaskIds(tasks)
        )
    }

    @Test
    fun networkBackoffReleasesSlotAndWakeupWaitsForCapacity() {
        val failedAttempt = baseTask.copy(status = DownloadStatus.DOWNLOADING, workToken = "transfer", createdAt = 1L)
        val queued = baseTask.copy(cid = 2L, status = DownloadStatus.QUEUED, createdAt = 2L)
        val waiting = assertNotNull(failedAttempt.deferAutomaticRetry(nowMs = 10_000L))
        assertEquals(listOf(queued.id), resolveNextQueuedDownloadTaskIds(listOf(waiting, queued), maxConcurrent = 1))
        assertTrue(shouldPauseAllInclude(waiting))

        assertNull(waiting.makeRetryReady("older-transfer"))
        assertNull(waiting.copy(status = DownloadStatus.PAUSED).makeRetryReady("transfer"))
        val ready = assertNotNull(waiting.makeRetryReady("transfer"))
        val running = queued.copy(status = DownloadStatus.DOWNLOADING)
        assertTrue(resolveNextQueuedDownloadTaskIds(listOf(ready, running), maxConcurrent = 1).isEmpty())
        assertEquals(
            listOf(ready.id),
            resolveNextQueuedDownloadTaskIds(listOf(ready, running.copy(status = DownloadStatus.COMPLETED)), maxConcurrent = 1),
        )
    }

    private val baseTask = DownloadTask(
        bvid = "BV1queue",
        cid = 1L,
        title = "缓存视频",
        cover = "cover",
        ownerName = "UP",
        ownerFace = "",
        duration = 120,
        quality = 80,
        qualityDesc = "1080P",
        videoUrl = "video",
        audioUrl = "audio"
    )
}
