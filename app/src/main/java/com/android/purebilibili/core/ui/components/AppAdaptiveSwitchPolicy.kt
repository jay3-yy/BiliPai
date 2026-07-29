package com.android.purebilibili.core.ui.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.ui.motion.BottomBarMotionProfile
import com.android.purebilibili.core.ui.motion.MotionSpringConfig
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec

internal enum class AppAdaptiveSwitchTreatment {
    MATERIAL,
    MIUIX,
    CUPERTINO,
    LIQUID_GLASS
}

internal fun resolveAppAdaptiveSwitchTreatment(
    uiPreset: UiPreset,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    settingsLiquidGlassEnabled: Boolean
): AppAdaptiveSwitchTreatment {
    return when {
        uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX -> AppAdaptiveSwitchTreatment.MIUIX
        uiPreset == UiPreset.MD3 -> AppAdaptiveSwitchTreatment.MATERIAL
        else -> AppAdaptiveSwitchTreatment.CUPERTINO
    }
}

internal data class LiquidSwitchThumbColors(
    val baseColor: Color,
    val topHighlightAlpha: Float,
    val edgeHighlightAlpha: Float,
    val greenRefractionAlpha: Float,
    val blueRefractionAlpha: Float
)

internal fun resolveLiquidSwitchThumbColors(highlightProgress: Float): LiquidSwitchThumbColors {
    return LiquidSwitchThumbColors(
        baseColor = Color.White,
        topHighlightAlpha = 0f,
        edgeHighlightAlpha = 0f,
        greenRefractionAlpha = 0f,
        blueRefractionAlpha = 0f
    )
}

internal fun resolveLiquidSwitchTrackColor(
    checked: Boolean,
    themePrimaryColor: Color,
    uncheckedTrackColor: Color
): Color {
    return if (checked) themePrimaryColor else uncheckedTrackColor
}

internal data class LiquidSwitchMotionSpec(
    val selectionSpring: MotionSpringConfig,
    val pressSpring: MotionSpringConfig,
    val indicatorScaleSpring: MotionSpringConfig,
    val deformationScaleXDelta: Float,
    val deformationScaleYCompressionRatio: Float
)

internal fun resolveLiquidSwitchMotionSpec(): LiquidSwitchMotionSpec {
    val bottomBarSpec = resolveBottomBarMotionSpec(BottomBarMotionProfile.IOS_FLOATING)
    return LiquidSwitchMotionSpec(
        selectionSpring = bottomBarSpec.drag.selectionSpring,
        pressSpring = bottomBarSpec.drag.pressSpring,
        indicatorScaleSpring = bottomBarSpec.indicator.scaleSpring,
        deformationScaleXDelta = bottomBarSpec.indicator.deformationScaleXDelta,
        deformationScaleYCompressionRatio = bottomBarSpec.indicator.deformationScaleYCompressionRatio
    )
}

internal data class LiquidSwitchThumbTransform(
    val scaleX: Float,
    val scaleY: Float
)

internal fun resolveLiquidSwitchThumbTransform(
    motionProgress: Float,
    motionSpec: LiquidSwitchMotionSpec
): LiquidSwitchThumbTransform {
    val progress = motionProgress.coerceIn(0f, 1f)
    val restrainedDeformation = motionSpec.deformationScaleXDelta * 0.52f * progress
    return LiquidSwitchThumbTransform(
        scaleX = 1f + restrainedDeformation,
        scaleY = 1f
    )
}

internal data class LiquidSwitchLayoutSpec(
    val containerWidthDp: Int,
    val containerHeightDp: Int,
    val trackWidthDp: Int,
    val trackHeightDp: Int,
    val trackOffsetYDp: Int,
    val thumbWidthDp: Int,
    val thumbHeightDp: Int,
    val thumbOffsetYDp: Int,
    val checkedThumbOffsetXDp: Int
)

internal fun resolveLiquidSwitchLayoutSpec(): LiquidSwitchLayoutSpec {
    return LiquidSwitchLayoutSpec(
        containerWidthDp = 56,
        containerHeightDp = 22,
        trackWidthDp = 56,
        trackHeightDp = 22,
        trackOffsetYDp = 0,
        thumbWidthDp = 34,
        thumbHeightDp = 22,
        thumbOffsetYDp = 0,
        checkedThumbOffsetXDp = 22
    )
}
