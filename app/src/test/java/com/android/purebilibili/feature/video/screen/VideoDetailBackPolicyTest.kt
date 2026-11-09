package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoDetailBackPolicyTest {
    @Test
    fun landscapeBackOpensCommentsThenLeavesWithoutExitingFullscreen() {
        assertEquals(VideoDetailLocalBackTarget.SHOW_LANDSCAPE_COMMENTS, landscapeBack(commentsVisible = false))
        assertEquals(VideoDetailLocalBackTarget.NAVIGATE_BACK_KEEP_LANDSCAPE, landscapeBack(commentsVisible = true))
    }

    @Test
    fun disabledPreferenceKeepsExistingFullscreenExit() {
        assertEquals(
            VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = true, isPortraitFullscreen = false,
                canShowLandscapeComments = true, landscapeCommentsVisible = true,
            ),
        )
    }

    @Test
    fun unstableFullscreenAndUnavailablePanelKeepExistingExit() {
        for (phase in listOf(ContinuousPlayerTransitionPhase.AwaitingLandscape,
            ContinuousPlayerTransitionPhase.AwaitingPortrait, ContinuousPlayerTransitionPhase.Collapsing)) {
            assertEquals(VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN, landscapeBack(phase = phase))
        }
        assertEquals(VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN, landscapeBack(panelAvailable = false))
    }

    @Test
    fun portraitAndInlineBackDoNotOpenLandscapeComments() {
        assertEquals(
            VideoDetailLocalBackTarget.EXIT_PORTRAIT_FULLSCREEN,
            resolveVideoDetailLocalBackTarget(false, true, landscapeBackShowsComments = true),
        )
        assertEquals(
            VideoDetailLocalBackTarget.NAVIGATE_BACK,
            resolveVideoDetailLocalBackTarget(false, false, landscapeBackShowsComments = true),
        )
    }

    private fun landscapeBack(
        commentsVisible: Boolean = false,
        phase: ContinuousPlayerTransitionPhase = ContinuousPlayerTransitionPhase.Fullscreen,
        panelAvailable: Boolean = true,
    ) = resolveVideoDetailLocalBackTarget(
        isLandscapeFullscreen = true, isPortraitFullscreen = false,
        continuousPlayerPhase = phase, landscapeBackShowsComments = true,
        canShowLandscapeComments = panelAvailable, landscapeCommentsVisible = commentsVisible,
    )
}
