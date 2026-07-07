package com.android.purebilibili.core.ui.transition

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiNavRouteTransition
import kotlin.math.roundToInt

private const val PREDICTIVE_BACK_MAX_BLUR_RADIUS_PX_DARK = 28f
private const val PREDICTIVE_BACK_MAX_BLUR_RADIUS_PX_LIGHT = 22f
private const val PREDICTIVE_BACK_BLUR_QUANTUM_PX = 2f
private const val PREDICTIVE_BACK_LIGHT_SEPARATION_TINT_ALPHA = 0.05f

/** 与 SCALE handler 提交退出动画时长对齐。 */
internal const val PREDICTIVE_BACK_BACKGROUND_COMMIT_DURATION_MS = 200
internal const val PREDICTIVE_BACK_BACKGROUND_CANCEL_DURATION_MS = 160

internal data class PredictiveBackBlurFrame(
    val blurRadiusPx: Float,
    val separationTintAlpha: Float = 0f,
    val useLightSeparationTint: Boolean = false,
)

internal data class PredictiveBackBackgroundState(
    val progressProvider: () -> Float = { 0f },
    val targetKeyProvider: () -> BiliPaiNavKey? = { null },
    val motionTierProvider: () -> MotionTier = { MotionTier.Normal },
    val isLightBackgroundProvider: () -> Boolean = { false },
)

internal val LocalPredictiveBackBackgroundState = compositionLocalOf {
    PredictiveBackBackgroundState()
}

/**
 * 预测式返回手势进行中，把系统回退进度(0→1)映射为底层页模糊强度(0→1)。
 * 与视频返场 [resolveVideoCardTransitionBackgroundGestureProgress] 方向相反。
 */
internal fun resolvePredictiveBackGestureBlurProgress(backProgress: Float): Float {
    val clamped = backProgress.coerceIn(0f, 1f)
    return 1f - (1f - clamped) * (1f - clamped)
}

internal fun resolvePredictiveBackMaxBlurRadiusPx(isLightBackground: Boolean): Float {
    return if (isLightBackground) {
        PREDICTIVE_BACK_MAX_BLUR_RADIUS_PX_LIGHT
    } else {
        PREDICTIVE_BACK_MAX_BLUR_RADIUS_PX_DARK
    }
}

internal fun resolvePredictiveBackSeparationTintAlpha(
    progress: Float,
    isLightBackground: Boolean,
): Float {
    if (!isLightBackground) return 0f
    return PREDICTIVE_BACK_LIGHT_SEPARATION_TINT_ALPHA * progress.coerceIn(0f, 1f)
}

internal fun resolvePredictiveBackBlurFrame(
    progress: Float,
    motionTier: MotionTier = MotionTier.Normal,
    isLightBackground: Boolean = false,
    sdkInt: Int = Build.VERSION.SDK_INT,
): PredictiveBackBlurFrame {
    val clamped = progress.coerceIn(0f, 1f)
    val maxBlurRadiusPx = resolvePredictiveBackMaxBlurRadiusPx(isLightBackground)
    val rawBlurRadiusPx = if (
        clamped > 0f &&
        motionTier != MotionTier.Reduced &&
        sdkInt >= Build.VERSION_CODES.S
    ) {
        maxBlurRadiusPx * clamped
    } else {
        0f
    }
    return PredictiveBackBlurFrame(
        blurRadiusPx = quantizePredictiveBackBlurRadius(rawBlurRadiusPx, maxBlurRadiusPx),
        separationTintAlpha = resolvePredictiveBackSeparationTintAlpha(
            progress = clamped,
            isLightBackground = isLightBackground,
        ),
        useLightSeparationTint = isLightBackground,
    )
}

