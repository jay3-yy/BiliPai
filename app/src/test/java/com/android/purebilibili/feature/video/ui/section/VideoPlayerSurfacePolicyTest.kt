package com.android.purebilibili.feature.video.ui.section

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlayerSurfacePolicyTest {

    @Test
    fun `flip disabled keeps default surface type`() {
        assertFalse(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = false,
                isFlippedVertical = false,
                preferCompatibleTextureSurface = false,
            )
        )
    }

    @Test
    fun `horizontal flip requires texture surface`() {
        assertTrue(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = true,
                isFlippedVertical = false,
                preferCompatibleTextureSurface = false,
            )
        )
    }

    @Test
    fun `vertical flip requires texture surface`() {
        assertTrue(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = false,
                isFlippedVertical = true,
                preferCompatibleTextureSurface = false,
            )
        )
    }

    @Test
    fun `live back preview requires texture surface`() {
        assertTrue(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = false,
                isFlippedVertical = false,
                liveBackPreview = true,
                preferCompatibleTextureSurface = false,
            )
        )
    }

    @Test
    fun `navigation transform requires texture surface before back starts`() {
        assertTrue(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = false,
                isFlippedVertical = false,
                navigationTransformEnabled = true,
                preferCompatibleTextureSurface = false,
            )
        )
    }

    @Test
    fun `hdr output forces surface view even when navigation transform is enabled`() {
        assertFalse(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = false,
                isFlippedVertical = false,
                navigationTransformEnabled = true,
                requiresHdrSurfaceOutput = true,
                preferCompatibleTextureSurface = true,
            )
        )
        // 请求翻转也不能让 HDR 输出降为纹理合成的 SDR。
        assertFalse(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = true,
                isFlippedVertical = false,
                navigationTransformEnabled = true,
                requiresHdrSurfaceOutput = true,
                preferCompatibleTextureSurface = true,
            )
        )
        assertFalse(
            shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = false,
                isFlippedVertical = true,
                liveBackPreview = true,
                requiresHdrSurfaceOutput = true,
                preferCompatibleTextureSurface = true,
            )
        )
    }

    @Test
    fun `live surface switch gates navigation texture request`() {
        assertTrue(
            resolveNavigationLiveSurfaceTextureEnabled(
                cardTransitionEnabled = true,
                liveSurfaceCardTransitionEnabled = true,
            )
        )
        assertFalse(
            resolveNavigationLiveSurfaceTextureEnabled(
                cardTransitionEnabled = true,
                liveSurfaceCardTransitionEnabled = false,
            )
        )
        assertFalse(
            resolveNavigationLiveSurfaceTextureEnabled(
                cardTransitionEnabled = false,
                liveSurfaceCardTransitionEnabled = true,
            )
        )
    }

    @Test
    fun `live player shared element never attaches under hdr`() {
        assertTrue(
            resolveAllowLivePlayerSharedElementForMorph(
                cardTransitionEnabled = true,
                liveSurfaceCardTransitionEnabled = true,
                requiresHdrSurfaceOutput = false,
            )
        )
        assertFalse(
            resolveAllowLivePlayerSharedElementForMorph(
                cardTransitionEnabled = true,
                liveSurfaceCardTransitionEnabled = true,
                requiresHdrSurfaceOutput = true,
            )
        )
        assertFalse(
            resolveAllowLivePlayerSharedElementForMorph(
                cardTransitionEnabled = true,
                liveSurfaceCardTransitionEnabled = false,
            )
        )
    }

    @Test
    fun `hdr surface required for quality 125 126 and pq hlg transfer`() {
        assertTrue(requiresHdrSurfaceOutput(currentQualityId = 125))
        assertTrue(requiresHdrSurfaceOutput(currentQualityId = 126))
        assertTrue(requiresHdrSurfaceOutput(currentQualityId = 129))
        assertTrue(requiresHdrSurfaceOutput(currentQualityId = 80, colorTransfer = 6))
        assertTrue(requiresHdrSurfaceOutput(currentQualityId = 80, colorTransfer = 7))
        assertFalse(requiresHdrSurfaceOutput(currentQualityId = 80, colorTransfer = 0))
        assertFalse(requiresHdrSurfaceOutput(currentQualityId = 120, colorTransfer = 0))
    }

    @Test
    fun `player surface stays hidden until smooth reveal starts`() {
        val spec = resolveVideoPlayerSurfaceRevealSpec(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasStartedSmoothReveal = false
        )

        assertEquals(0f, spec.alpha)
    }

    @Test
    fun `player surface animates to fully visible when smooth reveal starts`() {
        val spec = resolveVideoPlayerSurfaceRevealSpec(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasStartedSmoothReveal = true
        )

        assertEquals(1f, spec.alpha)
    }

    @Test
    fun `forced return and manual start keep player surface hidden`() {
        val forcedReturnSpec = resolveVideoPlayerSurfaceRevealSpec(
            forceCoverDuringReturnAnimation = true,
            shouldKeepCoverForManualStart = false,
            hasStartedSmoothReveal = true
        )
        val manualStartSpec = resolveVideoPlayerSurfaceRevealSpec(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = true,
            hasStartedSmoothReveal = true
        )

        assertEquals(0f, forcedReturnSpec.alpha)
        assertEquals(0f, manualStartSpec.alpha)
    }

    @Test
    fun `inline player view stays visible under manual start cover`() {
        // CoverFirst 只叠封面，不得 INVISIBLE PlayerView，否则首帧事件永不触发。
        assertTrue(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = true
            )
        )
        assertTrue(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false
            )
        )
        assertFalse(
            shouldShowInlinePlayerView(
                isPortraitFullscreen = false,
                forceCoverDuringReturnAnimation = true,
                shouldKeepCoverForManualStart = false
            )
        )
    }

    @Test
    fun `collection switch waits when success arrives before paused player output`() {
        assertEquals(
            MediaSwitchSurfaceRebindAction.WAIT_FOR_OUTPUT,
            resolveMediaSwitchSurfaceRebindAction(
                hasSuccessPlaybackIdentity = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                hasPlayerView = true,
                mediaItemCount = 0
            )
        )
    }

    @Test
    fun `collection switch rebinds once player view and media item are ready`() {
        assertEquals(
            MediaSwitchSurfaceRebindAction.REBIND,
            resolveMediaSwitchSurfaceRebindAction(
                hasSuccessPlaybackIdentity = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                hasPlayerView = true,
                mediaItemCount = 1
            )
        )
    }

    @Test
    fun `collection switch surface recovery skips pip and stale success`() {
        assertEquals(
            MediaSwitchSurfaceRebindAction.SKIP,
            resolveMediaSwitchSurfaceRebindAction(
                hasSuccessPlaybackIdentity = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = true,
                hasPlayerView = true,
                mediaItemCount = 1
            )
        )
        assertEquals(
            MediaSwitchSurfaceRebindAction.SKIP,
            resolveMediaSwitchSurfaceRebindAction(
                hasSuccessPlaybackIdentity = false,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                hasPlayerView = true,
                mediaItemCount = 1
            )
        )
    }

    @Test
    fun `collection switch retries surface when ready playback still has no first frame`() {
        assertTrue(
            shouldRetryMediaSwitchSurfaceRebind(
                hasRenderedFirstFrame = false,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                hasPlayerView = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun `collection switch stops surface retries after frame or outside active host`() {
        assertFalse(
            shouldRetryMediaSwitchSurfaceRebind(
                hasRenderedFirstFrame = true,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                hasPlayerView = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRetryMediaSwitchSurfaceRebind(
                hasRenderedFirstFrame = false,
                shouldBindInlinePlayerView = true,
                isInPipMode = true,
                hasPlayerView = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRetryMediaSwitchSurfaceRebind(
                hasRenderedFirstFrame = false,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                hasPlayerView = true,
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
        assertFalse(
            shouldRetryMediaSwitchSurfaceRebind(
                hasRenderedFirstFrame = false,
                shouldBindInlinePlayerView = true,
                isInPipMode = false,
                hasPlayerView = true,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }
}
