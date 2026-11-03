// SPDX-License-Identifier: GPL-3.0-only
package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

private const val SCALE_MIN = 0.85f
private const val SCALE_GESTURE_HORIZONTAL_TRAVEL_FRACTION = 0.065f
private const val SCALE_GESTURE_VERTICAL_FOLLOW_FRACTION = 0.12f
private const val SCALE_GESTURE_MAX_ROTATION_DEGREES = 2.25f
private val ScaleExitDrift = 96.dp
private val ScaleGestureMaxVerticalTravel = 28.dp

internal data class ScaleGestureCardTransform(
    val translationX: Float,
    val translationY: Float,
    val rotationZ: Float,
    val pivotFractionX: Float,
    val pivotFractionY: Float,
)

internal fun resolveScaleGestureCardTransform(
    progress: Float,
    touchY: Float,
    initialTouchY: Float,
    widthPx: Float,
    heightPx: Float,
    isLeftEdge: Boolean,
    maxVerticalTravelPx: Float,
    settleWeight: Float = 1f,
): ScaleGestureCardTransform {
    val safeProgress = progress.coerceIn(0f, 1f)
    val easedProgress = BackGestureEasing.transform(safeProgress)
    val safeSettleWeight = settleWeight.coerceIn(0f, 1f)
    val edgeSign = if (isLeftEdge) 1f else -1f
    val verticalDelta = touchY - initialTouchY
    val normalizedVerticalDelta = if (heightPx > 0f) {
        (verticalDelta / (heightPx * 0.5f)).coerceIn(-1f, 1f)
    } else {
        0f
    }
    val gestureWeight = easedProgress * safeSettleWeight

    return ScaleGestureCardTransform(
        translationX = edgeSign * widthPx.coerceAtLeast(0f) *
            SCALE_GESTURE_HORIZONTAL_TRAVEL_FRACTION * gestureWeight,
        translationY = (verticalDelta * SCALE_GESTURE_VERTICAL_FOLLOW_FRACTION)
            .coerceIn(-maxVerticalTravelPx.coerceAtLeast(0f), maxVerticalTravelPx.coerceAtLeast(0f)) *
            gestureWeight,
        rotationZ = edgeSign * normalizedVerticalDelta *
            SCALE_GESTURE_MAX_ROTATION_DEGREES * gestureWeight,
        pivotFractionX = if (isLeftEdge) 0.8f else 0.2f,
        pivotFractionY = if (heightPx > 0f) {
            (touchY / heightPx).coerceIn(0.1f, 0.9f)
        } else {
            0.5f
        },
    )
}

private val ScaleExitMotion = NavMotion(
    commit = NavSettleSpec.Tween(durationMillis = 450, easing = FastOutExtraSlowIn),
    cancel = NavSettleSpec.Spring(stiffness = 1500f),
    programmatic = NavSettleSpec.Tween(
        durationMillis = 200,
        easing = AppMotionEasing.Md3Standard,
    ),
)

