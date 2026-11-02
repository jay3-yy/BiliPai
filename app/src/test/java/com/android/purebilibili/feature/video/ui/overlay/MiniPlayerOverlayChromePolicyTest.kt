package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniPlayerOverlayChromePolicyTest {
    @Test
    fun hiddenControls_leaveOnlyVideoProgress() {
        val chrome = resolveMiniPlayerOverlayChrome(false, false, false)
        assertFalse(chrome.showHeaderChrome)
        assertFalse(chrome.showCenterControls)
        assertTrue(chrome.showProgressBar)
    }

    @Test
    fun movingAndResizing_hideTransportUntilGestureEnds() {
        for ((dragging, resizing) in listOf(true to false, false to true)) {
            val chrome = resolveMiniPlayerOverlayChrome(true, dragging, resizing)
            assertTrue(chrome.showHeaderChrome)
            assertFalse(chrome.showCenterControls)
        }
        assertTrue(resolveMiniPlayerOverlayChrome(true, false, false).showCenterControls)
    }

    @Test
    fun shortWindows_useOneControlRowWithoutBottomOverlap() {
        val layout = resolveMiniPlayerControlLayout(220f, 92f, true)
        assertTrue(layout.showHeaderTransport)
        assertFalse(layout.showBottomTransport)
        assertFalse(layout.showSeekButtons)
    }

    @Test
    fun narrowPortraitWindows_keepPlaybackAndMoveExpansionToMenu() {
        val layout = resolveMiniPlayerControlLayout(112f, 200f, true)
        assertTrue(layout.showCloseButton)
        assertTrue(layout.showBottomTransport)
        assertFalse(layout.showExpandButton)
        assertFalse(layout.showSeekButtons)
    }

    @Test
    fun seekControls_onlyAppearForSeekableVideoWithEnoughRoom() {
        assertTrue(resolveMiniPlayerControlLayout(220f, 124f, true).showSeekButtons)
        assertFalse(resolveMiniPlayerControlLayout(220f, 124f, false).showSeekButtons)
        assertFalse(resolveMiniPlayerControlLayout(168f, 124f, true).showSeekButtons)
    }
}
