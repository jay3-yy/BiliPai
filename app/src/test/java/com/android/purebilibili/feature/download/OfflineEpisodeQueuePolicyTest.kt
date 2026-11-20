package com.android.purebilibili.feature.download

import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineEpisodeQueuePolicyTest {

    @Test
    fun resolveOfflineEpisodeQueue_prefersSharedGroupKeyAcrossDifferentBvids() {
        val first = completedTask(
            taskIdSuffix = "ep1",
            bvid = "BVep1",
            cid = 101L,
            groupKey = "ugc:42",
            episodeSortIndex = 1
        )
        val second = completedTask(
            taskIdSuffix = "ep2",
            bvid = "BVep2",
            cid = 102L,
            groupKey = "ugc:42",
            episodeSortIndex = 2
        )
        val other = completedTask(
            taskIdSuffix = "other",
            bvid = "BVother",
            cid = 201L,
            groupKey = "ugc:100",
            episodeSortIndex = 1
        )

        val queue = resolveOfflineEpisodeQueue(
            tasks = listOf(second, other, first),
            currentTask = second
        )

        assertEquals(listOf(first.id, second.id), queue.map { it.id })
    }

    @Test
    fun resolveOfflineEpisodeQueue_fallsBackToSameBvidAndPageLabelOrdering() {
        val second = completedTask(
            taskIdSuffix = "page2",
            bvid = "BVsame",
            cid = 102L,
            episodeLabel = "P2 第二集"
        )
        val first = completedTask(
            taskIdSuffix = "page1",
            bvid = "BVsame",
            cid = 101L,
            episodeLabel = "P1 第一集"
        )

        val queue = resolveOfflineEpisodeQueue(
            tasks = listOf(second, first),
            currentTask = second
        )

        assertEquals(listOf(first.id, second.id), queue.map { it.id })
    }

    @Test
    fun playbackCompletionUsesCachedEpisodesWithoutTreatingDuplicateQualitiesAsNext() {
        val first = completedTask("page1", "BVsame", 101L, episodeSortIndex = 1)
        val second = completedTask("page2", "BVsame", 102L, episodeSortIndex = 2)
        val otherQuality = first.copy(quality = 64, createdAt = first.createdAt + 1)
        val queue = resolveOfflineEpisodeQueue(listOf(otherQuality, second, first), first)

        assertEquals(listOf(first.id, second.id), queue.map { it.id })
        assertEquals(
            second,
            resolveOfflineEpisodeAfterPlayback(queue, 0, PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC, true),
        )
        assertNull(resolveOfflineEpisodeAfterPlayback(queue, 0, PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC, false))
        assertNull(resolveOfflineEpisodeAfterPlayback(queue, 0, PlaybackCompletionBehavior.STOP_AFTER_CURRENT, true))
        assertNull(resolveOfflineEpisodeAfterPlayback(queue, 1, PlaybackCompletionBehavior.PLAY_IN_ORDER, true))
        assertEquals(first, resolveOfflineEpisodeAfterPlayback(queue, 1, PlaybackCompletionBehavior.LOOP_PLAYLIST, true))
    }

    private fun completedTask(
        taskIdSuffix: String,
        bvid: String,
        cid: Long,
        groupKey: String? = null,
        episodeSortIndex: Int = 0,
        episodeLabel: String? = null
    ): DownloadTask {
        val file = File.createTempFile(taskIdSuffix, ".mp4").apply { deleteOnExit() }
        return DownloadTask(
            bvid = bvid,
            cid = cid,
            title = "离线视频",
            episodeLabel = episodeLabel,
            groupKey = groupKey,
            episodeSortIndex = episodeSortIndex,
            cover = "cover",
            ownerName = "UP",
            ownerFace = "",
            duration = 120,
            quality = 80,
            qualityDesc = "1080P",
            videoUrl = "",
            audioUrl = "",
            status = DownloadStatus.COMPLETED,
            progress = 1f,
            filePath = file.absolutePath
        )
    }
}
