package com.android.purebilibili.core.ui.transition

import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiNavRouteTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredictiveBackBackgroundPolicyTest {

    @Test
    fun gestureProgressMapsBackGestureToDecreasingBlur() {
        assertEquals(1f, resolvePredictiveBackGestureBlurProgress(0f))
        assertEquals(0.5f, resolvePredictiveBackGestureBlurProgress(0.5f))
        assertEquals(0f, resolvePredictiveBackGestureBlurProgress(1f))
    }

    @Test
    fun gestureProgressClampsOutOfRangeBackProgress() {
        assertEquals(1f, resolvePredictiveBackGestureBlurProgress(-0.5f))
        assertEquals(0f, resolvePredictiveBackGestureBlurProgress(1.5f))
    }

    @Test
    fun settingsIosPushPopGestureProgressMatchesUnifiedDecreasingBlur() {
        assertEquals(1f, resolvePredictiveBackGestureBlurProgress(
            backProgress = 0f,
            routeTransition = BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
        ))
        assertEquals(0.5f, resolvePredictiveBackGestureBlurProgress(
            backProgress = 0.5f,
            routeTransition = BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
        ))
        assertEquals(0f, resolvePredictiveBackGestureBlurProgress(
            backProgress = 1f,
            routeTransition = BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
        ))
        // CLASSIC_CARD 与设置同向：不再越拖越糊。
        assertEquals(
            resolvePredictiveBackGestureBlurProgress(
                backProgress = 0.5f,
                routeTransition = BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
            ),
            resolvePredictiveBackGestureBlurProgress(
                backProgress = 0.5f,
                routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
            ),
        )
    }

    @Test
    fun blurFrameUsesQuantizedRadiusOnApi31Plus_darkTheme() {
        val frame = resolvePredictiveBackBlurFrame(
            progress = 1f,
            motionTier = MotionTier.Normal,
            isLightBackground = false,
            sdkInt = 35,
        )

        assertEquals(28f, frame.blurRadiusPx)
        assertEquals(0f, frame.blurRadiusPx % 2f)
        assertEquals(0f, frame.separationTintAlpha)
    }

    @Test
    fun blurFrameUsesLowerRadiusAndSeparationTintInLightTheme() {
        val frame = resolvePredictiveBackBlurFrame(
            progress = 1f,
            motionTier = MotionTier.Normal,
            isLightBackground = true,
            sdkInt = 35,
        )

        assertEquals(22f, frame.blurRadiusPx)
        assertEquals(0.05f, frame.separationTintAlpha)
        assertTrue(frame.useLightSeparationTint)
    }

    @Test
    fun lightSeparationTintScalesWithGestureProgress() {
        assertEquals(0f, resolvePredictiveBackSeparationTintAlpha(0f, isLightBackground = true))
        assertEquals(0.025f, resolvePredictiveBackSeparationTintAlpha(0.5f, isLightBackground = true))
        assertEquals(0f, resolvePredictiveBackSeparationTintAlpha(0.5f, isLightBackground = false))
    }

    @Test
    fun reducedMotionTierSkipsPredictiveBlur() {
        val frame = resolvePredictiveBackBlurFrame(
            progress = 1f,
            motionTier = MotionTier.Reduced,
            sdkInt = 35,
        )

        assertEquals(0f, frame.blurRadiusPx)
    }

    @Test
    fun shouldApplyPredictiveBlur_onlyForClassicCardWhenEnabled() {
        assertTrue(
            shouldApplyPredictiveBackGestureBlur(
                routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
                predictiveBackEnabled = true,
                gestureReturningVideoCard = false,
                motionTier = MotionTier.Normal,
            )
        )
        assertFalse(
            shouldApplyPredictiveBackGestureBlur(
                routeTransition = BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT,
                predictiveBackEnabled = true,
                gestureReturningVideoCard = false,
                motionTier = MotionTier.Normal,
            )
        )
        assertFalse(
            shouldApplyPredictiveBackGestureBlur(
                routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
                predictiveBackEnabled = true,
                gestureReturningVideoCard = true,
                motionTier = MotionTier.Normal,
            )
        )
        assertFalse(
            shouldApplyPredictiveBackGestureBlur(
                routeTransition = BiliPaiNavRouteTransition.CLASSIC_CARD,
                predictiveBackEnabled = false,
                gestureReturningVideoCard = false,
                motionTier = MotionTier.Normal,
            )
        )
    }

    @Test
    fun shouldApplyPredictiveBlur_forSiblingPopTransitionsWhenEnabled() {
        assertTrue(
            shouldApplyPredictiveBackGestureBlur(
                routeTransition = BiliPaiNavRouteTransition.BOTTOM_BAR_SIBLING_POP,
                predictiveBackEnabled = true,
                gestureReturningVideoCard = false,
                motionTier = MotionTier.Normal,
            )
        )
        assertTrue(
            shouldApplyPredictiveBackGestureBlur(
                routeTransition = BiliPaiNavRouteTransition.LIGHT_SIBLING_POP,
                predictiveBackEnabled = true,
                gestureReturningVideoCard = false,
                motionTier = MotionTier.Normal,
            )
        )
    }

    @Test
    fun shouldApplyPredictiveBlur_skipsSettingsIosPushPop() {
        assertFalse(
            shouldApplyPredictiveBackGestureBlur(
                routeTransition = BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
                predictiveBackEnabled = true,
                gestureReturningVideoCard = false,
                motionTier = MotionTier.Normal,
            )
        )
    }

    @Test
    fun routeMatcherKeepsDeferredBlurModifierOnlyOnTargetBackKey() {
        assertTrue(
            shouldApplyPredictiveBackBlurToRoute(
                entryKey = BiliPaiNavKey.MainHost,
                targetBackKey = BiliPaiNavKey.MainHost,
            )
        )
        assertFalse(
            shouldApplyPredictiveBackBlurToRoute(
                entryKey = BiliPaiNavKey.MainHost,
                targetBackKey = BiliPaiNavKey.MainHost,
                videoCardBackgroundApplied = true,
            )
        )
        assertTrue(
            shouldApplyPredictiveBackBlurToRoute(
                entryKey = BiliPaiNavKey.Search(),
                targetBackKey = BiliPaiNavKey.Search(),
            )
        )
        assertFalse(
            shouldApplyPredictiveBackBlurToRoute(
                entryKey = BiliPaiNavKey.Search(),
                targetBackKey = BiliPaiNavKey.MainHost,
            )
        )
        assertTrue(
            shouldApplyPredictiveBackBlurToRoute(
                entryKey = BiliPaiNavKey.MainHost,
                targetBackKey = BiliPaiNavKey.MainHost,
            )
        )
    }

    @Test
    fun commitBlurDurationScalesWithRemainingBlur() {
        assertEquals(
            PREDICTIVE_BACK_BACKGROUND_COMMIT_DURATION_MS,
            resolvePredictiveBackCommitBlurDurationMs(1f),
        )
        assertEquals(
            PREDICTIVE_BACK_BACKGROUND_COMMIT_DURATION_MS / 2,
            resolvePredictiveBackCommitBlurDurationMs(0.5f),
        )
        assertEquals(
            PREDICTIVE_BACK_BACKGROUND_CANCEL_DURATION_MS,
            resolvePredictiveBackCommitBlurDurationMs(0f),
        )
    }
}
