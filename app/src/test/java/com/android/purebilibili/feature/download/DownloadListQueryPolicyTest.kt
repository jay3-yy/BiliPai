package com.android.purebilibili.feature.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadListQueryPolicyTest {

    private fun sampleTask(
        bvid: String,
        cid: Long = 1L,
        title: String,
        ownerName: String = "测试UP",
        duration: Int = 120,
        quality: Int = 80,
        qualityDesc: String = "1080P",
        status: DownloadStatus = DownloadStatus.COMPLETED,
        fileSize: Long = 10_000L,
        downloadedSize: Long = 0L,
        createdAt: Long = 1_000L,
        episodeLabel: String? = null,
        groupTitle: String? = null,
        episodeSortIndex: Int = 0,
        isAudioOnly: Boolean = false,
        assets: List<DownloadAssetState> = emptyList(),
    ) = DownloadTask(
        bvid = bvid,
        cid = cid,
        title = title,
        episodeLabel = episodeLabel,
        groupTitle = groupTitle,
        episodeSortIndex = episodeSortIndex,
        cover = "",
        ownerName = ownerName,
        ownerFace = "",
        duration = duration,
        quality = quality,
        qualityDesc = qualityDesc,
        videoUrl = "",
        audioUrl = "",
        status = status,
        fileSize = fileSize,
        downloadedSize = downloadedSize,
        createdAt = createdAt,
        isAudioOnly = isAudioOnly,
        assets = assets,
    )

    @Test
    fun searchMatchesTitlePinyinOwnerEpisodeBvidQualityAndAudio() {
        val videoTask = sampleTask(
            bvid = "BV1AbC23456",
            title = "深度学习入门指南",
            ownerName = "李沐老师",
            episodeLabel = "第3讲 卷积神经网络",
            groupTitle = "动手学AI合集",
            qualityDesc = "4K 超清",
        )
        val audioTask = sampleTask(
            bvid = "BV1Audio999",
            title = "雨声白噪音",
            ownerName = "助眠电台",
            qualityDesc = "Hi-Res",
            isAudioOnly = true,
        )

        assertTrue(matchesDownloadTaskQuery(videoTask, "   "))
        assertTrue(matchesDownloadTaskQuery(videoTask, "深度"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "sdxx"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "shenduxuexi"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "limu"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "卷积"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "动手学"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "bv1abc"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "4k"))
        assertTrue(matchesDownloadTaskQuery(videoTask, "lm 4k"))
        assertFalse(matchesDownloadTaskQuery(videoTask, "lm 720p"))

        assertTrue(matchesDownloadTaskQuery(audioTask, "音频"))
        assertTrue(matchesDownloadTaskQuery(audioTask, "audio"))
        assertFalse(matchesDownloadTaskQuery(videoTask, "音频"))
    }

    @Test
    fun sortsByTimeSizeDurationTitleAndStatus() {
        val completedSmallOld = sampleTask(
            bvid = "BV1",
            title = "Beta 教程",
            duration = 600,
            status = DownloadStatus.COMPLETED,
            fileSize = 200L,
            createdAt = 100L,
        )
        val downloadingLarge = sampleTask(
            bvid = "BV2",
            title = "Alpha 概览",
            duration = 180,
            status = DownloadStatus.DOWNLOADING,
            fileSize = 0L,
            downloadedSize = 500L,
            createdAt = 200L,
            assets = listOf(
                DownloadAssetState(
                    kind = DownloadAssetKind.VIDEO,
                    status = DownloadAssetStatus.DOWNLOADING,
                    totalBytes = 1_000L,
                    downloadedBytes = 500L,
                )
            ),
        )
        val failedNewest = sampleTask(
            bvid = "BV3",
            title = "Gamma 实战",
            duration = 900,
            status = DownloadStatus.FAILED,
            fileSize = 0L,
            downloadedSize = 300L,
            createdAt = 300L,
        )
        val pausedMid = sampleTask(
            bvid = "BV4",
            title = "Alpha 概览",
            duration = 300,
            status = DownloadStatus.PAUSED,
            fileSize = 0L,
            downloadedSize = 50L,
            createdAt = 250L,
            episodeSortIndex = 2,
        )
        val all = listOf(completedSmallOld, downloadingLarge, failedNewest, pausedMid)

        assertEquals(
            listOf("BV3", "BV4", "BV2", "BV1"),
            filterAndSortDownloadTasks(all, "", DownloadListSortOrder.RECENT_FIRST).map { it.bvid },
        )
        assertEquals(
            listOf("BV1", "BV2", "BV4", "BV3"),
            filterAndSortDownloadTasks(all, "", DownloadListSortOrder.OLDEST_FIRST).map { it.bvid },
        )
        assertEquals(
            listOf("BV2", "BV3", "BV1", "BV4"),
            filterAndSortDownloadTasks(all, "", DownloadListSortOrder.SIZE_DESC).map { it.bvid },
        )
        assertEquals(
            listOf("BV3", "BV1", "BV4", "BV2"),
            filterAndSortDownloadTasks(all, "", DownloadListSortOrder.DURATION_DESC).map { it.bvid },
        )
        assertEquals(
            listOf("BV2", "BV4", "BV1", "BV3"),
            filterAndSortDownloadTasks(all, "", DownloadListSortOrder.TITLE_ASC).map { it.bvid },
        )
        assertEquals(
            listOf("BV2", "BV4", "BV3", "BV1"),
            filterAndSortDownloadTasks(all, "", DownloadListSortOrder.STATUS_FIRST).map { it.bvid },
        )
    }

    @Test
    fun sortOrderFromStorageKeyFallsBackToRecentFirst() {
        assertEquals(
            DownloadListSortOrder.SIZE_DESC,
            DownloadListSortOrder.fromStorageKey("size_desc"),
        )
        assertEquals(
            DownloadListSortOrder.RECENT_FIRST,
            DownloadListSortOrder.fromStorageKey("unknown_key"),
        )
        assertEquals(
            DownloadListSortOrder.RECENT_FIRST,
            DownloadListSortOrder.fromStorageKey(null),
        )
    }

    @Test
    fun selectionAllToggleOnlyAffectsVisibleCompletedItems() {
        val visible = setOf("a", "b")
        val initial = setOf("hidden", "a")

        val afterSelectAll = resolveDownloadSelectionAllToggle(visible, initial)
        assertEquals(setOf("hidden", "a", "b"), afterSelectAll)

        val afterClearVisible = resolveDownloadSelectionAllToggle(visible, afterSelectAll)
        assertEquals(setOf("hidden"), afterClearVisible)
    }

    @Test
    fun groupsMultiplePartsOfSameBvidInEpisodeOrderAndPreservesSingleItems() {
        val part2 = sampleTask(
            bvid = "BV_MULTI",
            cid = 202L,
            title = "周杰伦精选 - P2 以父之名",
            episodeLabel = "P2 以父之名",
            episodeSortIndex = 2,
            createdAt = 300L,
        )
        val solo = sampleTask(
            bvid = "BV_SOLO",
            cid = 301L,
            title = "独立视频",
            createdAt = 200L,
        )
        val part1 = sampleTask(
            bvid = "BV_MULTI",
            cid = 201L,
            title = "周杰伦精选 - P1 安静",
            episodeLabel = "P1 安静",
            episodeSortIndex = 1,
            createdAt = 100L,
        )

        val grouped = buildDownloadListEntries(listOf(part2, solo, part1), groupMultiPart = true)
        assertEquals(2, grouped.size)
        val firstGroup = grouped[0] as DownloadListEntry.MultiPartGroup
        assertEquals("BV_MULTI", firstGroup.bvid)
        assertEquals("周杰伦精选", firstGroup.groupTitle)
        assertEquals(listOf(201L, 202L), firstGroup.tasks.map { it.cid })
        assertEquals("BV_SOLO", (grouped[1] as DownloadListEntry.Single).task.bvid)

        val ungrouped = buildDownloadListEntries(listOf(part2, solo, part1), groupMultiPart = false)
        assertEquals(3, ungrouped.size)
        assertTrue(ungrouped.all { it is DownloadListEntry.Single })
    }
}
