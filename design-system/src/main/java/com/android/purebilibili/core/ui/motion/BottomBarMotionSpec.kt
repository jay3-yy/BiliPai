package com.android.purebilibili.core.ui.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

data class MotionSpringConfig(
    val dampingRatio: Float,
    val stiffness: Float,
    val visibilityThreshold: Float? = null
) {
    fun toSpringSpec(): SpringSpec<Float> {
        return spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
            visibilityThreshold = visibilityThreshold
        )
    }
}

data class BottomBarDragMotionSpec(
    val baseResistance: Float,
    val overscrollResistance: Float,
    val overscrollLimitItems: Float,
    val flingProjectionTimeSeconds: Float,
    val maxReleaseStepCount: Int,
    val pressSpring: MotionSpringConfig,
    val selectionSpring: MotionSpringConfig,
    val offsetSnapSpring: MotionSpringConfig
)

data class BottomBarRefractionMotionSpec(
    val movingVelocityThresholdPxPerSecond: Float,
    val speedProgressDivisorPxPerSecond: Float,
    val dragProgressFloor: Float,
    val motionDeadzone: Float,
    val panelOffsetMaxDp: Float
)

data class BottomBarIndicatorMotionSpec(
    val deformationScaleXDelta: Float,
    val deformationScaleYCompressionRatio: Float,
    val scaleSpring: MotionSpringConfig,
    val scaleYSpring: MotionSpringConfig,
    val lensVelocityRangePxPerSecond: Float,
    val railFractionStretchMultiplier: Float,
    val capsuleVelocityNormalizationDivisor: Float,
    val capsuleVelocityScaleXMultiplier: Float,
    val capsuleVelocityScaleYMultiplier: Float,
    val capsuleVelocityClamp: Float
)

data class BottomBarMotionSpec(
    val drag: BottomBarDragMotionSpec,
    val refraction: BottomBarRefractionMotionSpec,
    val indicator: BottomBarIndicatorMotionSpec
)

// 照搬 HyperIsland LiquidGlassNavigationBar 的按下放大弹簧：
// scaleX = spring(0.6f, 250f, 0.001f)，scaleY = spring(0.7f, 250f, 0.001f)。
// 四套 profile 共用同一组放大弹簧，避免各自维护一套更长的放大时长。
private val hyperIslandIndicatorScaleXSpring = MotionSpringConfig(
    dampingRatio = 0.6f,
    stiffness = 250f,
    visibilityThreshold = 0.001f
)
private val hyperIslandIndicatorScaleYSpring = MotionSpringConfig(
    dampingRatio = 0.7f,
    stiffness = 250f,
    visibilityThreshold = 0.001f
)

enum class BottomBarMotionProfile {
    DEFAULT,
    IOS_FLOATING,
    ANDROID_NATIVE_FLOATING,
    MIUI_FLOATING
}

