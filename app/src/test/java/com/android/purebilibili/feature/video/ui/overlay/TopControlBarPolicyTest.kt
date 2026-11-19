package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopControlBarPolicyTest {
    @Test
    fun portraitInline_showsAudioAndCastWithoutTitleOrStatus() {
        val policy = resolveTopControlBarLayoutPolicy(isPortrait = true, isFullscreen = false)
        assertFalse(policy.showTitle)
        assertFalse(policy.showStatus)
        assertTrue(policy.showAudio)
        assertTrue(policy.showCast)
        assertFalse(policy.showDanmakuActions)
        assertFalse(policy.showEngagement)
    }

    @Test
    fun portraitFullscreen_showsTitleDanmakuAndSecondRowEngagement() {
        val policy = resolveTopControlBarLayoutPolicy(isPortrait = true, isFullscreen = true)
        assertTrue(policy.showTitle)
        assertFalse(policy.showStatus)
        assertFalse(policy.showAudio)
        assertFalse(policy.showCast)
        assertTrue(policy.showDanmakuActions)
        assertTrue(policy.showEngagement)
        assertTrue(policy.showDislike)
    }

    @Test
    fun landscapeInline_horizontalScreen_hidesTitleStatusAndEngagement() {
        val policy = resolveTopControlBarLayoutPolicy(isPortrait = false, isFullscreen = false)
        assertFalse(policy.showTitle)
        assertFalse(policy.showStatus)
        assertTrue(policy.showAudio)
        assertTrue(policy.showCast)
        assertTrue(policy.showDanmakuActions)
        assertFalse(policy.showEngagement)
    }

    @Test
    fun landscapeFullscreen_showsTitleStatusAndEngagementWithoutCastOrAudio() {
        val policy = resolveTopControlBarLayoutPolicy(isPortrait = false, isFullscreen = true)
        assertTrue(policy.showTitle)
        assertTrue(policy.showStatus)
        assertFalse(policy.showAudio)
        assertFalse(policy.showCast)
        assertTrue(policy.showDanmakuActions)
        assertTrue(policy.showEngagement)
    }

    @Test
    fun landscapeInline_withoutHorizontalScreen_alsoShowsTitleAndStatus() {
        val policy = resolveTopControlBarLayoutPolicy(
            isPortrait = false, isFullscreen = false, horizontalScreen = false,
        )
        assertTrue(policy.showTitle)
        assertTrue(policy.showStatus)
    }

    @Test
    fun localFiles_hideNetworkActionsAndEngagement() {
        for (fullscreen in listOf(false, true)) {
            val policy = resolveTopControlBarLayoutPolicy(
                isPortrait = false, isFullscreen = fullscreen, isFileSource = true,
            )
            assertFalse(policy.showAudio)
            assertFalse(policy.showCast)
            assertFalse(policy.showEngagement)
            assertTrue(policy.showDanmakuActions)
        }
    }

    @Test
    fun pgcHasNoAudioPageOrDislikeButRetainsFullscreenEngagement() {
        val inline = resolveTopControlBarLayoutPolicy(isPortrait = true, isFullscreen = false, isUgc = false)
        val fullscreen = resolveTopControlBarLayoutPolicy(isPortrait = true, isFullscreen = true, isUgc = false)
        assertFalse(inline.showAudio)
        assertTrue(inline.showCast)
        assertFalse(fullscreen.showDislike)
        assertTrue(fullscreen.showEngagement)
    }

    @Test
    fun fullscreenEngagementPreference_controlsBothOrientations() {
        for (portrait in listOf(false, true)) {
            val policy = resolveTopControlBarLayoutPolicy(
                isPortrait = portrait, isFullscreen = true, showFullscreenActionItems = false,
            )
            assertFalse(policy.showEngagement)
        }
    }

    @Test
    fun statusPolling_requiresBothVisibleStatusAndStartedHost() {
        for (visible in listOf(false, true)) {
            for (started in listOf(false, true)) {
                val expected = visible && started
                kotlin.test.assertEquals(expected, shouldPollTopControlBarClock(visible, started))
                kotlin.test.assertEquals(expected, shouldPollTopControlBarBattery(visible, started))
            }
        }
    }
}
