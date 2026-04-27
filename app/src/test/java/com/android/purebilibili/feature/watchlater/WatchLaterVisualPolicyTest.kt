package com.android.purebilibili.feature.watchlater

import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchLaterVisualPolicyTest {

    private fun item(
        title: String,
        ownerName: String = "测试UP",
        duration: Int = 600,
        progress: Int = -1,
        viewAt: Long = 0L
    ): VideoItem {
        return VideoItem(
            bvid = "BV1",
            cid = 101L,
            title = title,
            owner = Owner(name = ownerName),
            duration = duration,
            progress = progress,
            view_at = viewAt
        )
    }

    @Test
    fun `filterWatchLaterItemsByQuery returns all items for blank query`() {
        val items = listOf(
            item(title = "第一个视频"),
            item(title = "第二个视频", ownerName = "另一个UP")
        )

        assertEquals(items, filterWatchLaterItemsByQuery(items, ""))
    }

    @Test
    fun `filterWatchLaterItemsByQuery matches title and owner`() {
        val items = listOf(
            item(title = "猫咪视频", ownerName = "小明"),
            item(title = "狗狗视频", ownerName = "测试UP")
        )

        assertEquals(listOf(items[0]), filterWatchLaterItemsByQuery(items, "猫咪"))
        assertEquals(listOf(items[1]), filterWatchLaterItemsByQuery(items, "测试UP"))
    }

    @Test
    fun `resolveWatchLaterPlaybackVisualState marks completed videos`() {
        val visualState = resolveWatchLaterPlaybackVisualState(
            video = item(progress = -1, viewAt = 1L),
            localPositionMs = 0L
        )

        assertTrue(visualState.isCompleted)
        assertEquals("已看完", visualState.durationBadgeText)
    }

    @Test
    fun `resolveWatchLaterPlaybackVisualState keeps in-progress videos incomplete`() {
        val visualState = resolveWatchLaterPlaybackVisualState(
            video = item(progress = 120, viewAt = 1L),
            localPositionMs = 0L
        )

        assertFalse(visualState.isCompleted)
        assertEquals("已播放 20%", visualState.durationBadgeText)
    }
}
