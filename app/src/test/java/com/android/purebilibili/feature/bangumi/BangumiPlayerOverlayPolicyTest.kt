package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.data.model.response.BangumiVideoInfo
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.FormatItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BangumiPlayerOverlayPolicyTest {

    @Test
    fun quickSeekAdvancesNinetySecondsFromEachCurrentPosition() {
        assertEquals(150_000L, resolveBangumiQuickSeekTargetPosition(60_000L, 1_440_000L))
        assertEquals(240_000L, resolveBangumiQuickSeekTargetPosition(150_000L, 1_440_000L))
    }

    @Test
    fun quickSeekStopsAtEpisodeEndAndWaitsForKnownDuration() {
        assertEquals(120_000L, resolveBangumiQuickSeekTargetPosition(100_000L, 120_000L))
        assertEquals(30_000L, resolveBangumiQuickSeekTargetPosition(0L, 30_000L))
        assertEquals(120_000L, resolveBangumiQuickSeekTargetPosition(120_000L, 120_000L))
        assertNull(resolveBangumiQuickSeekTargetPosition(0L, 0L))
        assertNull(resolveBangumiQuickSeekTargetPosition(0L, androidx.media3.common.C.TIME_UNSET))
    }

    @Test
    fun resolveBangumiOverlayQualityLabel_returnsMatchedLabel() {
        val label = resolveBangumiOverlayQualityLabel(
            currentQuality = 80,
            acceptQuality = listOf(120, 80, 64),
            acceptDescription = listOf("4K", "1080P", "720P")
        )

        assertEquals("1080P", label)
    }

    @Test
    fun resolveBangumiOverlayQualityLabel_fallsBackToAutoWhenQualityMissing() {
        val label = resolveBangumiOverlayQualityLabel(
            currentQuality = 32,
            acceptQuality = listOf(120, 80, 64),
            acceptDescription = listOf("4K", "1080P", "720P")
        )

        assertEquals("自动", label)
    }

    @Test
    fun resolveBangumiOverlaySwitchableQualityIds_filtersInvalidAndDuplicateValues() {
        val switchableQualityIds = resolveBangumiOverlaySwitchableQualityIds(
            acceptQuality = listOf(120, 80, 80, 64, 0, -1)
        )

        assertEquals(listOf(120, 80, 64), switchableQualityIds)
    }

    @Test
    fun resolveBangumiQualityOptions_mergesApiFormatAndDashMetadata() {
        val options = resolveBangumiQualityOptions(
            BangumiVideoInfo(
                acceptQuality = listOf(126, 120),
                acceptDescription = listOf("杜比视界", "4K"),
                supportFormats = listOf(
                    FormatItem(quality = 127, displayDesc = "8K", codecs = listOf("av01")),
                    FormatItem(quality = 125, newDescription = "HDR 真彩")
                ),
                dash = Dash(video = listOf(DashVideo(id = 116, codecs = "av01.0.08M.08")))
            )
        )

        assertEquals(listOf(127, 126, 125, 120, 116), options.ids)
        assertEquals(listOf("8K", "杜比视界", "HDR 真彩", "4K", "1080P 60帧"), options.labels)
    }

    @Test
    fun buildBangumiOverlayPages_convertsEpisodesInDisplayOrder() {
        val pages = buildBangumiOverlayPages(
            listOf(
                BangumiEpisode(
                    id = 101L,
                    cid = 1001L,
                    title = "第1话",
                    longTitle = "相遇",
                    duration = 1_420_000L
                ),
                BangumiEpisode(
                    id = 102L,
                    cid = 1002L,
                    title = "第2话",
                    longTitle = "启程",
                    duration = 1_380_000L
                )
            )
        )

        assertEquals(2, pages.size)
        assertEquals(1001L, pages[0].cid)
        assertEquals(1, pages[0].page)
        assertEquals("第1话 相遇", pages[0].part)
        assertEquals(1420L, pages[0].duration)
        assertEquals(1002L, pages[1].cid)
        assertEquals(2, pages[1].page)
        assertEquals("第2话 启程", pages[1].part)
        assertEquals(1380L, pages[1].duration)
    }

    @Test
    fun resolveBangumiOverlayCurrentPageIndex_matchesCurrentEpisode() {
        val episodes = listOf(
            BangumiEpisode(id = 101L, cid = 1001L, title = "第1话"),
            BangumiEpisode(id = 102L, cid = 1002L, title = "第2话"),
            BangumiEpisode(id = 103L, cid = 1003L, title = "第3话")
        )

        val pageIndex = resolveBangumiOverlayCurrentPageIndex(
            episodes = episodes,
            currentEpisodeId = 102L
        )

        assertEquals(1, pageIndex)
    }

    @Test
    fun resolveBangumiEpisodeForPageSelection_returnsEpisodeAtIndex() {
        val episodes = listOf(
            BangumiEpisode(id = 101L, cid = 1001L, title = "第1话"),
            BangumiEpisode(id = 102L, cid = 1002L, title = "第2话")
        )

        val selectedEpisode = resolveBangumiEpisodeForPageSelection(
            episodes = episodes,
            selectedPageIndex = 1
        )

        assertEquals(102L, selectedEpisode?.id)
    }

    @Test
    fun resolveBangumiUnsupportedOverlayActionMessage_returnsFriendlyHint() {
        assertEquals(
            "番剧暂不支持点赞操作",
            resolveBangumiUnsupportedOverlayActionMessage(BangumiOverlayUnsupportedAction.LIKE)
        )
        assertEquals(
            "番剧暂不支持点踩操作",
            resolveBangumiUnsupportedOverlayActionMessage(BangumiOverlayUnsupportedAction.DISLIKE)
        )
        assertEquals(
            "番剧暂不支持投币操作",
            resolveBangumiUnsupportedOverlayActionMessage(BangumiOverlayUnsupportedAction.COIN)
        )
    }

    @Test
    fun resolveBangumiOverlayShareTitle_prefersEpisodeSubtitle() {
        val shareTitle = resolveBangumiOverlayShareTitle(
            title = "名侦探柯南",
            subtitle = "第1话 云霄飞车杀人事件"
        )

        assertEquals("名侦探柯南 第1话 云霄飞车杀人事件", shareTitle)
    }

    @Test
    fun shouldShowBangumiOverlayDislikeAction_isAlwaysFalse() {
        assertFalse(shouldShowBangumiOverlayDislikeAction())
    }

    @Test
    fun resolveBangumiPlaybackDebugInfo_marksRenderedFirstFrame() {
        val info = resolveBangumiPlaybackDebugInfo(
            BangumiPlaybackDebugSnapshot(
                episodeId = 39L,
                cid = 34442052203L,
                playbackState = androidx.media3.common.Player.STATE_READY,
                playWhenReady = true,
                isPlaying = true,
                firstFrameRendered = true,
                lastVideoEvent = "first frame rendered"
            )
        )

        assertEquals("rendered", info.firstFrame)
        assertEquals("READY", info.playbackState)
        assertEquals("true", info.playWhenReady)
        assertEquals("true", info.isPlaying)
        assertEquals("episode=39 cid=34442052203 first frame rendered", info.lastVideoEvent)
    }

    @Test
    fun resetBangumiPlaybackDebugSnapshot_rebindsDiagnosticsToNewEpisode() {
        val previous = BangumiPlaybackDebugSnapshot(
            episodeId = 40L,
            cid = 400L,
            playbackState = androidx.media3.common.Player.STATE_READY,
            playWhenReady = true,
            isPlaying = true,
            firstFrameRendered = true,
            lastVideoEvent = "first frame rendered"
        )

        val reset = previous.resetForEpisode(episodeId = 39L, cid = 390L)
        val info = resolveBangumiPlaybackDebugInfo(reset)

        assertEquals(39L, reset.episodeId)
        assertEquals(390L, reset.cid)
        assertEquals("", info.firstFrame)
        assertEquals("episode=39 cid=390 waiting first frame", info.lastVideoEvent)
    }

    @Test
    fun updateBangumiSuccessInteractionState_replacesInteractionFlags() {
        val updatedState = updateBangumiSuccessInteractionState(
            state = sampleSuccessState(isLiked = false, coinCount = 0),
            isLiked = true,
            coinCount = 3
        )

        assertTrue(updatedState.isLiked)
        assertEquals(2, updatedState.coinCount)
    }

    @Test
    fun applyBangumiCoinResult_marksEpisodeCoinedAndLikedWhenRequested() {
        val updatedState = applyBangumiCoinResult(
            state = sampleSuccessState(isLiked = false, coinCount = 0),
            coinDelta = 1,
            alsoLike = true
        )

        assertTrue(updatedState.isLiked)
        assertEquals(1, updatedState.coinCount)
    }

    private fun sampleSuccessState(
        isLiked: Boolean,
        coinCount: Int
    ): BangumiPlayerState.Success {
        return BangumiPlayerState.Success(
            seasonDetail = BangumiDetail(
                seasonId = 2026L,
                title = "名侦探柯南"
            ),
            currentEpisode = BangumiEpisode(
                id = 101L,
                aid = 10001L,
                cid = 1001L,
                title = "第1话",
                longTitle = "云霄飞车杀人事件"
            ),
            currentEpisodeIndex = 0,
            playUrl = "https://example.com/video.mpd",
            audioUrl = "https://example.com/audio.m4a",
            quality = 80,
            acceptQuality = listOf(80, 64),
            acceptDescription = listOf("1080P", "720P"),
            isLiked = isLiked,
            coinCount = coinCount
        )
    }
}
