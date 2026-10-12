package com.android.purebilibili.feature.audio.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioNowPlayingBarPresenceMotionPolicyTest {

    @Test
    fun enterUsesASlightlyUnderdampedSpringForBounce() {
        val enter = resolveAudioNowPlayingPresenceEnterSpringSpec()
        assertEquals(0.7f, enter.dampingRatio)
        assertTrue(enter.stiffness > 0f)
        val enterGeometry = resolveAudioNowPlayingPresenceEnterGeometrySpringSpec()
        assertEquals(0.7f, enterGeometry.dampingRatio)
    }

    @Test
    fun exitUsesAsymmetricAccelerateCurve() {
        val exit = resolveAudioNowPlayingPresenceAnimationSpec(active = false, reduceMotion = false)
        assertEquals(AUDIO_NOW_PLAYING_PRESENCE_EXIT_DURATION_MILLIS, exit.durationMillis)
        assertEquals(0f, exit.easing.transform(0f))
        assertTrue(exit.easing.transform(0.5f) < 0.5f)
        assertEquals(1f, exit.easing.transform(1f))
    }

    @Test
    fun reduceMotionCollapsesBothDirectionsToShortFade() {
        val enter = resolveAudioNowPlayingPresenceAnimationSpec(active = true, reduceMotion = true)
        val exit = resolveAudioNowPlayingPresenceAnimationSpec(active = false, reduceMotion = true)
        assertEquals(AUDIO_NOW_PLAYING_PRESENCE_REDUCED_MOTION_DURATION_MILLIS, enter.durationMillis)
        assertEquals(AUDIO_NOW_PLAYING_PRESENCE_REDUCED_MOTION_DURATION_MILLIS, exit.durationMillis)
    }

    @Test
    fun alphaWindowKeepsShellInvisibleWhileGeometryLeads() {
        assertEquals(0f, resolveAudioNowPlayingPresenceAlpha(0f))
        assertEquals(0f, resolveAudioNowPlayingPresenceAlpha(0.25f))
        assertTrue(resolveAudioNowPlayingPresenceAlpha(0.5f) in 0f..1f)
        assertEquals(1f, resolveAudioNowPlayingPresenceAlpha(1f))
        // 越界输入被夹取。
        assertEquals(0f, resolveAudioNowPlayingPresenceAlpha(-0.3f))
        assertEquals(1f, resolveAudioNowPlayingPresenceAlpha(1.4f))
    }

    @Test
    fun geometryFactorFollowsProgressLinearlyAndClamps() {
        assertEquals(0f, resolveAudioNowPlayingPresenceGeometryFactor(0f))
        assertEquals(0.5f, resolveAudioNowPlayingPresenceGeometryFactor(0.5f))
        assertEquals(1f, resolveAudioNowPlayingPresenceGeometryFactor(1f))
        assertEquals(0f, resolveAudioNowPlayingPresenceGeometryFactor(-1f))
        assertEquals(1f, resolveAudioNowPlayingPresenceGeometryFactor(2f))
    }

}
