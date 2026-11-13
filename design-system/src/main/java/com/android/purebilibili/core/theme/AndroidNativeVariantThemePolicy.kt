package com.android.purebilibili.core.theme

import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import com.android.purebilibili.core.ui.AppShapeTokens

internal const val MD3_CORNER_RADIUS_SCALE = AppShapeTokens.MaterialCornerRadiusScale
internal const val MIUIX_CORNER_RADIUS_SCALE = AppShapeTokens.MiuixCornerRadiusScale

data class AndroidNativeChromeTokens(
    val containerCornerRadiusDp: Int,
    val pillCornerRadiusDp: Int,
    val selectedContainerAlpha: Float,
    val tonalSurfaceElevationDp: Int,
    val denseHorizontalSpacingDp: Int,
    val rowMinTouchTargetDp: Int,
    val expressiveMotionDurationMillis: Int,
    val motionScale: Float,
    val motionStandardMillis: Int,
    val motionEmphasizedMillis: Int
)

fun resolveAndroidNativeChromeTokens(
    uiStyle: AppUiStyle
): AndroidNativeChromeTokens = when (uiStyle) {
    AppUiStyle.MIUIX -> AndroidNativeChromeTokens(
        containerCornerRadiusDp = 20,
        pillCornerRadiusDp = 22,
        selectedContainerAlpha = 0.18f,
        tonalSurfaceElevationDp = 0,
        denseHorizontalSpacingDp = 16,
        // Miuix controls use denser visual geometry. A 48dp visual minimum makes
        // buttons and preference rows look inflated; accessibility hit slop is
        // handled by the component instead of enlarging its visible container.
        rowMinTouchTargetDp = 44,
        expressiveMotionDurationMillis = 180,
        motionScale = 1f,
        motionStandardMillis = 180,
        motionEmphasizedMillis = 240
    )
    AppUiStyle.MATERIAL3 -> AndroidNativeChromeTokens(
        containerCornerRadiusDp = 24,
        pillCornerRadiusDp = 28,
        selectedContainerAlpha = 0.14f,
        tonalSurfaceElevationDp = 3,
        denseHorizontalSpacingDp = 18,
        rowMinTouchTargetDp = 48,
        expressiveMotionDurationMillis = 200,
        motionScale = 1f,
        motionStandardMillis = 200,
        motionEmphasizedMillis = 300
    )
}

fun resolveCornerRadiusScale(
    uiStyle: AppUiStyle
): Float = when (uiStyle) {
    AppUiStyle.MIUIX -> MIUIX_CORNER_RADIUS_SCALE
    AppUiStyle.MATERIAL3 -> MD3_CORNER_RADIUS_SCALE
}

fun shouldUseMiuixSmoothRounding(
    uiStyle: AppUiStyle
): Boolean = uiStyle == AppUiStyle.MIUIX

fun resolveMaterialTypography(
    uiStyle: AppUiStyle,
    liquidGlassEnabled: Boolean = true,
): Typography = when (uiStyle) {
    AppUiStyle.MIUIX -> BiliMiuixTypography
    AppUiStyle.MATERIAL3 -> Md3Typography
}

fun resolveMaterialMotionScheme(
    uiStyle: AppUiStyle
): MotionScheme = when (uiStyle) {
    // 普通 MD3 组件在两种玻璃状态下共用标准动效；玻璃 dock 自行管理材质反馈。
    AppUiStyle.MATERIAL3 -> MotionScheme.standard()
    AppUiStyle.MIUIX -> MotionScheme.standard()
}

fun resolveMaterialShapes(
    uiStyle: AppUiStyle
): Shapes = when (uiStyle) {
    AppUiStyle.MIUIX -> MiuixAlignedShapes
    AppUiStyle.MATERIAL3 -> Md3Shapes
}
