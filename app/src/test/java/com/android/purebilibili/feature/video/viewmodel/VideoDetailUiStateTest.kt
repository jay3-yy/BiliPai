package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.ViewInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class VideoDetailUiStateTest {
    private val info = ViewInfo(bvid = "BV1detail", cid = 123L, title = "Title", desc = "Description")
    private val details = VideoDetailUiState(requestBvid = info.bvid, requestCid = info.cid, info = info)

    @Test
    fun detailsCanRenderWhilePlaybackIsLoading() {
        val content = details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(info, content?.info)
        assertEquals("", content?.playUrl)
        assertEquals(VideoPlaybackUiState.RelatedLoadState.LOADING, content?.relatedLoadState)
    }

    @Test
    fun metadataDurationUsesTheSelectedPage() {
        val multiPageInfo = info.copy(pages = listOf(
            Page(cid = 456L, duration = 90L),
            Page(cid = info.cid, duration = 30L)
        ))
        val content = details.copy(info = multiPageInfo)
            .contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(30_000L, content?.videoDurationMs)
    }

    @Test
    fun missingPageDurationDoesNotInventAnEstimate() {
        val content = details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(0L, content?.videoDurationMs)
    }

    @Test
    fun playbackFailureDoesNotHideAnAlreadyLoadedDescription() {
        val error = VideoPlaybackUiState.Error(VideoLoadError.PlayUrlEmpty)
        assertEquals(info.desc, details.contentSnapshot(error, info.bvid, info.cid)?.info?.desc)
    }

    @Test
    fun metadataFromAnotherVideoOrPageIsNeverDisplayed() {
        assertNull(details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, "BV1other", info.cid))
        assertNull(details.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, 456L))
    }

    @Test
    fun readyPlaybackKeepsItsExistingContentAndActionState() {
        val playback = VideoPlaybackUiState.Success(info, "stream", isLiked = true)
        assertSame(playback, details.contentSnapshot(playback, info.bvid, info.cid))
    }

    @Test
    fun avRoutesKeepTheCanonicalPlaybackStateAfterDetailsResolve() {
        val avDetails = details.copy(requestBvid = "av123", requestCid = 0L)
        val playback = VideoPlaybackUiState.Success(info, "stream", isLiked = true)
        assertSame(playback, avDetails.contentSnapshot(playback, "av123", 0L))
    }

    @Test
    fun correctedCidCanRenderMetadataForTheOriginalRequest() {
        val requested = details.copy(requestCid = 999L)
        assertEquals(info.cid, requested.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, 999L)?.info?.cid)
    }

    @Test
    fun relatedFailureDoesNotHideMetadata() {
        val failed = details.copy(relatedLoadState = VideoPlaybackUiState.RelatedLoadState.FAILED)
        val content = failed.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(info, content?.info)
        assertEquals(VideoPlaybackUiState.RelatedLoadState.FAILED, content?.relatedLoadState)
    }

    @Test
    fun resolvingAnUnspecifiedCidDoesNotHideLoadedMetadata() {
        val unresolved = details.copy(requestCid = 0L)
        assertEquals(info, unresolved.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, 0L)?.info)
        assertEquals(info, unresolved.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)?.info)
        assertNull(unresolved.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, 456L))
    }

    @Test
    fun restartingPlaybackKeepsMetadataThroughLoadingAndFailure() {
        val restarted = details.forNewRequest(info.bvid, info.cid, 2L, VideoPlaybackUiState.Loading.Initial)
        assertEquals(2L, restarted.requestToken)
        assertEquals(info, restarted.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)?.info)
        val error = VideoPlaybackUiState.Error(VideoLoadError.PlayUrlEmpty)
        assertEquals(info, restarted.contentSnapshot(error, info.bvid, info.cid)?.info)
    }

    @Test
    fun restartingCachedPlaybackSeedsDetailsBeforePlaybackReturnsToLoading() {
        val playback = VideoPlaybackUiState.Success(info, "stream", isLoggedIn = true, isVip = true)
        val restarted = VideoDetailUiState().forNewRequest(info.bvid, info.cid, 2L, playback)
        val content = restarted.contentSnapshot(VideoPlaybackUiState.Loading.Initial, info.bvid, info.cid)
        assertEquals(info, content?.info)
        assertEquals(true, content?.isLoggedIn)
        assertEquals(true, content?.isVip)
    }

    @Test
    fun changingVideoOrPageClearsPreviousMetadata() {
        val playback = VideoPlaybackUiState.Success(info, "stream")
        val otherVideo = details.forNewRequest("BV1other", info.cid, 2L, playback)
        val otherPage = details.forNewRequest(info.bvid, 456L, 3L, playback)
        assertNull(otherVideo.info)
        assertNull(otherPage.info)
    }

    @Test
    fun knownPartSwitchKeepsDescriptionAndSelectionDuringPlaybackHandoff() {
        val multiPageInfo = info.copy(pages = listOf(
            Page(cid = info.cid, page = 1, duration = 30L),
            Page(cid = 456L, page = 2, duration = 90L),
        ))
        val multiPageDetails = details.copy(info = multiPageInfo)
        val oldPlayback = VideoPlaybackUiState.Success(multiPageInfo, "old-part-stream")
        val error = VideoPlaybackUiState.Error(VideoLoadError.PlayUrlEmpty)
        // 页面先收到目标 CID 时，旧播放投影、加载及失败均不能让整份正文消失。
        for (playback in listOf(oldPlayback, VideoPlaybackUiState.Loading.Initial, error)) {
            val content = multiPageDetails.contentSnapshot(playback, info.bvid, 456L)
            assertEquals(info.desc, content?.info?.desc)
            assertEquals(456L, content?.info?.cid)
            assertEquals(multiPageInfo.pages, content?.info?.pages)
            assertEquals(90_000L, content?.videoDurationMs)
            assertEquals("", content?.playUrl)
        }
        val restarted = multiPageDetails.forNewRequest(info.bvid, 456L, 2L, oldPlayback)
        assertEquals(456L, restarted.info?.cid)
        assertEquals(info.desc, restarted.info?.desc)
        assertNull(multiPageDetails.contentSnapshot(oldPlayback, "BV1other", 456L))
        assertNull(multiPageDetails.contentSnapshot(oldPlayback, info.bvid, 999L))
    }
}
