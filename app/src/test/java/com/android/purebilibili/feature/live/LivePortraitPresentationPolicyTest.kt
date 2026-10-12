package com.android.purebilibili.feature.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivePortraitPresentationPolicyTest {
    @Test
    fun `clear screen hides all portrait chrome without changing chat preference`() {
        val cleared = resolveLivePortraitPresentation(
            LiveRoomLayoutMode.PortraitVerticalOverlay,
            clearScreen = true,
            chatVisible = true,
        )
        assertTrue(cleared.clearScreen)
        assertFalse(cleared.showChrome)
        assertFalse(cleared.showChatPreview)
        assertFalse(cleared.showMediaOverlays)

        val restored = resolveLivePortraitPresentation(
            LiveRoomLayoutMode.PortraitVerticalOverlay,
            clearScreen = false,
            chatVisible = true,
        )
        assertTrue(restored.showChrome)
        assertTrue(restored.showChatPreview)
        assertTrue(restored.showMediaOverlays)
    }

    @Test
    fun `restoring clear screen preserves hidden chat`() {
        val restored = resolveLivePortraitPresentation(
            LiveRoomLayoutMode.PortraitVerticalOverlay,
            clearScreen = false,
            chatVisible = false,
        )
        assertTrue(restored.showChrome)
        assertFalse(restored.showChatPreview)
    }

    @Test
    fun `fullscreen portrait hides the room header and regular chat`() {
        val presentation = resolveLivePortraitPresentation(
            LiveRoomLayoutMode.PortraitVerticalOverlay,
            clearScreen = false,
            chatVisible = true,
            isFullscreen = true,
        )

        assertFalse(presentation.showChrome)
        assertFalse(presentation.showChatPreview)
        assertTrue(presentation.showMediaOverlays)
    }

    @Test
    fun `portrait settings never hide landscape or horizontal stream controls`() {
        listOf(
            LiveRoomLayoutMode.PortraitPanel,
            LiveRoomLayoutMode.LandscapeSplit,
            LiveRoomLayoutMode.LandscapeOverlay,
        ).forEach { mode ->
            val presentation = resolveLivePortraitPresentation(mode, true, true)
            assertFalse(presentation.usePortraitControls)
            assertFalse(presentation.clearScreen)
            assertTrue(presentation.showMediaOverlays)
        }
    }

    @Test
    fun `compact windows and large text reduce chat preview density`() {
        assertEquals(3, resolveLivePortraitChatPreviewCount(640, 1f))
        assertEquals(3, resolveLivePortraitChatPreviewCount(900, 1.5f))
        assertEquals(4, resolveLivePortraitChatPreviewCount(844, 1f))
    }

    @Test
    fun `all live layouts keep player double tap and fullscreen center drag`() {
        LiveRoomLayoutMode.entries.forEach { mode ->
            val policy = resolveLivePlayerGesturePolicy(mode)
            assertTrue(policy.doubleTapPlayback)
            assertTrue(policy.centerDragFullscreen)
        }
    }
}
