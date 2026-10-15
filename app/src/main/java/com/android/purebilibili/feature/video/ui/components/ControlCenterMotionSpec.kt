package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Shared only by opted-in liquid-glass panels; progress is 0 closed, 1 open. */
internal object ControlCenterMotionSpec {
    const val MaxOverExpansion = 0.25f
    private const val DragResistance = 0.35f
    private const val ReleaseVelocityThreshold = 2.5f
    private const val CloseProgressThreshold = 0.65f

    fun dragProgress(progress: Float, openingDelta: Float): Float {
        val next = progress + openingDelta
        val resisted = when {
            openingDelta <= 0f || next <= 1f -> next
            progress < 1f -> 1f + (next - 1f) * DragResistance
            else -> progress + openingDelta * DragResistance
        }
        return resisted.coerceIn(0f, 1f + MaxOverExpansion)
    }

    fun shouldClose(openProgress: Float, openingVelocity: Float): Boolean = when {
        openingVelocity <= -ReleaseVelocityThreshold -> true
        openingVelocity >= ReleaseVelocityThreshold -> false
        else -> openProgress < CloseProgressThreshold
    }

    fun releaseVelocity(velocity: Float): Float = velocity.coerceIn(-4f, 4f)

    fun springSpec(closing: Boolean): SpringSpec<Float> = spring(
        dampingRatio = if (closing) 1f else 0.72f,
        stiffness = 300f,
        visibilityThreshold = 0.001f,
    )
}
