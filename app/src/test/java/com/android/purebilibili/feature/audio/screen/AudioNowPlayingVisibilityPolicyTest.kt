package com.android.purebilibili.feature.audio.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioNowPlayingVisibilityPolicyTest {

    @Test
    fun visibleOnHomeWhenListeningAndNotOnPlayerPage() {
        assertTrue(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true
            )
        )
    }

    @Test
    fun hiddenOnAudioModeScreenPipOrEmptyQueue() {
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = true,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true
            )
        )
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = true,
                hasCurrentItem = true,
                barEnabled = true
            )
        )
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = false,
                barEnabled = true
            )
        )
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = false,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true
            )
        )
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = false
            )
        )
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true,
                isVideoDetailDestination = true
            )
        )
    }

    @Test
    fun hiddenInLandscapeAndPlayerDestinations() {
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true,
                isLandscape = true
            )
        )
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true,
                isPlayerDestination = true
            )
        )
        assertTrue(isAudioNowPlayingPlayerDestination("video/BV1xx?cid=1"))
        assertTrue(isAudioNowPlayingPlayerDestination("bangumi/play/1/2"))
        assertTrue(isAudioNowPlayingPlayerDestination("live/123"))
        assertTrue(isAudioNowPlayingPlayerDestination("live"))
        assertFalse(isAudioNowPlayingPlayerDestination("main_host"))
        assertFalse(isAudioNowPlayingPlayerDestination("listen_video"))
    }

    @Test
    fun landscapeTabletKeepsVideoEntryReachableOutsidePlaybackDestinations() {
        assertTrue(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true,
                isLandscape = true,
                isTabletLayout = true,
            )
        )
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true,
                isLandscape = true,
                isTabletLayout = true,
                isVideoDetailDestination = true,
            )
        )
    }

    @Test
    fun miniPlayerTakesPriorityOverAudioNowPlayingBar() {
        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                isInMiniMode = true,
                hasCurrentItem = true,
                barEnabled = true
            )
        )
        assertTrue(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                isInMiniMode = false,
                hasCurrentItem = true,
                barEnabled = true
            )
        )
    }

    @Test
    fun dockNowPlayingStaysVisibleDuringChromeTransition() {
        assertTrue(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true
            )
        )
    }

    @Test
    fun hiddenOnIndependentDestinationsWithOwnBottomInteractiveBar() {
        assertTrue(hasAudioNowPlayingConflictingBottomBarDestination("dynamic_detail/12345?openComments=true"))
        assertTrue(hasAudioNowPlayingConflictingBottomBarDestination("article/6789?title=test"))
        assertTrue(hasAudioNowPlayingConflictingBottomBarDestination("comment_detail/1/2?type=1"))
        assertTrue(hasAudioNowPlayingConflictingBottomBarDestination("chat/100/1?name=up"))
        assertTrue(hasAudioNowPlayingConflictingBottomBarDestination("bangumi/456?epId=1"))
        assertFalse(hasAudioNowPlayingConflictingBottomBarDestination("bangumi?type=1"))
        assertFalse(hasAudioNowPlayingConflictingBottomBarDestination("main_host"))
        assertFalse(hasAudioNowPlayingConflictingBottomBarDestination("space/123"))

        assertFalse(
            resolveAudioNowPlayingVisible(
                sessionActive = true,
                isOnAudioModeScreen = false,
                isInPipMode = false,
                hasCurrentItem = true,
                barEnabled = true,
                hasOwnBottomBar = true,
            )
        )
    }
}
