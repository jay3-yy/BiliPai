package com.android.purebilibili.feature.video.state

import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoPlayerMetadataTest {
    @Test
    fun `engagement and playback detail updates do not republish media metadata`() = runTest {
        val initial = VideoPlaybackUiState.Success(
            info = ViewInfo(bvid = "BV1", cid = 1, title = "同名视频", pic = "https://example.com/cover.jpg"),
            playUrl = "https://example.com/video",
        )
        val events = flowOf<VideoPlaybackUiState>(
            initial,
            initial.copy(isLiked = true),
            initial.copy(currentQuality = 80),
            initial.copy(relatedLoadState = VideoPlaybackUiState.RelatedLoadState.LOADING),
            initial.copy(info = initial.info.copy(cid = 2)),
            initial.copy(info = initial.info.copy(bvid = "BV2")),
            initial.copy(info = initial.info.copy(title = "新标题")),
            initial.copy(info = initial.info.copy(pic = "https://example.com/new-cover.jpg")),
            VideoPlaybackUiState.Loading.Initial,
            initial,
        ).playerMetadataUpdates().toList()
        assertEquals(7, events.size)
        assertEquals(initial.playerMetadata(), events[0])
        assertEquals(2L, events[1]!!.cid)
        assertEquals("BV2", events[2]!!.bvid)
        assertEquals("新标题", events[3]!!.title)
        assertEquals("https://example.com/new-cover.jpg", events[4]!!.coverUrl)
        assertNull(events[5])
        assertEquals(initial.playerMetadata(), events[6])
    }
}
