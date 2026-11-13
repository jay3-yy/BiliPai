package com.android.purebilibili.core.util

import com.android.purebilibili.core.util.AppFoldPosture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoDetailPresentationPolicyTest {

    private fun paneInput(
        isExpandedWindow: Boolean = true,
        foldPosture: AppFoldPosture = AppFoldPosture.None,
        isVerticalVideo: Boolean = false,
        requestsLandscapeFullscreen: Boolean = false,
        shouldAvoidHinge: Boolean = false,
        isStartingAudio: Boolean = false,
    ) = VideoDetailPresentationInput(
        isExpandedWindow = isExpandedWindow,
        foldPosture = foldPosture,
        isVerticalVideo = isVerticalVideo,
        requestsLandscapeFullscreen = requestsLandscapeFullscreen,
        shouldAvoidHinge = shouldAvoidHinge,
        isStartingAudio = isStartingAudio,
    )

    @Test
    fun `expanded window landscape video resolves companion pane`() {
        assertEquals(
            VideoDetailPresentationMode.COMPANION_PANE,
            resolveVideoDetailPresentation(paneInput()),
        )
        assertEquals(
            VideoDetailPresentationMode.COMPANION_PANE,
            resolveVideoDetailPresentation(paneInput(foldPosture = AppFoldPosture.Flat)),
        )
    }

    @Test
    fun `medium window and hinge postures keep full takeover`() {
        assertEquals(
            VideoDetailPresentationMode.FULL_TAKEOVER,
            resolveVideoDetailPresentation(paneInput(isExpandedWindow = false)),
        )
        assertEquals(
            VideoDetailPresentationMode.FULL_TAKEOVER,
            resolveVideoDetailPresentation(paneInput(foldPosture = AppFoldPosture.Book)),
        )
        assertEquals(
            VideoDetailPresentationMode.FULL_TAKEOVER,
            resolveVideoDetailPresentation(paneInput(foldPosture = AppFoldPosture.Tabletop)),
        )
        assertEquals(
            VideoDetailPresentationMode.FULL_TAKEOVER,
            resolveVideoDetailPresentation(paneInput(shouldAvoidHinge = true)),
        )
    }

    @Test
    fun `vertical video fullscreen intent and audio mode keep full takeover`() {
        assertEquals(
            VideoDetailPresentationMode.FULL_TAKEOVER,
            resolveVideoDetailPresentation(paneInput(isVerticalVideo = true)),
        )
        assertEquals(
            VideoDetailPresentationMode.FULL_TAKEOVER,
            resolveVideoDetailPresentation(paneInput(requestsLandscapeFullscreen = true)),
        )
        assertEquals(
            VideoDetailPresentationMode.FULL_TAKEOVER,
            resolveVideoDetailPresentation(paneInput(isStartingAudio = true)),
        )
    }

    @Test
    fun `companion pane width leaves readable feed area`() {
        assertEquals(0.58f, resolveCompanionPaneWidthFraction(1280), 0.0001f)
        assertEquals(0.62f, resolveCompanionPaneWidthFraction(840), 0.0001f)
        // 1200dp 恰在高档位下限：面板 0.58，feed 剩 504dp。
        assertEquals(0.58f, resolveCompanionPaneWidthFraction(1200), 0.0001f)
        // 840dp 窗口面板 0.62 → feed 剩 319dp，不低于 30% 下限。
        assertTrue(resolveCompanionPaneWidthFraction(840) <= 0.70f)
    }
}
