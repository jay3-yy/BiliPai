package com.android.purebilibili.feature.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertTrue

class DownloadPersistencePolicyTest {

    @Test
    fun normalizeRestoredDownloadTask_requeuesInterruptedActiveStates() {
        assertEquals(
            DownloadStatus.QUEUED,
            normalizeRestoredDownloadTask(baseTask.copy(status = DownloadStatus.QUEUED)).status
        )
        assertEquals(
            DownloadStatus.QUEUED,
            normalizeRestoredDownloadTask(baseTask.copy(status = DownloadStatus.PENDING)).status
        )
        assertEquals(
            DownloadStatus.QUEUED,
            normalizeRestoredDownloadTask(baseTask.copy(status = DownloadStatus.DOWNLOADING)).status
        )
        assertEquals(
            DownloadStatus.QUEUED,
            normalizeRestoredDownloadTask(baseTask.copy(status = DownloadStatus.MERGING)).status
        )
    }

    @Test
    fun normalizeRestoredDownloadTask_keepsStableAndUserPausedStatesUntouched() {
        val completedTask = baseTask.copy(status = DownloadStatus.COMPLETED)
        val pausedTask = baseTask.copy(status = DownloadStatus.PAUSED)

        assertEquals(completedTask, normalizeRestoredDownloadTask(completedTask))
        assertEquals(pausedTask, normalizeRestoredDownloadTask(pausedTask))
    }

    @Test
    fun restoreKeepsWaitingTaskAndRemainingRetryBudget() {
        val running = baseTask.copy(status = DownloadStatus.DOWNLOADING, workToken = "transfer")
        val firstRetry = assertNotNull(running.deferAutomaticRetry(nowMs = 1_000L))
        val restored = normalizeRestoredDownloadTask(
            Json.decodeFromString<DownloadTask>(Json.encodeToString(firstRetry)),
        )
        assertEquals(31_000L, restored.retryNotBeforeMs)
        assertTrue(resolveNextQueuedDownloadTaskIds(listOf(restored)).isEmpty())

        val ready = assertNotNull(restored.makeRetryReady("transfer")).copy(status = DownloadStatus.DOWNLOADING)
        val secondRetry = assertNotNull(ready.deferAutomaticRetry(nowMs = 31_000L))
        assertEquals(91_000L, secondRetry.retryNotBeforeMs)
        val lastAttempt = assertNotNull(secondRetry.makeRetryReady("transfer")).copy(status = DownloadStatus.DOWNLOADING)
        assertNull(lastAttempt.deferAutomaticRetry(nowMs = 91_000L))
    }

    @Test
    fun normalizeRestoredDownloadTask_sanitizesNonFiniteProgressValues() {
        val normalized = normalizeRestoredDownloadTask(
            baseTask.copy(
                progress = Float.POSITIVE_INFINITY,
                videoProgress = 0.4f,
                audioProgress = 0.6f
            )
        )

        assertFalse(normalized.progress.isInfinite())
        assertEquals(0.5f, normalized.progress)
        assertEquals(0.4f, normalized.videoProgress)
        assertEquals(0.6f, normalized.audioProgress)
    }

    @Test
    fun shouldPersistDownloadTaskUpdate_ignoresProgressOnlyChanges() {
        val updated = baseTask.copy(progress = 0.5f, videoProgress = 0.4f, audioProgress = 0.6f)

        assertFalse(shouldPersistDownloadTaskUpdate(baseTask, updated))
    }

    @Test
    fun shouldPersistDownloadTaskUpdate_persistsStatusAndFileChanges() {
        assertTrue(
            shouldPersistDownloadTaskUpdate(
                baseTask,
                baseTask.copy(status = DownloadStatus.COMPLETED, filePath = "/tmp/video.mp4")
            )
        )
    }

    @Test
    fun shouldPersistDownloadTaskUpdate_persistsOptionsAndDanmakuAssets() {
        assertTrue(
            shouldPersistDownloadTaskUpdate(
                baseTask,
                baseTask.copy(options = DownloadOptions(includeDanmaku = false))
            )
        )
        assertTrue(
            shouldPersistDownloadTaskUpdate(
                baseTask,
                baseTask.copy(localDanmakuSegmentPaths = listOf("/tmp/seg.pb"))
            )
        )
    }

    private val baseTask = DownloadTask(
        bvid = "BV1persist",
        cid = 9L,
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