internal fun scaleNavTransition(exitDirection: BiliPaiPredictiveBackExitDirection): NavTransition {
    val pop = navGraphicsTransition(
        opaqueDepth = 1f,
        motion = ScaleExitMotion,
        scrim = { scope ->
            when {
                scope.settle?.phase == NavSettlePhase.Commit ->
                    (1f - (scope.settle?.elapsedMillis ?: 0f) / 450f).coerceIn(0f, 1f)

                scope.gesture != null -> 1f
                else -> coverProgress(scope.relativeDepth)
            }
        },
    ) { scope ->
        val depth = scope.relativeDepth
        val widthPx = scope.layoutSize.width.toFloat()
        val heightPx = scope.layoutSize.height.toFloat()
        val driftPx = with(scope.density) { ScaleExitDrift.toPx() }
        val maxVerticalTravelPx = with(scope.density) { ScaleGestureMaxVerticalTravel.toPx() }
        val gesture = scope.gesture
        val sign = exitDirectionSign(exitDirection, scope)
        val committing = scope.settle?.phase == NavSettlePhase.Commit
        val cancelling = scope.settle?.phase == NavSettlePhase.Cancel
        val outgoingCommit = scope.role == NavRole.Outgoing && committing && gesture != null
        if (depth <= 0f) {
            val progress = topProgress(depth)
            val commitPost = if (outgoingCommit) {
                val releaseProgress = (1f - gesture.progress).coerceAtLeast(0.01f)
                (1f - progress / releaseProgress).coerceIn(0f, 1f)
            } else {
                0f
            }
            val cancelWeight = if (cancelling && gesture != null) {
                ((1f - progress) / gesture.progress.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            } else {
                1f
            }
            val gestureTransform = gesture?.let {
                resolveScaleGestureCardTransform(
                    progress = it.progress,
                    touchY = it.touchY,
                    initialTouchY = it.initialTouchY,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    isLeftEdge = it.swipeEdge == NavSwipeEdge.Left,
                    maxVerticalTravelPx = maxVerticalTravelPx,
                    settleWeight = if (committing) 1f - commitPost else cancelWeight,
                )
            }
            val pageScale = if (outgoingCommit) {
                val releaseProgress = (1f - gesture.progress).coerceAtLeast(0.01f)
                val releaseEasedProgress = shapedTopProgress(releaseProgress, gesture)
                val committedScale = SCALE_MIN + (1f - SCALE_MIN) * releaseEasedProgress
                committedScale + (SCALE_MIN - committedScale) * commitPost
            } else {
                val easedProgress = shapedTopProgress(progress, gesture)
                SCALE_MIN + (1f - SCALE_MIN) * easedProgress
            }
            val pivotX = gestureTransform?.pivotFractionX ?: 0.5f
            val pivotY = gestureTransform?.pivotFractionY ?: 0.5f
            scaleX = snapScaleToPixelExtent(pageScale, widthPx)
            scaleY = scaleX
            transformOrigin = TransformOrigin(
                pivotFractionX = pivotX,
                pivotFractionY = pivotY,
            )
            val rawTranslationX = if (gesture != null && scope.settle == null) {
                gestureTransform?.translationX ?: 0f
            } else if (outgoingCommit) {
                sign * commitPost * driftPx + (gestureTransform?.translationX ?: 0f)
            } else if (gesture != null) {
                gestureTransform?.translationX ?: 0f
            } else {
                sign * (1f - progress) * widthPx
            }
            translationX = snapTranslationToPixelEdge(
                translation = rawTranslationX,
                scale = scaleX,
                extent = widthPx,
                pivotFraction = pivotX,
            )
            translationY = snapTranslationToPixelEdge(
                translation = gestureTransform?.translationY ?: 0f,
                scale = scaleY,
                extent = heightPx,
                pivotFraction = pivotY,
            )
            rotationZ = gestureTransform?.rotationZ ?: 0f
            if (outgoingCommit) {
                val elapsedMillis = scope.settle?.elapsedMillis ?: 0f
                alpha = (1f - 5f * (elapsedMillis / 450f)).coerceAtLeast(0f)
            }
        }
    }
    return navDirectionalTransition(
        push = NavTransitions.MiuixDefault,
        pop = pop,
        predictivePop = pop,
    )
}

private fun shapedTopProgress(progress: Float, gesture: NavGesture?): Float =
    if (gesture == null) progress else 1f - BackGestureEasing.transform((1f - progress).coerceIn(0f, 1f))

private fun exitDirectionSign(
    direction: BiliPaiPredictiveBackExitDirection,
    scope: NavTransitionScope,
): Float = when (direction) {
    BiliPaiPredictiveBackExitDirection.FOLLOW_GESTURE ->
        if (scope.gesture?.swipeEdge == NavSwipeEdge.Left) 1f else -1f

    BiliPaiPredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
    BiliPaiPredictiveBackExitDirection.ALWAYS_LEFT -> -1f
}
