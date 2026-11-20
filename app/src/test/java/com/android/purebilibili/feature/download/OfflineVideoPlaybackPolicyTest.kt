package com.android.purebilibili.feature.download

import androidx.media3.common.Player
import com.android.purebilibili.core.store.FullscreenMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineVideoPlaybackPolicyTest {

    @Test
    fun landscapeNaturalCover_entersAndExitsOfflineFullscreenWithoutRotation() {
        assertEquals(
            OfflineRequestedOrientationMode.Unspecified,
            resolveOfflineRequestedOrientationMode(
                isFullscreen = true,
                usesInWindowFullscreen = true,
            )
        )
        assertEquals(
            OfflineRequestedOrientationMode.Unspecified,
            resolveOfflineRequestedOrientationMode(
                isFullscreen = false,
                usesInWindowFullscreen = true,
            )
        )
    }

    @Test
    fun ordinaryPhone_keepsOfflineFullscreenRotation() {
        assertEquals(
            OfflineRequestedOrientationMode.SensorLandscape,
            resolveOfflineRequestedOrientationMode(
                isFullscreen = true,
                usesInWindowFullscreen = false,
            )
        )
        assertEquals(
            OfflineRequestedOrientationMode.Portrait,
            resolveOfflineRequestedOrientationMode(
                isFullscreen = false,
                usesInWindowFullscreen = false,
            )
        )
    }

    @Test
    fun offlinePhoneFullscreenHonorsOrientationPreference() {
        val cases = listOf(
            Triple(FullscreenMode.AUTO, true, OfflineRequestedOrientationMode.Portrait),
            Triple(FullscreenMode.NONE, false, OfflineRequestedOrientationMode.KeepCurrent),
            Triple(FullscreenMode.VERTICAL, false, OfflineRequestedOrientationMode.Portrait),
            Triple(FullscreenMode.HORIZONTAL, true, OfflineRequestedOrientationMode.SensorLandscape),
        )
        for ((mode, verticalVideo, orientation) in cases) {
            assertEquals(
                orientation,
                resolveOfflineRequestedOrientationMode(
                    isFullscreen = true,
                    usesInWindowFullscreen = false,
                    fullscreenMode = mode,
                    isVerticalVideo = verticalVideo,
                ),
            )
        }
    }

    @Test
    fun tabletOrUnfoldedInner_entersAndExitsOfflineFullscreenWithoutRotation() {
        assertEquals(
            OfflineRequestedOrientationMode.Unspecified,
            resolveOfflineRequestedOrientationMode(
                isFullscreen = true,
                usesInWindowFullscreen = false,
                isPhonePlayerOrientation = false,
            )
        )
        assertEquals(
            OfflineRequestedOrientationMode.Unspecified,
            resolveOfflineRequestedOrientationMode(
                isFullscreen = false,
                usesInWindowFullscreen = false,
                isPhonePlayerOrientation = false,
            )
        )
    }

    @Test
    fun seekFromEndedState_restartsPlayback() {
        assertTrue(
            shouldResumePlaybackAfterOfflineSeek(
                playbackState = Player.STATE_ENDED,
                wasPlayingBeforeSeek = false,
                targetPositionMs = 15_000L,
                durationMs = 120_000L
            )
        )
    }

    @Test
    fun pausedSeek_keepsPausedWhenPlayerDidNotEnd() {
        assertFalse(
            shouldResumePlaybackAfterOfflineSeek(
                playbackState = Player.STATE_READY,
                wasPlayingBeforeSeek = false,
                targetPositionMs = 15_000L,
                durationMs = 120_000L
            )
        )
    }

    @Test
    fun offlineDanmakuControlRequiresLocalSegmentsAndVideoMode() {
        assertTrue(shouldShowOfflineDanmakuControl(localSegmentCount = 1, isAudioOnly = false))
        assertFalse(shouldShowOfflineDanmakuControl(localSegmentCount = 0, isAudioOnly = false))
        assertFalse(shouldShowOfflineDanmakuControl(localSegmentCount = 1, isAudioOnly = true))
    }

    @Test
    fun offlineDanmakuLayerFollowsUserToggle() {
        assertTrue(
            shouldShowOfflineDanmakuLayer(
                localSegmentCount = 2,
                isAudioOnly = false,
                danmakuEnabled = true
            )
        )
        assertFalse(
            shouldShowOfflineDanmakuLayer(
                localSegmentCount = 2,
                isAudioOnly = false,
                danmakuEnabled = false
            )
        )
    }

    @Test
    fun resolveOfflineSeekPositionFromTouchClampsIntoDuration() {
        assertEquals(0L, resolveOfflineSeekPositionFromTouch(-20f, 200f, 120_000L))
        assertEquals(60_000L, resolveOfflineSeekPositionFromTouch(100f, 200f, 120_000L))
        assertEquals(120_000L, resolveOfflineSeekPositionFromTouch(260f, 200f, 120_000L))
        assertEquals(0L, resolveOfflineSeekPositionFromTouch(100f, 0f, 120_000L))
    }
}
