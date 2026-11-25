package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.layout.ContentScale
import androidx.media3.ui.AspectRatioFrameLayout
import com.android.purebilibili.feature.anime4k.gl.Anime4KDisplayScaleMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoAspectRatioLayoutPolicyTest {

    @Test
    fun `fixed 4 to 3 ratio should fit inside landscape container`() {
        val layout = resolveVideoViewportLayout(
            containerWidth = 2400,
            containerHeight = 1080,
            aspectRatio = VideoAspectRatio.RATIO_4_3
        )

        assertEquals(1440, layout.width)
        assertEquals(1080, layout.height)
    }

    @Test
    fun `fixed 16 to 9 ratio should fit inside landscape container`() {
        val layout = resolveVideoViewportLayout(
            containerWidth = 2400,
            containerHeight = 1080,
            aspectRatio = VideoAspectRatio.RATIO_16_9
        )

        assertEquals(1920, layout.width)
        assertEquals(1080, layout.height)
    }

    @Test
    fun `fixed 16 to 9 ratio should letterbox in 16 to 10 tablet fullscreen`() {
        val layout = resolveVideoViewportLayout(
            containerWidth = 2560,
            containerHeight = 1600,
            aspectRatio = VideoAspectRatio.RATIO_16_9
        )

        assertEquals(2560, layout.width)
        assertEquals(1440, layout.height)
    }

    @Test
    fun `fit mode should keep fullscreen viewport`() {
        val layout = resolveVideoViewportLayout(
            containerWidth = 2400,
            containerHeight = 1080,
            aspectRatio = VideoAspectRatio.FIT
        )

        assertEquals(2400, layout.width)
        assertEquals(1080, layout.height)
        assertNull(VideoAspectRatio.FIT.targetAspectRatio)
    }

    @Test
    fun `fill and stretch should keep fullscreen viewport`() {
        val fillLayout = resolveVideoViewportLayout(
            containerWidth = 2400,
            containerHeight = 1080,
            aspectRatio = VideoAspectRatio.FILL
        )
        val stretchLayout = resolveVideoViewportLayout(
            containerWidth = 2400,
            containerHeight = 1080,
            aspectRatio = VideoAspectRatio.STRETCH
        )

        assertEquals(2400, fillLayout.width)
        assertEquals(1080, fillLayout.height)
        assertEquals(2400, stretchLayout.width)
        assertEquals(1080, stretchLayout.height)
        assertNull(VideoAspectRatio.FILL.targetAspectRatio)
        assertNull(VideoAspectRatio.STRETCH.targetAspectRatio)
        assertTrue(shouldUseFillMaxPlayerViewport(VideoAspectRatio.FILL))
        assertTrue(shouldUseFillMaxPlayerViewport(VideoAspectRatio.STRETCH))
        assertTrue(shouldUseFillMaxPlayerViewport(VideoAspectRatio.FIT))
        assertFalse(shouldUseFillMaxPlayerViewport(VideoAspectRatio.RATIO_16_9))
        assertFalse(shouldUseFillMaxPlayerViewport(VideoAspectRatio.RATIO_4_3))
    }


    @Test
    fun `measured viewport refreshes until Android view matches Compose size`() {
        assertTrue(shouldRefreshMeasuredPlayerViewport(2400, 1080, 1920, 1080))
        assertFalse(shouldRefreshMeasuredPlayerViewport(2400, 1080, 2400, 1080))
        assertTrue(shouldRefreshMeasuredPlayerViewport(0, 0, 2400, 1080))
    }

    @Test
    fun `fullscreen ratios should map to expected player resize modes`() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, VideoAspectRatio.FIT.playerResizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, VideoAspectRatio.FILL.playerResizeMode)
        // Fixed frames letterbox the outer viewport; content FITs inside (no accidental crop).
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, VideoAspectRatio.RATIO_16_9.playerResizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, VideoAspectRatio.RATIO_4_3.playerResizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, VideoAspectRatio.STRETCH.playerResizeMode)
        assertEquals(16f / 9f, VideoAspectRatio.RATIO_16_9.targetAspectRatio)
        assertEquals(4f / 3f, VideoAspectRatio.RATIO_4_3.targetAspectRatio)
    }

    @Test
    fun `fixed ratio layout plus fit content leaves 4 to 3 video uncropped in 16 to 9 frame`() {
        // Phone landscape 21:9-ish → 16:9 frame is width-limited.
        val frame = resolveVideoViewportLayout(
            containerWidth = 2340,
            containerHeight = 1080,
            aspectRatio = VideoAspectRatio.RATIO_16_9
        )
        assertEquals(1920, frame.width)
        assertEquals(1080, frame.height)
        // Inner PlayerView uses FIT so a 4:3 source letterboxes inside this frame.
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, VideoAspectRatio.RATIO_16_9.playerResizeMode)
    }

    @Test
    fun `stretch fill fit and zoom cover common landscape source shapes`() {
        // Ultrawide container, 16:9 source: FIT keeps full frame, FILL/ZOOM crop sides.
        val full = resolveVideoViewportLayout(2400, 1080, VideoAspectRatio.FIT)
        assertEquals(2400, full.width)
        assertEquals(1080, full.height)
        val fill = resolveVideoViewportLayout(2400, 1080, VideoAspectRatio.FILL)
        assertEquals(2400, fill.width)
        assertEquals(1080, fill.height)
        // 4:3 forced frame on same container is pillarboxed.
        val fourThree = resolveVideoViewportLayout(2400, 1080, VideoAspectRatio.RATIO_4_3)
        assertEquals(1440, fourThree.width)
        assertEquals(1080, fourThree.height)
    }

    @Test
    fun `every explicit fit choice is preserved for both source orientations`() {
        VideoAspectRatio.entries.forEach { ratio ->
            assertEquals(ratio, resolveSafeVideoAspectRatio(ratio, isVerticalVideo = true))
            assertEquals(ratio, resolveSafeVideoAspectRatio(ratio, isVerticalVideo = false))
        }
    }

    @Test
    fun `equal width and equal height use source ratio even when viewport overflows`() {
        assertEquals(VideoViewportLayout(1000, 2000),
            resolveVideoViewportLayout(1000, 600, VideoAspectRatio.FIT_WIDTH, 500, 1000))
        assertEquals(VideoViewportLayout(1200, 600),
            resolveVideoViewportLayout(1000, 600, VideoAspectRatio.FIT_HEIGHT, 2000, 1000))
    }

    @Test
    fun `original preserves decoded pixels and limit never upscales`() {
        assertEquals(VideoViewportLayout(1920, 1080),
            resolveVideoViewportLayout(1000, 600, VideoAspectRatio.ORIGINAL, 1920, 1080))
        assertEquals(VideoViewportLayout(500, 250),
            resolveVideoViewportLayout(1000, 600, VideoAspectRatio.SCALE_DOWN, 500, 250))
        assertEquals(VideoViewportLayout(1000, 563),
            resolveVideoViewportLayout(1000, 600, VideoAspectRatio.SCALE_DOWN, 1920, 1080))
    }

    @Test
    fun `all source sized modes use explicit viewport with fit content`() {
        listOf(VideoAspectRatio.FIT_WIDTH, VideoAspectRatio.FIT_HEIGHT,
            VideoAspectRatio.ORIGINAL, VideoAspectRatio.SCALE_DOWN).forEach { ratio ->
            assertFalse(shouldUseFillMaxPlayerViewport(ratio))
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, ratio.playerResizeMode)
            assertEquals(Anime4KDisplayScaleMode.FIT, ratio.toAnime4KDisplayScaleMode())
        }
    }

    @Test
    fun `video ratio modes map to matching Anime4K display modes`() {
        assertEquals(Anime4KDisplayScaleMode.FIT, VideoAspectRatio.FIT.toAnime4KDisplayScaleMode())
        assertEquals(Anime4KDisplayScaleMode.CROP, VideoAspectRatio.FILL.toAnime4KDisplayScaleMode())
        assertEquals(Anime4KDisplayScaleMode.FIT, VideoAspectRatio.RATIO_16_9.toAnime4KDisplayScaleMode())
        assertEquals(Anime4KDisplayScaleMode.FIT, VideoAspectRatio.RATIO_4_3.toAnime4KDisplayScaleMode())
        assertEquals(Anime4KDisplayScaleMode.STRETCH, VideoAspectRatio.STRETCH.toAnime4KDisplayScaleMode())
    }

    @Test
    fun `compose surface sync workaround targets Android 14 through Android 17 plus SurfaceViews`() {
        assertFalse(shouldRunComposeSurfaceSyncWorkaround(sdkInt = 33, isSurfaceView = true))
        listOf(34, 35, 36, 37).forEach { sdkInt ->
            assertTrue(shouldRunComposeSurfaceSyncWorkaround(sdkInt = sdkInt, isSurfaceView = true))
            assertFalse(shouldRunComposeSurfaceSyncWorkaround(sdkInt = sdkInt, isSurfaceView = false))
        }
    }

    @Test
    fun `inline viewport always uses FIT while fullscreen preserves selected aspect ratio`() {
        VideoAspectRatio.entries.forEach { ratio ->
            assertEquals(
                VideoAspectRatio.FIT,
                resolvePlayerViewportAspectRatio(isFullscreen = false, selectedAspectRatio = ratio),
            )
            assertEquals(
                ratio,
                resolvePlayerViewportAspectRatio(isFullscreen = true, selectedAspectRatio = ratio),
            )
        }
    }

    @Test
    fun `handoff frame content scale matches viewport aspect ratio without stretching FIT`() {
        assertEquals(ContentScale.Fit, resolvePlayerHandoffContentScale(VideoAspectRatio.FIT))
        assertEquals(ContentScale.Crop, resolvePlayerHandoffContentScale(VideoAspectRatio.FILL))
        assertEquals(ContentScale.FillBounds, resolvePlayerHandoffContentScale(VideoAspectRatio.STRETCH))
        assertEquals(ContentScale.Fit, resolvePlayerHandoffContentScale(VideoAspectRatio.RATIO_4_3))
        assertEquals(ContentScale.Fit, resolvePlayerHandoffContentScale(VideoAspectRatio.ORIGINAL))
    }
}