fun resolveBottomBarMotionSpec(
    profile: BottomBarMotionProfile = BottomBarMotionProfile.DEFAULT
): BottomBarMotionSpec {
    val base = createDefaultBottomBarMotionSpec()
    return when (profile) {
        BottomBarMotionProfile.DEFAULT -> base
        BottomBarMotionProfile.IOS_FLOATING -> base.copy(
            drag = base.drag.copy(
                baseResistance = 1f,
                overscrollResistance = 0.34f,
                flingProjectionTimeSeconds = 0.24f,
                selectionSpring = MotionSpringConfig(
                    dampingRatio = 0.78f,
                    stiffness = 430f
                )
            ),
            refraction = base.refraction.copy(
                movingVelocityThresholdPxPerSecond = 32f,
                speedProgressDivisorPxPerSecond = 1150f,
                dragProgressFloor = 0.24f,
                panelOffsetMaxDp = 6f
            ),
            indicator = base.indicator.copy(
                deformationScaleXDelta = 0.42f,
                deformationScaleYCompressionRatio = 0.58f,
                scaleSpring = hyperIslandIndicatorScaleXSpring,
                scaleYSpring = hyperIslandIndicatorScaleYSpring,
                lensVelocityRangePxPerSecond = 2200f
            )
        )
        BottomBarMotionProfile.ANDROID_NATIVE_FLOATING -> base.copy(
            drag = base.drag.copy(
                baseResistance = 1f,
                overscrollResistance = 0.34f,
                flingProjectionTimeSeconds = 0.14f,
                selectionSpring = MotionSpringConfig(
                    dampingRatio = 0.68f,
                    stiffness = 520f
                ),
                offsetSnapSpring = MotionSpringConfig(
                    dampingRatio = 0.62f,
                    stiffness = 420f
                )
            ),
            refraction = base.refraction.copy(
                movingVelocityThresholdPxPerSecond = 38f,
                speedProgressDivisorPxPerSecond = 1250f,
                dragProgressFloor = 0.22f,
                panelOffsetMaxDp = 5f
            ),
            indicator = base.indicator.copy(
                deformationScaleXDelta = 0.40f,
                deformationScaleYCompressionRatio = 0.54f,
                scaleSpring = hyperIslandIndicatorScaleXSpring,
                scaleYSpring = hyperIslandIndicatorScaleYSpring,
                lensVelocityRangePxPerSecond = 2500f,
                railFractionStretchMultiplier = 0.095f,
                capsuleVelocityNormalizationDivisor = 10.5f,
                capsuleVelocityScaleXMultiplier = 0.72f,
                capsuleVelocityScaleYMultiplier = 0.24f,
                capsuleVelocityClamp = 0.20f
            )
        )
        BottomBarMotionProfile.MIUI_FLOATING -> base.copy(
            drag = base.drag.copy(
                baseResistance = 1f,
                overscrollResistance = 0.30f,
                flingProjectionTimeSeconds = 0.18f,
                selectionSpring = MotionSpringConfig(
                    dampingRatio = 0.84f,
                    stiffness = 500f
                )
            ),
            refraction = base.refraction.copy(
                movingVelocityThresholdPxPerSecond = 48f,
                speedProgressDivisorPxPerSecond = 1500f,
                dragProgressFloor = 0.16f,
                panelOffsetMaxDp = 4f
            ),
            indicator = base.indicator.copy(
                deformationScaleXDelta = 0.30f,
                deformationScaleYCompressionRatio = 0.48f,
                scaleSpring = hyperIslandIndicatorScaleXSpring,
                scaleYSpring = hyperIslandIndicatorScaleYSpring,
                lensVelocityRangePxPerSecond = 2800f,
                railFractionStretchMultiplier = 0.065f,
                capsuleVelocityNormalizationDivisor = 11f,
                capsuleVelocityScaleXMultiplier = 0.64f,
                capsuleVelocityScaleYMultiplier = 0.22f,
                capsuleVelocityClamp = 0.18f
            )
        )
    }
}

private fun createDefaultBottomBarMotionSpec(): BottomBarMotionSpec {
    return BottomBarMotionSpec(
        drag = BottomBarDragMotionSpec(
            baseResistance = 1f,
            overscrollResistance = 0.3f,
            overscrollLimitItems = 0.5f,
            flingProjectionTimeSeconds = 0.14f,
            maxReleaseStepCount = 1,
            pressSpring = MotionSpringConfig(
                dampingRatio = 1f,
                stiffness = 1000f,
                visibilityThreshold = 0.001f
            ),
            selectionSpring = MotionSpringConfig(
                dampingRatio = 0.82f,
                stiffness = 500f
            ),
            offsetSnapSpring = MotionSpringConfig(
                dampingRatio = 0.78f,
                stiffness = 420f
            )
        ),
        refraction = BottomBarRefractionMotionSpec(
            movingVelocityThresholdPxPerSecond = 45f,
            speedProgressDivisorPxPerSecond = 1400f,
            dragProgressFloor = 0.18f,
            motionDeadzone = 0.03f,
            panelOffsetMaxDp = 4f
        ),
        indicator = BottomBarIndicatorMotionSpec(
            deformationScaleXDelta = 0.34f,
            deformationScaleYCompressionRatio = 0.52f,
            scaleSpring = hyperIslandIndicatorScaleXSpring,
            scaleYSpring = hyperIslandIndicatorScaleYSpring,
            lensVelocityRangePxPerSecond = 2600f,
            railFractionStretchMultiplier = 0.08f,
            capsuleVelocityNormalizationDivisor = 10f,
            capsuleVelocityScaleXMultiplier = 0.75f,
            capsuleVelocityScaleYMultiplier = 0.25f,
            capsuleVelocityClamp = 0.2f
        )
    )
}
