package com.android.purebilibili.feature.video.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuComposerPolicyTest {

    @Test
    fun inlineComposer_usedForLandscapeFullscreenMode() {
        assertTrue(shouldUseInlineDanmakuComposer(isFullscreenMode = true))
        assertFalse(shouldUseInlineDanmakuComposer(isFullscreenMode = false))
    }

    @Test
    fun fullscreenCapsule_requiresBothRoomyViewportAndMeasuredSlot() {
        assertFalse(shouldShowDanmakuInputInControlBar(isFullscreen = true, widthDp = 719))
        assertTrue(shouldShowDanmakuInputInControlBar(isFullscreen = true, widthDp = 720))
        assertFalse(shouldShowDanmakuInputInControlBar(isFullscreen = false, widthDp = 720))
        assertFalse(shouldDrawDanmakuInputCapsule(availableWidthDp = 95))
        assertTrue(shouldDrawDanmakuInputCapsule(availableWidthDp = 96))
    }

    @Test
    fun inlineSend_retainsRoomyCinemaVisibility() {
        assertFalse(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 411))
        assertFalse(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 600))
        assertFalse(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 719))
        assertTrue(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 720))
        assertTrue(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 800))
    }


}