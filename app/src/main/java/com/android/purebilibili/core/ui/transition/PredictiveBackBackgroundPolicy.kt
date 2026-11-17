package com.android.purebilibili.core.ui.transition

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.BiliPaiNavRouteTransition
import kotlin.math.roundToInt

private const val PREDICTIVE_BACK_MAX_BLUR_RADIUS_PX_DARK = 28f
private const val PREDICTIVE_BACK_MAX_BLUR_RADIUS_PX_LIGHT = 22f
private const val PREDICTIVE_BACK_BLUR_QUANTUM_PX = 4f
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

/** 栈顶和返回目标更新只改变 provider 的读取结果，不重建所有导航入口的内容包装。 */
@Composable
internal fun rememberPredictiveBackBackgroundState(
    progressProvider: () -> Float,
    targetKey: BiliPaiNavKey?,
    motionTier: MotionTier,
    isLightBackground: Boolean,
): PredictiveBackBackgroundState {
    val progress = rememberUpdatedState(progressProvider)
    val target = rememberUpdatedState(targetKey)
    val tier = rememberUpdatedState(motionTier)
    val light = rememberUpdatedState(isLightBackground)
    return remember {
        PredictiveBackBackgroundState(
            progressProvider = { progress.value() },
            targetKeyProvider = { target.value },
            motionTierProvider = { tier.value },
            isLightBackgroundProvider = { light.value },
        )
    }
}

/**
 * 预测式返回手势进行中，把系统回退进度(0→1)映射为底层页模糊强度。
 *
 * 统一为随手势减弱(1→0)：底层页随返回露出来应逐渐清晰。
 * （旧版 CLASSIC_CARD 等用 0→1 增强模糊，体感与设置页相反，已统一。）
 */
internal fun resolvePredictiveBackGestureBlurProgress(
    backProgress: Float,
    routeTransition: BiliPaiNavRouteTransition? = null,
): Float {
    @Suppress("UNUSED_PARAMETER")
    val ignoredTransition = routeTransition
    val clamped = backProgress.coerceIn(0f, 1f)
    // 1:1 跟手线性渐变：手指拖出多少比例，底层模糊就线性退去多少；反向推回则等比恢复，手感丝滑连贯。
    return 1f - clamped
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
    // 设置 iOS 预测返回已靠横滑露出完整底层页；再叠满屏 GPU 模糊会起手发灰、跟手发沉。
    if (routeTransition == BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP) return false
    return routeTransition == BiliPaiNavRouteTransition.CLASSIC_CARD ||
        routeTransition == BiliPaiNavRouteTransition.BOTTOM_BAR_SIBLING_POP ||
        routeTransition == BiliPaiNavRouteTransition.LIGHT_SIBLING_POP
}

internal fun shouldApplyPredictiveBackBlurToRoute(
    entryKey: BiliPaiNavKey,
    targetBackKey: BiliPaiNavKey?,
    videoCardBackgroundApplied: Boolean = false,
): Boolean {
    return !videoCardBackgroundApplied && targetBackKey != null && entryKey == targetBackKey
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

@Composable
internal fun Modifier.predictiveBackBackgroundEffect(
    progressProvider: () -> Float,
    motionTierProvider: () -> MotionTier = { MotionTier.Normal },
    isLightBackgroundProvider: () -> Boolean = { false },
): Modifier {
    return drawWithContent {
        drawContent()
        val light = isLightBackgroundProvider()
        val tintAlpha = resolvePredictiveBackSeparationTintAlpha(progressProvider(), light)
        if (tintAlpha > 0.001f) {
            val tintColor = if (light) Color.White else Color.Black
            drawRect(tintColor.copy(alpha = tintAlpha))
        }
    }.navigationBlurBlend {
        resolveNavigationBlurBlendFrame(
            progressProvider(), motionTierProvider(), isLightBackgroundProvider(),
        )
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