internal fun shouldApplyPredictiveBackGestureBlur(
    routeTransition: BiliPaiNavRouteTransition,
    predictiveBackEnabled: Boolean,
    gestureReturningVideoCard: Boolean,
    motionTier: MotionTier,
): Boolean {
    if (!predictiveBackEnabled) return false
    if (gestureReturningVideoCard) return false
    if (motionTier == MotionTier.Reduced) return false
    if (routeTransition == BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT) return false
    return routeTransition == BiliPaiNavRouteTransition.CLASSIC_CARD ||
        routeTransition == BiliPaiNavRouteTransition.BOTTOM_BAR_SIBLING_POP ||
        routeTransition == BiliPaiNavRouteTransition.LIGHT_SIBLING_POP
}

internal fun shouldApplyPredictiveBackBlurToRoute(
    entryKey: BiliPaiNavKey,
    targetBackKey: BiliPaiNavKey?,
    blurProgress: Float,
): Boolean {
    if (blurProgress <= 0.001f) return false
    return targetBackKey != null && entryKey == targetBackKey
}

internal fun resolvePredictiveBackCommitBlurDurationMs(startProgress: Float): Int {
    val clamped = startProgress.coerceIn(0f, 1f)
    if (clamped <= 0f) {
        return PREDICTIVE_BACK_BACKGROUND_CANCEL_DURATION_MS
    }
    return (PREDICTIVE_BACK_BACKGROUND_COMMIT_DURATION_MS * clamped)
        .roundToInt()
        .coerceIn(1, PREDICTIVE_BACK_BACKGROUND_COMMIT_DURATION_MS)
}

private class PredictiveBackBlurFrameCache {
    private var lastProgress = Float.NaN
    private var lastMotionTier: MotionTier? = null
    private var lastIsLightBackground: Boolean? = null
    private var cached = PredictiveBackBlurFrame(blurRadiusPx = 0f)

    fun resolve(
        progress: Float,
        motionTier: MotionTier,
        isLightBackground: Boolean,
    ): PredictiveBackBlurFrame {
        if (
            progress != lastProgress ||
            motionTier != lastMotionTier ||
            isLightBackground != lastIsLightBackground
        ) {
            lastProgress = progress
            lastMotionTier = motionTier
            lastIsLightBackground = isLightBackground
            cached = resolvePredictiveBackBlurFrame(
                progress = progress,
                motionTier = motionTier,
                isLightBackground = isLightBackground,
            )
        }
        return cached
    }
}

internal fun Modifier.predictiveBackBackgroundEffect(
    progressProvider: () -> Float,
    motionTierProvider: () -> MotionTier = { MotionTier.Normal },
    isLightBackgroundProvider: () -> Boolean = { false },
): Modifier {
    val frameCache = PredictiveBackBlurFrameCache()
    return graphicsLayer {
        val frame = frameCache.resolve(
            progressProvider(),
            motionTierProvider(),
            isLightBackgroundProvider(),
        )
        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && frame.blurRadiusPx > 0.01f) {
            RenderEffect
                .createBlurEffect(
                    frame.blurRadiusPx,
                    frame.blurRadiusPx,
                    Shader.TileMode.CLAMP,
                )
                .asComposeRenderEffect()
        } else {
            null
        }
    }.drawWithContent {
        drawContent()
        val frame = frameCache.resolve(
            progressProvider(),
            motionTierProvider(),
            isLightBackgroundProvider(),
        )
        if (frame.separationTintAlpha > 0.001f) {
            val tintColor = if (frame.useLightSeparationTint) Color.White else Color.Black
            drawRect(tintColor.copy(alpha = frame.separationTintAlpha))
        }
    }
}

private fun quantizePredictiveBackBlurRadius(
    radiusPx: Float,
    maxBlurRadiusPx: Float,
): Float {
    if (radiusPx <= 0f) return 0f
    return ((radiusPx / PREDICTIVE_BACK_BLUR_QUANTUM_PX).roundToInt() *
        PREDICTIVE_BACK_BLUR_QUANTUM_PX)
        .coerceIn(0f, maxBlurRadiusPx)
}
