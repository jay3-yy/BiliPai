package com.android.purebilibili.feature.video.ui.components

import org.junit.Assert.assertEquals
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
    fun compactDanmakuSend_requiresRoomyTabletInlinePlayer() {
        assertFalse(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 411))
        assertFalse(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 600))
        assertFalse(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 719))
        assertTrue(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 720))
        assertTrue(shouldShowCompactDanmakuSendAction(isFullscreen = false, widthDp = 800))
    }

    @Test
    fun placeholder_reflectsLoginState() {
        assertTrue(resolveDanmakuInputPlaceholder(isLoggedIn = true).contains("友善"))
        assertTrue(resolveDanmakuInputPlaceholder(isLoggedIn = false).contains("登录"))
    }

    @Test
    fun sendOptions_includeExpectedDefaults() {
        assertEquals(9, danmakuSendColorOptions().size)
        assertEquals(3, danmakuSendModeOptions().size)
        assertEquals(3, danmakuSendFontSizeOptions().size)
    }
}
