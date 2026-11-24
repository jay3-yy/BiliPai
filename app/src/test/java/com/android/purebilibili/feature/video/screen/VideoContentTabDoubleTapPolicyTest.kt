package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoContentTabDoubleTapPolicyTest {

    @Test
    fun `isVideoContentTabDoubleTap triggers only on rapid second tap of already selected tab`() {
        // Switching from intro (0) to comment (1) is not a double-tap.
        assertFalse(
            isVideoContentTabDoubleTap(
                selectedTabIndex = 0,
                tappedTabIndex = 1,
                lastTappedTabIndex = 1,
                lastTapAtMs = 1_000L,
                nowMs = 1_150L,
            )
        )
        // First tap on already selected comment tab (1) is not a double-tap yet.
        assertFalse(
            isVideoContentTabDoubleTap(
                selectedTabIndex = 1,
                tappedTabIndex = 1,
                lastTappedTabIndex = -1,
                lastTapAtMs = 0L,
                nowMs = 1_000L,
            )
        )
        // Second tap within window triggers scroll-to-top.
        assertTrue(
            isVideoContentTabDoubleTap(
                selectedTabIndex = 1,
                tappedTabIndex = 1,
                lastTappedTabIndex = 1,
                lastTapAtMs = 1_000L,
                nowMs = 1_280L,
            )
        )
        // Second tap outside window does not trigger.
        assertFalse(
            isVideoContentTabDoubleTap(
                selectedTabIndex = 1,
                tappedTabIndex = 1,
                lastTappedTabIndex = 1,
                lastTapAtMs = 1_000L,
                nowMs = 1_500L,
            )
        )
    }
}
