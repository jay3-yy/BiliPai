package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeReturnAnimationPolicyTest {

    @Test
    fun quickReturn_withTransition_usesSharedElementSoftLandingSuppressionOnPhone() {
        assertEquals(
            500L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = true,
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true
            )
        )
    }

    @Test
    fun quickReturn_withTransition_usesSharedElementSoftLandingSuppressionOnTablet() {
        assertEquals(
            540L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = true,
                cardAnimationEnabled = true,
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true
            )
        )
    }

    @Test
    fun normalReturn_usesOriginalDurations() {
        assertEquals(
            500L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = true,
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = false
            )
        )
        assertEquals(
            220L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = false,
                cardTransitionEnabled = false,
                isQuickReturnFromDetail = false
            )
        )
    }

    @Test
    fun nonSharedReturn_usesShorterSuppressionDurations() {
        assertEquals(
            240L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = true,
                cardTransitionEnabled = false,
                isQuickReturnFromDetail = false
            )
        )
        assertEquals(
            220L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = true,
                cardAnimationEnabled = true,
                cardTransitionEnabled = false,
                isQuickReturnFromDetail = false
            )
        )
    }

    @Test
    fun contentInteractionRestore_doesNotWaitForSharedElementSuppression() {
        assertEquals(
            0L,
            resolveHomeContentInteractionRestoreDelayMs(
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = false
            )
        )
        assertEquals(
            0L,
            resolveHomeContentInteractionRestoreDelayMs(
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true
            )
        )
        assertEquals(
            0L,
            resolveHomeContentInteractionRestoreDelayMs(
                cardTransitionEnabled = false,
                isQuickReturnFromDetail = false
            )
        )
    }

}
