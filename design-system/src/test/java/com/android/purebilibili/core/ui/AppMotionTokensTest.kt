package com.android.purebilibili.core.ui

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.IntOffset

import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.resolveAndroidNativeChromeTokens
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.iosMorphTween
import com.android.purebilibili.core.ui.motion.navigationSlideSpring
import com.android.purebilibili.core.ui.motion.pullRefreshReleaseSpring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppMotionTokensTest {

    @Test
    fun iosMorphSpec_usesSharedAppleEaseInOutCurve() {
        val spec = iosMorphTween<Float>(260)

        assertEquals(260, spec.durationMillis)
        assertEquals(AppMotionEasing.IosEaseInOut, spec.easing)
    }

    @Test
    fun material3_standardSpec_isTween200ms() {
        val spec = AppMotionTokens.resolveStandardSpec<Float>(
            uiStyle = AppUiStyle.MATERIAL3
        )
        val tween = spec as? TweenSpec<Float>
            ?: error("expected TweenSpec, got ${spec::class.simpleName}")
        assertEquals(200, tween.durationMillis)
        assertEquals(com.android.purebilibili.core.ui.motion.AppMotionEasing.Md3Standard, tween.easing)
    }

    @Test
    fun miuix_standardSpec_isTween180ms() {
        val spec = AppMotionTokens.resolveStandardSpec<Float>(
            uiStyle = AppUiStyle.MIUIX
        )
        val tween = spec as? TweenSpec<Float>
            ?: error("expected TweenSpec, got ${spec::class.simpleName}")
        assertEquals(180, tween.durationMillis)
        assertEquals(com.android.purebilibili.core.ui.motion.AppMotionEasing.Continuity, tween.easing)
    }

    @Test
    fun material3_emphasizedSpec_isTween300ms() {
        val spec = AppMotionTokens.resolveEmphasizedSpec<Float>(
            uiStyle = AppUiStyle.MATERIAL3
        )
        val tween = spec as? TweenSpec<Float>
            ?: error("expected TweenSpec, got ${spec::class.simpleName}")
        assertEquals(300, tween.durationMillis)
        assertEquals(com.android.purebilibili.core.ui.motion.AppMotionEasing.Md3EmphasizedDecelerate, tween.easing)
    }

    @Test
    fun miuix_emphasizedSpec_isTween240ms() {
        val spec = AppMotionTokens.resolveEmphasizedSpec<Float>(
            uiStyle = AppUiStyle.MIUIX
        )
        val tween = spec as? TweenSpec<Float>
            ?: error("expected TweenSpec, got ${spec::class.simpleName}")
        assertEquals(240, tween.durationMillis)
    }

    @Test
    fun spatialSpec_keepsSharedElementSpringParameters() {
        val spec = AppMotionTokens.resolveSpatialSpec<androidx.compose.ui.geometry.Rect>()
        val spring = spec as? SpringSpec<androidx.compose.ui.geometry.Rect>
            ?: error("expected SpringSpec, got ${spec::class.simpleName}")

        assertEquals(0.82f, spring.dampingRatio, "spatial damping")
        assertEquals(380f, spring.stiffness, "spatial stiffness")
    }

    @Test
    fun chromeTokens_exposeMotionMillis() {
        val md3 = resolveAndroidNativeChromeTokens(AppUiStyle.MATERIAL3)
        val miuix = resolveAndroidNativeChromeTokens(AppUiStyle.MIUIX)

        assertEquals(200, md3.motionStandardMillis)
        assertEquals(300, md3.motionEmphasizedMillis)
        assertEquals(180, miuix.motionStandardMillis)
        assertEquals(240, miuix.motionEmphasizedMillis)
    }

    @Test
    fun material3_bottomSheetSlideSpec_usesStandardTween() {
        val spec = AppMotionTokens.resolveBottomSheetSlideSpec<Int>(
            uiStyle = AppUiStyle.MATERIAL3
        )
        val tween = spec as? TweenSpec<Int>
            ?: error("expected TweenSpec, got ${spec::class.simpleName}")
        assertEquals(200, tween.durationMillis)
    }

    @Test
    fun miuix_bottomSheetSlideSpec_usesDenserTween() {
        val spec = AppMotionTokens.resolveBottomSheetSlideSpec<Int>(
            uiStyle = AppUiStyle.MIUIX
        )
        val tween = spec as? TweenSpec<Int>
            ?: error("expected TweenSpec, got ${spec::class.simpleName}")
        assertEquals(180, tween.durationMillis)
    }

    @Test
    fun pullRefreshReleaseSpring_usesTightDampingForSmallRebound() {
        val spring = pullRefreshReleaseSpring()

        assertTrue(spring.dampingRatio >= 0.94f, "pull refresh release should not visibly bounce")
        assertTrue(spring.stiffness >= 480f, "pull refresh release should settle quickly")
    }

    @Test
    fun navigationSlideSpring_scalesStiffnessWithTargetDuration() {
        val fast = navigationSlideSpring(durationMillis = 220)
        val standard = navigationSlideSpring(durationMillis = 300)
        val slow = navigationSlideSpring(durationMillis = 350)

        assertEquals(1f, fast.dampingRatio)
        assertTrue(fast.stiffness > standard.stiffness)
        assertTrue(standard.stiffness > slow.stiffness)
        assertTrue(fast.stiffness >= 1_700f)
        assertTrue(standard.stiffness >= 900f)
        assertEquals(700f, slow.stiffness)
        assertEquals(IntOffset(1, 1), slow.visibilityThreshold)
    }
}
