package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.store.PlayerProgressPlacement
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomControlBarLayoutPolicyTest {


    @Test
    fun bottomEdgeProgress_removesBottomGapFromWholeControlBar() {
        assertEquals(
            0,
            resolveBottomControlBarBottomPaddingDp(
                defaultBottomPaddingDp = 12,
                progressPlacement = PlayerProgressPlacement.BOTTOM_EDGE
            )
        )
        assertEquals(
            12,
            resolveBottomControlBarBottomPaddingDp(
                defaultBottomPaddingDp = 12,
                progressPlacement = PlayerProgressPlacement.ABOVE_CONTROLS
            )
        )
    }

    @Test
    fun inlineDetailPlayer_pinsProgressToVideoBottomEdge() {
        assertEquals(
            PlayerProgressPlacement.BOTTOM_EDGE,
            resolveVideoDetailProgressPlacement(
                requestedPlacement = PlayerProgressPlacement.ABOVE_CONTROLS,
                isFullscreen = false
            )
        )
    }

    @Test
    fun fullscreen_keepsRequestedProgressPlacement() {
        assertEquals(
            PlayerProgressPlacement.ABOVE_CONTROLS,
            resolveVideoDetailProgressPlacement(
                requestedPlacement = PlayerProgressPlacement.ABOVE_CONTROLS,
                isFullscreen = true
            )
        )
    }
}
