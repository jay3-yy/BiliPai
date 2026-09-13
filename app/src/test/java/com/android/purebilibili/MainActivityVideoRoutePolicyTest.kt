package com.android.purebilibili

import kotlin.test.Test
import kotlin.test.assertEquals

class MainActivityVideoRoutePolicyTest {

    @Test
    fun notificationRoute_defaultsToAutoPortraitAndStartAudioFalse() {
        assertEquals(
            "video/BV1abc?cid=0&cover=&startAudio=false&autoPortrait=true&fullscreen=false&resumePositionMs=0&commentRootRpid=0&commentTargetRpid=0",
            resolveMainActivityVideoRoute(bvid = "BV1abc", cid = 0L)
        )
    }

    @Test
    fun notificationRoute_keepsCidValue() {
        assertEquals(
            "video/BV2xyz?cid=12345&cover=&startAudio=false&autoPortrait=true&fullscreen=false&resumePositionMs=0&commentRootRpid=0&commentTargetRpid=0",
            resolveMainActivityVideoRoute(bvid = "BV2xyz", cid = 12345L)
        )
    }

    @Test
    fun notificationRoute_canRequestStartInFullscreen() {
        assertEquals(
            "video/BV9full?cid=42&cover=&startAudio=false&autoPortrait=true&fullscreen=true&resumePositionMs=0&commentRootRpid=0&commentTargetRpid=0",
            resolveMainActivityVideoRoute(
                bvid = "BV9full",
                cid = 42L,
                startFullscreen = true
            )
        )
    }

    @Test
    fun miniPlayerExpandRoute_requestsFullscreenPlayback() {
        assertEquals(
            "video/BVmini?cid=88&cover=&startAudio=false&autoPortrait=true&fullscreen=true&resumePositionMs=0&commentRootRpid=0&commentTargetRpid=0",
            resolveMiniPlayerExpandVideoRoute(bvid = "BVmini", cid = 88L)
        )
    }

    @Test
    fun activePlaybackReturnRoute_keepsExactCidAndLivePosition() {
        assertEquals(
            "video/BVactive?cid=2468&cover=&startAudio=false&autoPortrait=true&fullscreen=false&resumePositionMs=38123&commentRootRpid=0&commentTargetRpid=0",
            resolveActivePlaybackReturnRoute(
                isActive = true,
                bvid = "BVactive",
                cid = 2468L,
                currentPositionMs = 38123L,
            )
        )
    }

    @Test
    fun activePlaybackReturnRoute_rejectsIncompleteSession() {
        assertEquals(
            null,
            resolveActivePlaybackReturnRoute(
                isActive = true,
                bvid = "BVactive",
                cid = 0L,
                currentPositionMs = 38123L,
            )
        )
    }
}
