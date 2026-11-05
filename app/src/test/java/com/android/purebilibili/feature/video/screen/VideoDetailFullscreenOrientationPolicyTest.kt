package com.android.purebilibili.feature.video.screen

import android.content.pm.ActivityInfo
import com.android.purebilibili.core.store.FullscreenMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailFullscreenOrientationPolicyTest {

    @Test
    fun portraitPagerExcludesLandscapeFullscreenEvenBeforeConfigurationRotates() {
        for (orientationDriven in listOf(false, true)) {
            for (manualRequest in listOf(false, true)) {
                for (smallWindow in listOf(false, true)) {
                    assertFalse(resolveVideoDetailFullscreenMode(
                        isOrientationDrivenFullscreen = orientationDriven,
                        isLandscape = true,
                        userRequestedFullscreen = manualRequest,
                        isInMultiWindowMode = smallWindow,
                        isPortraitFullscreen = true,
                    ))
                }
            }
        }
    }

    @Test
    fun `phone small window detail entry stays portrait despite stale landscape bounds`() {
        for (autoRotate in listOf(false, true)) {
            for (mode in listOf(FullscreenMode.AUTO, FullscreenMode.HORIZONTAL)) {
                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    resolvePhoneVideoRequestedOrientation(
                        autoRotateEnabled = autoRotate,
                        fullscreenMode = mode,
                        isCompactDevice = true,
                        isOrientationDrivenFullscreen = true,
                        isFullscreenMode = false,
                        manualFullscreenRequested = false,
                        isInMultiWindowMode = true,
                        isCurrentlyLandscape = true,
                        currentRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    )
                )
            }
        }
    }

    @Test
    fun `explicit fullscreen still requests landscape inside a phone small window`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolvePhoneVideoRequestedOrientation(
                autoRotateEnabled = true,
                fullscreenMode = FullscreenMode.HORIZONTAL,
                isCompactDevice = true,
                isOrientationDrivenFullscreen = true,
                isFullscreenMode = false,
                manualFullscreenRequested = true,
                isInMultiWindowMode = true,
                currentRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            )
        )
    }

    @Test
    fun `right landscape entry remains exact before fullscreen configuration arrives`() {
        for (appAutoRotate in listOf(false, true)) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                resolvePhoneVideoRequestedOrientation(
                    autoRotateEnabled = appAutoRotate,
                    systemAutoRotateEnabled = true,
                    fullscreenMode = FullscreenMode.HORIZONTAL,
                    isCompactDevice = true,
                    isOrientationDrivenFullscreen = true,
                    isFullscreenMode = false,
                    manualFullscreenRequested = true,
                    currentRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                )
            )
        }
    }

    @Test
    fun `manual fullscreen tracks both sides with app auto rotate off and system rotation on`() {
        assertTrue(shouldObservePhoneAutoRotate(
            autoRotateEnabled = false,
            systemAutoRotateEnabled = true,
            isCompactDevice = true,
            isOrientationDrivenFullscreen = true,
            fullscreenMode = FullscreenMode.HORIZONTAL,
            manualPortraitHoldActive = false,
            manualFullscreenRequested = true,
            isFullscreenMode = false,
        ))
        var request = requireNotNull(resolvePhoneVideoRequestedOrientation(
            autoRotateEnabled = false,
            systemAutoRotateEnabled = true,
            fullscreenMode = FullscreenMode.HORIZONTAL,
            isCompactDevice = true,
            isOrientationDrivenFullscreen = true,
            isFullscreenMode = false,
            manualFullscreenRequested = true,
            currentRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, request)
        for ((degrees, expected) in listOf(
            90 to ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            270 to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            90 to ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        )) {
            request = requireNotNull(resolvePhoneAutoRotateRequestedOrientation(
                orientationDegrees = degrees,
                isCurrentlyLandscape = true,
                allowPortraitTransitions = false,
            ))
            assertFalse(shouldReleaseManualFullscreenRequestAfterSensorTarget(
                manualFullscreenRequested = true,
                sensorTargetOrientation = request,
                autoRotateEnabled = false,
            ))
            // Configuration can still report portrait while the first request is pending.
            for (fullscreen in listOf(false, true)) {
                request = requireNotNull(resolvePhoneVideoRequestedOrientation(
                    autoRotateEnabled = false,
                    systemAutoRotateEnabled = true,
                    fullscreenMode = FullscreenMode.HORIZONTAL,
                    isCompactDevice = true,
                    isOrientationDrivenFullscreen = true,
                    isFullscreenMode = fullscreen,
                    manualFullscreenRequested = true,
                    currentRequestedOrientation = request,
                ))
                assertEquals(expected, request)
            }
        }
        assertNull(resolvePhoneAutoRotateRequestedOrientation(
            orientationDegrees = 0,
            isCurrentlyLandscape = true,
            allowPortraitTransitions = false,
        ))
    }

    @Test
    fun `landscape hold suppresses tilt back to portrait while landscape`() {
        // 关闭保持：横屏中倾到正上退出到竖屏（既有行为）。
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolvePhoneAutoRotateRequestedOrientation(
                orientationDegrees = 0,
                isCurrentlyLandscape = true,
            )
        )
        // 开启保持：同一姿态不再产生竖屏请求，窗口维持横屏，仅手动退出。
        assertNull(
            resolvePhoneAutoRotateRequestedOrientation(
                orientationDegrees = 0,
                isCurrentlyLandscape = true,
                landscapeHoldEnabled = true,
            )
        )
        // 尚未进入横屏时保持不生效：竖屏稳定姿态仍按原路径进入横屏。
        val entry = requireNotNull(
            resolvePhoneAutoRotateRequestedOrientation(
                orientationDegrees = 90,
                isCurrentlyLandscape = false,
                landscapeHoldEnabled = true,
            )
        )
        assertTrue(isLandscapeRequestedOrientation(entry))
    }

    @Test
    fun `app auto rotate remains available under system rotation lock`() {
        for (appAutoRotate in listOf(false, true)) {
            assertEquals(appAutoRotate, shouldObservePhoneAutoRotate(
                autoRotateEnabled = appAutoRotate,
                systemAutoRotateEnabled = false,
                isCompactDevice = true,
                isOrientationDrivenFullscreen = true,
                fullscreenMode = FullscreenMode.HORIZONTAL,
                manualPortraitHoldActive = false,
                manualFullscreenRequested = true,
                isFullscreenMode = true,
            ))
        }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            resolvePhoneVideoRequestedOrientation(
                autoRotateEnabled = false,
                systemAutoRotateEnabled = false,
                fullscreenMode = FullscreenMode.HORIZONTAL,
                isCompactDevice = true,
                isOrientationDrivenFullscreen = true,
                isFullscreenMode = false,
                manualFullscreenRequested = true,
            )
        )
    }

    @Test
    fun `auto rotate target protects against oscillation in both directions`() {
        val nowMs = 1000L

        // 1. Just switched to landscape -> portrait request is suppressed during settle window
        assertNull(
            resolvePhoneAutoRotateTargetToApply(
                candidateOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                lastLandscapeAppliedAtMs = 800L,
                nowMs = nowMs,
                landscapeSettleMs = 500L,
            )
        )

        // 2. Just switched to portrait -> landscape request is suppressed during settle window (fixes #782 loop)
        assertNull(
            resolvePhoneAutoRotateTargetToApply(
                candidateOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                lastLandscapeAppliedAtMs = null,
                nowMs = nowMs,
                lastPortraitAppliedAtMs = 800L,
                portraitSettleMs = 500L,
            )
        )

        // 3. Reverse landscape is also suppressed during portrait settle window
        assertNull(
            resolvePhoneAutoRotateTargetToApply(
                candidateOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                lastLandscapeAppliedAtMs = null,
                nowMs = nowMs,
                lastPortraitAppliedAtMs = 800L,
                portraitSettleMs = 500L,
            )
        )

        // 4. After settle window passes, requests are granted
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolvePhoneAutoRotateTargetToApply(
                candidateOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                lastLandscapeAppliedAtMs = 400L,
                nowMs = nowMs,
                landscapeSettleMs = 500L,
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolvePhoneAutoRotateTargetToApply(
                candidateOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                lastLandscapeAppliedAtMs = null,
                nowMs = nowMs,
                lastPortraitAppliedAtMs = 400L,
                portraitSettleMs = 500L,
            )
        )
    }

    @Test
    fun `exact landscape orientation resolution extracts specific horizontal side`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            resolveCurrentExactLandscapeOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            resolveCurrentExactLandscapeOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
        )
        assertNull(
            resolveCurrentExactLandscapeOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        )
        assertNull(
            resolveCurrentExactLandscapeOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        )
    }

    @Test
    fun `continuous transition awaiting portrait does not rearm landscape on repeated toggle`() {
        val decision = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.AwaitingPortrait,
            event = ContinuousPlayerTransitionEvent.Toggle,
        )

        assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, decision.phase)
        assertEquals(ContinuousPlayerOrientationRequest.Portrait, decision.orientationRequest)
    }
}
