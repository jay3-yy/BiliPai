package com.android.purebilibili.core.ui.transition

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardTransitionBackgroundPolicyTest {

    @Test
    fun reducedMotionTierSkipsRealtimeBlurButKeepsOpeningScrimAndScale() {
        val opening = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Reduced,
            sdkInt = 35
        )
        val returning = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            motionTier = MotionTier.Reduced,
            sdkInt = 35
        )

        assertEquals(0f, opening.blurRadiusPx)
        assertTrue(opening.scrimAlpha > 0f)
        assertTrue(opening.contentScale < 1f)
        assertEquals(0f, returning.blurRadiusPx)
        assertEquals(0f, returning.scrimAlpha)
    }

    @Test
    fun api35OpeningFrameUsesOriginalBlurStrengthAndScrim() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            isLightBackground = false,
            sdkInt = 35
        )

        assertEquals(36f, frame.blurRadiusPx)
        assertEquals(0f, frame.blurRadiusPx % 2f)
        assertEquals(0.22f, frame.scrimAlpha)
        assertFalse(frame.useLightScrimTint)
        assertTrue(frame.contentScale < 1f)
    }

    @Test
    fun lightOpeningUsesReducedScrimAndWarmTint() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            isLightBackground = true,
            sdkInt = 35
        )

        assertEquals(36f, frame.blurRadiusPx)
        assertEquals(0.10f, frame.scrimAlpha)
        assertTrue(frame.useLightScrimTint)
    }

    @Test
    fun lightReducedMotionUsesMinimalOpeningScrimWithoutBlur() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Reduced,
            isLightBackground = true,
            sdkInt = 35
        )

        assertEquals(0f, frame.blurRadiusPx)
        assertEquals(0.06f, frame.scrimAlpha)
        assertTrue(frame.useLightScrimTint)
    }

    @Test
    fun lightReturningScrimPolicyStillPrefersLighterTintForBackgroundHelpers() {
        val light = resolveVideoCardTransitionReturningScrimAlpha(
            blurStrength = 1f,
            isLightBackground = true,
        )
        val dark = resolveVideoCardTransitionReturningScrimAlpha(
            blurStrength = 1f,
            isLightBackground = false,
        )

        assertTrue(light < dark)
        assertEquals(0.05f, light)
        assertEquals(0.10f, dark)
    }

    @Test
    fun returningFrameClearsBlurAndScrimWithoutAffectingCardLayer() {
        val start = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 35
        )
        val middle = resolveVideoCardTransitionBackgroundFrame(
            progress = 0.5f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 35
        )
        val end = resolveVideoCardTransitionBackgroundFrame(
            progress = 0f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 35
        )

        assertEquals(0f, start.blurRadiusPx)
        assertEquals(0f, middle.blurRadiusPx)
        assertEquals(0f, end.blurRadiusPx)
        assertEquals(0f, start.scrimAlpha)
        assertEquals(0f, middle.scrimAlpha)
        assertEquals(0f, end.scrimAlpha)
        assertEquals(1f, start.contentScale)
        assertEquals(1f, middle.contentScale)
        assertEquals(1f, end.contentScale)
    }

    @Test
    fun heldFrameKeepsBackgroundBlurReadyForReturnWithoutScrimOrScale() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.HELD,
            sdkInt = 35
        )

        assertEquals(36f, frame.blurRadiusPx)
        assertEquals(0f, frame.scrimAlpha)
        assertEquals(1f, frame.contentScale)
    }

    @Test
    fun idleFrameClearsBackgroundEffect() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.IDLE,
            sdkInt = 35
        )

        assertEquals(0f, frame.blurRadiusPx)
        assertEquals(0f, frame.scrimAlpha)
        assertEquals(1f, frame.contentScale)
    }

    @Test
    fun androidBeforeSDisablesRealtimeBlurButKeepsOpeningScrim() {
        val opening = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            sdkInt = 30
        )
        val returning = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 30
        )

        assertEquals(0f, opening.blurRadiusPx)
        assertTrue(opening.scrimAlpha > 0f)
        assertEquals(0f, returning.blurRadiusPx)
        assertEquals(0f, returning.scrimAlpha)
    }

    @Test
    fun lowProgressReturningFrameKeepsCardLayerClear() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 0.25f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 35
        )

        assertEquals(0f, frame.blurRadiusPx)
        assertEquals(0f, frame.scrimAlpha)
        assertEquals(1f, frame.contentScale)
    }

    @Test
    fun routeMatcherTargetsOnlyRecordedSourceEntryOrActiveMainHostPage() {
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "home",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "home",
                activeMainHostRoute = "dynamic"
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "search",
                sourceRoute = "search",
                activeMainHostRoute = "home"
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "space/123",
                sourceRoute = "space/123?from=archive",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "settings",
                sourceRoute = "home",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "video/BV1",
                sourceRoute = "video",
                activeMainHostRoute = "home"
            )
        )
    }

    @Test
    fun routeMatcherTreatsHomeCategoryAsActiveHomePageForRealtimeBlur() {
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "home?category=RECOMMEND",
                activeMainHostRoute = "home"
            )
        )
    }

    @Test
    fun gestureProgressMapsBackGestureToDecreasingBlurStartingFromFull() {
        // 手势起点保持满虚化，与 HELD 衔接；拖到底背景清晰；中途单调递减。
        assertEquals(1f, resolveVideoCardTransitionBackgroundGestureProgress(0f))
        assertEquals(0.5f, resolveVideoCardTransitionBackgroundGestureProgress(0.5f))
        assertEquals(0f, resolveVideoCardTransitionBackgroundGestureProgress(1f))
    }

    @Test
    fun gestureProgressClampsOutOfRangeBackProgress() {
        assertEquals(1f, resolveVideoCardTransitionBackgroundGestureProgress(-0.5f))
        assertEquals(0f, resolveVideoCardTransitionBackgroundGestureProgress(1.5f))
    }

    @Test
    fun returnDurationScalesWithRemainingBlurButKeepsMinimumFloor() {
        // 未消解(startProgress=1)时用完整时长；手势已消解一半则约减半；接近清晰时不低于取消时长下限。
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_RETURN_DURATION_MS,
            resolveVideoCardTransitionBackgroundReturnDurationMs(1f)
        )
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_RETURN_DURATION_MS / 2,
            resolveVideoCardTransitionBackgroundReturnDurationMs(0.5f)
        )
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_CANCEL_DURATION_MS,
            resolveVideoCardTransitionBackgroundReturnDurationMs(0f)
        )
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_CANCEL_DURATION_MS,
            resolveVideoCardTransitionBackgroundReturnDurationMs(0.05f)
        )
    }
}
