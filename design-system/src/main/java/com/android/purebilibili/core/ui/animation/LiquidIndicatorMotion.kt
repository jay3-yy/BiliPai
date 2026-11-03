package com.android.purebilibili.core.ui.animation

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.android.purebilibili.core.ui.BottomBarReferencePressedScale
import com.android.purebilibili.core.ui.motion.pressFeedbackSpring
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Shared motion for tab lenses and liquid value thumbs. Velocity is full ranges per second. */
object LiquidIndicatorMotion {
    const val VelocityDivisor = 10f
    const val VelocityClamp = 0.2f
    const val ReleaseDistanceFraction = 0.025f
    const val SettleTolerance = 0.015f
    const val BloomWaitMillis = 300L
    const val SettleWaitMillis = 450L
    val pressedScale: Float get() = BottomBarReferencePressedScale

    fun positionSpec(threshold: Float = 0.001f): SpringSpec<Float> = spring(1f, 1000f, threshold)
    fun velocitySpec(): SpringSpec<Float> = spring(0.5f, 300f, 0.01f)
    fun pressSpec(): SpringSpec<Float> = pressFeedbackSpring()
    fun scaleXSpec(): SpringSpec<Float> = spring(0.6f, 250f, 0.001f)
    fun scaleYSpec(): SpringSpec<Float> = spring(0.7f, 250f, 0.001f)

    /** Page-driven motion blooms once between adjacent tabs and returns at either endpoint. */
    fun pagerPressProgress(position: Float): Float {
        if (!position.isFinite()) return 0f
        val fraction = position - floor(position)
        return sin(PI * fraction).toFloat().coerceIn(0f, 1f)
    }

    fun scaleX(base: Float, velocity: Float): Float =
        base / (1f - (velocity / VelocityDivisor * 0.75f).coerceIn(-VelocityClamp, VelocityClamp))

    fun scaleY(base: Float, velocity: Float): Float =
        base * (1f - (velocity / VelocityDivisor * 0.25f).coerceIn(-VelocityClamp, VelocityClamp))
}
