package com.android.purebilibili.core.ui.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.ui.motion.BottomBarMotionProfile
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppAdaptiveSwitchPolicyTest {

    @Test
    fun `ios liquid glass settings use cupertino switch treatment`() {
        assertEquals(
            AppAdaptiveSwitchTreatment.CUPERTINO,
            resolveAppAdaptiveSwitchTreatment(
                uiPreset = UiPreset.IOS,
                settingsLiquidGlassEnabled = true
            )
        )
    }

    @Test
    fun `ios without liquid glass keeps cupertino switch treatment`() {
        assertEquals(
            AppAdaptiveSwitchTreatment.CUPERTINO,
            resolveAppAdaptiveSwitchTreatment(
                uiPreset = UiPreset.IOS,
                settingsLiquidGlassEnabled = false
            )
        )
    }

    @Test
    fun `md3 keeps material switch treatment even when liquid glass is enabled`() {
        assertEquals(
            AppAdaptiveSwitchTreatment.MATERIAL,
            resolveAppAdaptiveSwitchTreatment(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MATERIAL3,
                settingsLiquidGlassEnabled = true
            )
        )
    }

    @Test
    fun `android native miuix variant uses miuix switch treatment`() {
        assertEquals(
            AppAdaptiveSwitchTreatment.MIUIX,
            resolveAppAdaptiveSwitchTreatment(
                uiPreset = UiPreset.MD3,
                androidNativeVariant = AndroidNativeVariant.MIUIX,
                settingsLiquidGlassEnabled = true
            )
        )
    }

    @Test
    fun `liquid switch rests with pure white thumb and no highlight`() {
        val colors = resolveLiquidSwitchThumbColors(highlightProgress = 0f)

        assertEquals(Color.White, colors.baseColor)
        assertEquals(0f, colors.topHighlightAlpha)
        assertEquals(0f, colors.edgeHighlightAlpha)
        assertEquals(0f, colors.greenRefractionAlpha)
        assertEquals(0f, colors.blueRefractionAlpha)
    }

    @Test
    fun `liquid switch keeps thumb pure white during click pulse`() {
        val colors = resolveLiquidSwitchThumbColors(highlightProgress = 1f)

        assertEquals(Color.White, colors.baseColor)
        assertEquals(0f, colors.topHighlightAlpha)
        assertEquals(0f, colors.edgeHighlightAlpha)
        assertEquals(0f, colors.greenRefractionAlpha)
        assertEquals(0f, colors.blueRefractionAlpha)
    }

    @Test
    fun `liquid switch shell shrinks around reference inner capsule`() {
        val layout = resolveLiquidSwitchLayoutSpec()

        assertEquals(56, layout.trackWidthDp)
        assertEquals(22, layout.trackHeightDp)
        assertEquals(layout.trackHeightDp, layout.containerHeightDp)
        assertEquals(0, layout.trackOffsetYDp)
        assertEquals(34, layout.thumbWidthDp)
        assertEquals(22, layout.thumbHeightDp)
        assertEquals(0, layout.thumbOffsetYDp)
        assertEquals(22, layout.checkedThumbOffsetXDp)
        assertEquals(0, layout.trackWidthDp - layout.checkedThumbOffsetXDp - layout.thumbWidthDp)
        assertEquals(0, layout.thumbOffsetYDp)
        assertEquals(0, layout.trackHeightDp - layout.thumbOffsetYDp - layout.thumbHeightDp)
    }

    @Test
    fun `liquid switch checked track follows current theme primary color`() {
        val themePrimary = Color(0xFF6750A4)
        val uncheckedTrack = Color(0xFFE9E9EA)

        assertEquals(
            themePrimary,
            resolveLiquidSwitchTrackColor(
                checked = true,
                themePrimaryColor = themePrimary,
                uncheckedTrackColor = uncheckedTrack
            )
        )
        assertEquals(
            uncheckedTrack,
            resolveLiquidSwitchTrackColor(
                checked = false,
                themePrimaryColor = themePrimary,
                uncheckedTrackColor = uncheckedTrack
            )
        )
    }

    @Test
    fun `liquid switch motion matches ios floating bottom bar indicator motion`() {
        val bottomBarSpec = resolveBottomBarMotionSpec(BottomBarMotionProfile.IOS_FLOATING)
        val switchSpec = resolveLiquidSwitchMotionSpec()

        assertEquals(bottomBarSpec.drag.selectionSpring, switchSpec.selectionSpring)
        assertEquals(bottomBarSpec.drag.pressSpring, switchSpec.pressSpring)
        assertEquals(bottomBarSpec.indicator.scaleSpring, switchSpec.indicatorScaleSpring)
        assertEquals(bottomBarSpec.indicator.deformationScaleXDelta, switchSpec.deformationScaleXDelta)
        assertEquals(
            bottomBarSpec.indicator.deformationScaleYCompressionRatio,
            switchSpec.deformationScaleYCompressionRatio
        )
    }

    @Test
    fun `liquid switch thumb slightly enlarges during click pulse`() {
        val motionSpec = resolveLiquidSwitchMotionSpec()
        val resting = resolveLiquidSwitchThumbTransform(0f, motionSpec)
        val moving = resolveLiquidSwitchThumbTransform(1f, motionSpec)

        assertEquals(1f, resting.scaleX)
        assertEquals(1f, resting.scaleY)
        assertTrue(moving.scaleX > 1f)
        assertTrue(moving.scaleX > 1.20f)
        assertTrue(moving.scaleX < 1.24f)
        assertEquals(1f, moving.scaleY)
    }
}
