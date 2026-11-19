package com.android.purebilibili.feature.live

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LivePlayerGesturePolicyTest {
    @Test
    fun `live playback keeps fullscreen drag without double tap playback`() {
        val policy = LivePlayerGesturePolicy.Shared
        assertFalse(policy.doubleTapPlayback)
        assertTrue(policy.centerDragFullscreen)
    }

    @Test
    fun `direction enters or exits once past the threshold and respects reverse`() {
        assertTrue(shouldToggleLiveFullscreenForDrag(-3f, 2.5f, false, false))
        assertTrue(shouldToggleLiveFullscreenForDrag(3f, 2.5f, true, false))
        assertFalse(shouldToggleLiveFullscreenForDrag(3f, 2.5f, false, false))
        assertFalse(shouldToggleLiveFullscreenForDrag(-2f, 2.5f, false, false))
        assertTrue(shouldToggleLiveFullscreenForDrag(3f, 2.5f, false, true))
        assertTrue(shouldToggleLiveFullscreenForDrag(-3f, 2.5f, true, true))
    }
}
