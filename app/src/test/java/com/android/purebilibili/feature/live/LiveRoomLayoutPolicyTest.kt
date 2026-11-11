package com.android.purebilibili.feature.live

import android.content.res.Configuration
import android.view.Surface
import com.android.purebilibili.core.util.AppDisplayContextInput
import com.android.purebilibili.core.util.resolveAppDisplayContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveRoomLayoutPolicyTest {
    @Test
    fun `landscape fullscreen keeps inline portrait layout until rotation arrives`() {
        val mode = resolveLiveRequestedOrientationMode(isTabletDevice = false, isFullscreen = true)
        val pending = shouldPresentLiveFullscreen(true, isLandscape = false, orientationMode = mode)
        assertFalse(pending)
        assertEquals(
            LiveRoomLayoutMode.PortraitPanel,
            resolveLiveRoomLayoutMode(false, false, pending, false),
        )
        val rotated = shouldPresentLiveFullscreen(true, isLandscape = true, orientationMode = mode)
        assertTrue(rotated)
        assertEquals(
            LiveRoomLayoutMode.LandscapeOverlay,
            resolveLiveRoomLayoutMode(true, false, rotated, false),
        )
    }

    @Test
    fun `pending fullscreen ignores repeated enter swipe and back cancels late rotation`() {
        val requested = true
        assertFalse(shouldToggleLiveFullscreenForDrag(-20f, 2.5f, requested, reverse = false))
        assertTrue(shouldToggleLiveFullscreenForDrag(20f, 2.5f, requested, reverse = false))
        val cancelledMode = resolveLiveRequestedOrientationMode(isTabletDevice = false, isFullscreen = false)
        assertEquals(LiveRequestedOrientationMode.Portrait, cancelledMode)
        assertFalse(shouldPresentLiveFullscreen(false, isLandscape = true, orientationMode = cancelledMode))
    }

    @Test
    fun `portrait live and tablet fullscreen do not wait for landscape`() {
        val portraitMode = resolveLiveRequestedOrientationMode(false, true, isPortraitLive = true)
        assertTrue(shouldPresentLiveFullscreen(true, isLandscape = false, orientationMode = portraitMode))
        val tabletMode = resolveLiveRequestedOrientationMode(true, true)
        assertTrue(shouldPresentLiveFullscreen(true, isLandscape = false, orientationMode = tabletMode))
    }

    @Test
    fun `phone system window requests fullscreen orientation and waits for matching bounds`() {
        val splitWindow = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 390,
                currentWindowHeightDp = 440,
                maximumWindowWidthDp = 390,
                maximumWindowHeightDp = 844,
                configurationOrientation = Configuration.ORIENTATION_PORTRAIT,
                displayRotation = Surface.ROTATION_0,
                isInMultiWindowMode = true,
            ),
        )
        val mode = resolveLiveRequestedOrientationMode(splitWindow, isFullscreen = true)
        assertEquals(LiveRequestedOrientationMode.SensorLandscape, mode)
        assertFalse(shouldPresentLiveFullscreen(true, isLandscape = false, orientationMode = mode))
        assertTrue(shouldPresentLiveFullscreen(true, isLandscape = true, orientationMode = mode))
        assertEquals(
            LiveRequestedOrientationMode.SensorPortrait,
            resolveLiveRequestedOrientationMode(splitWindow, isFullscreen = true, isPortraitLive = true),
        )
        assertEquals(
            LiveRequestedOrientationMode.Portrait,
            resolveLiveRequestedOrientationMode(splitWindow, isFullscreen = false),
        )
    }

    @Test
    fun `actual video dimensions include pixel aspect ratio and ignore unknown sizes`() {
        assertEquals(true, resolveLiveVideoIsPortrait(1080, 1920))
        assertEquals(false, resolveLiveVideoIsPortrait(1920, 1080))
        assertEquals(false, resolveLiveVideoIsPortrait(1080, 1920, 2f))
        assertEquals(null, resolveLiveVideoIsPortrait(0, 1920))
        assertEquals(null, resolveLiveVideoIsPortrait(1080, 1920, Float.NaN))
    }

    @Test
    fun `phone fullscreen requests sensor landscape instead of following window tablet width`() {
        assertEquals(
            LiveRequestedOrientationMode.SensorLandscape,
            resolveLiveRequestedOrientationMode(
                isTabletDevice = false,
                isFullscreen = true,
            )
        )
        assertEquals(
            LiveRequestedOrientationMode.Portrait,
            resolveLiveRequestedOrientationMode(
                isTabletDevice = false,
                isFullscreen = false,
            )
        )
        assertEquals(
            LiveRequestedOrientationMode.Unspecified,
            resolveLiveRequestedOrientationMode(
                isTabletDevice = true,
                isFullscreen = true,
            )
        )
        assertEquals(
            LiveRequestedOrientationMode.Unspecified,
            resolveLiveRequestedOrientationMode(
                isTabletDevice = true,
                isFullscreen = false,
            )
        )
    }

    @Test
    fun `landscape natural cover keeps live fullscreen inside the current window`() {
        assertEquals(
            LiveRequestedOrientationMode.Unspecified,
            resolveLiveRequestedOrientationMode(
                isTabletDevice = true,
                isFullscreen = true,
                isFoldableCoverWindow = true,
                usesInWindowFullscreen = true,
            )
        )
        assertEquals(
            LiveRequestedOrientationMode.Unspecified,
            resolveLiveRequestedOrientationMode(
                isTabletDevice = true,
                isFullscreen = false,
                isFoldableCoverWindow = true,
                usesInWindowFullscreen = true,
            )
        )
    }

    @Test
    fun `display context cover uses in-window live orientation`() {
        val cover = resolveAppDisplayContext(
            AppDisplayContextInput(
                currentWindowWidthDp = 616,
                currentWindowHeightDp = 421,
                maximumWindowWidthDp = 861,
                maximumWindowHeightDp = 609,
                configurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
                displayRotation = Surface.ROTATION_0,
                displayModeWidthPx = 1848,
                displayModeHeightPx = 1264,
                hasHingeAngleSensor = true,
            )
        )
        assertEquals(
            LiveRequestedOrientationMode.Unspecified,
            resolveLiveRequestedOrientationMode(
                displayContext = cover,
                isFullscreen = true,
            )
        )
    }

    @Test
    fun `portrait natural cover retains phone-style live orientation requests`() {
        assertEquals(
            LiveRequestedOrientationMode.SensorLandscape,
            resolveLiveRequestedOrientationMode(
                isTabletDevice = true,
                isFullscreen = true,
                isFoldableCoverWindow = true,
                usesInWindowFullscreen = false,
            )
        )
    }


    @Test
    fun `window orientation determines nonfullscreen layout across device classes`() {
        listOf(false, true).forEach { tablet ->
            assertEquals(LiveRoomLayoutMode.PortraitPanel, resolveLiveRoomLayoutMode(false, tablet, false, false))
            assertEquals(LiveRoomLayoutMode.PortraitVerticalOverlay, resolveLiveRoomLayoutMode(false, tablet, false, true))
            assertEquals(LiveRoomLayoutMode.LandscapeSplit, resolveLiveRoomLayoutMode(true, tablet, false, false))
            assertEquals(LiveRoomLayoutMode.LandscapeSplit, resolveLiveRoomLayoutMode(true, tablet, false, true))
        }
    }

    @Test
    fun `fullscreen only keeps the video layout`() {
        assertEquals(LiveRoomLayoutMode.LandscapeOverlay, resolveLiveRoomLayoutMode(true, false, true, false))
        assertEquals(LiveRoomLayoutMode.PortraitVerticalOverlay, resolveLiveRoomLayoutMode(false, false, true, true))
        assertEquals(LiveRoomLayoutMode.LandscapeOverlay, resolveLiveRoomLayoutMode(false, true, true, false))
    }

    @Test
    fun `obstructing hinge keeps room interaction in its own pane`() {
        assertEquals(LiveRoomLayoutMode.LandscapeSplit, resolveLiveRoomLayoutMode(false, false, false, true, true))
        assertEquals(LiveRoomLayoutMode.LandscapeOverlay, resolveLiveRoomLayoutMode(false, false, true, true, true))
    }

    @Test
    fun `split width follows local aspect ratio and caps the interaction column`() {
        assertEquals(400, resolveLiveSplitChatPanelWidthDp(1200, screenHeightDp = 700))
        assertEquals(352, resolveLiveSplitChatPanelWidthDp(800, screenHeightDp = 350))
        assertEquals(240, resolveLiveSplitChatPanelWidthDp(800, screenHeightDp = 700))
        assertEquals(181, resolveLiveSplitChatPanelWidthDp(440, screenHeightDp = 240))
        assertTrue(resolveLiveSplitChatPanelWidthDp(1, screenHeightDp = 1) in 0..1)
    }

    @Test
    fun `player insets are consumed only in fullscreen`() {
        LiveRoomLayoutMode.entries.forEach { mode ->
            assertFalse(shouldApplyLiveTopControlSystemInsets(mode, false))
            assertFalse(shouldApplyLiveBottomControlSystemInsets(mode, false, true))
            assertTrue(shouldApplyLiveTopControlSystemInsets(mode, true))
            assertTrue(shouldApplyLiveBottomControlSystemInsets(mode, true, false))
            assertTrue(shouldShowLivePlayerControlsTopBar(mode, false))
        }
    }

    @Test
    fun `room duration and count labels remain compact`() {
        val startedAt = 1_700_000_000L
        assertEquals("开播1小时30分钟", formatLiveDuration(startedAt, startedAt * 1000 + 90 * 60_000))
        assertEquals("1.2万", formatLiveViewerCount(12_300))
        assertEquals("-", formatLiveViewerCount(0))
    }
}
