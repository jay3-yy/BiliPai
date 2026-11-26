package com.android.purebilibili.feature.video.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.transition.NavigationBlurBlendFrame
import com.android.purebilibili.core.ui.transition.navigationBlurBlend
import com.android.purebilibili.core.ui.transition.resolveNavigationBlurBlendFrame
import com.android.purebilibili.feature.settings.rememberSettingsNavigationConfiguration

private const val COMMENT_THREAD_BLUR_RADIUS_DP_DARK = 18f
private const val COMMENT_THREAD_BLUR_RADIUS_DP_LIGHT = 16f
private const val COMMENT_THREAD_MAX_BLUR_RADIUS_PX = 72f
private const val COMMENT_THREAD_MIN_BLUR_RADIUS_PX = 24f
private const val COMMENT_THREAD_SCRIM_ALPHA_DARK = 0.22f
private const val COMMENT_THREAD_SCRIM_ALPHA_LIGHT = 0.12f

internal fun resolveCommentThreadBlurFrame(
    progress: Float,
    density: Float,
    isDarkTheme: Boolean,
    motionTier: MotionTier = MotionTier.Normal,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NavigationBlurBlendFrame {
    val clamped = progress.coerceIn(0f, 1f)
    val radiusDp = if (isDarkTheme) COMMENT_THREAD_BLUR_RADIUS_DP_DARK else COMMENT_THREAD_BLUR_RADIUS_DP_LIGHT
    val radiusPx = (radiusDp * density.coerceAtLeast(1f))
        .coerceIn(COMMENT_THREAD_MIN_BLUR_RADIUS_PX, COMMENT_THREAD_MAX_BLUR_RADIUS_PX)
    val maxScrim = if (isDarkTheme) COMMENT_THREAD_SCRIM_ALPHA_DARK else COMMENT_THREAD_SCRIM_ALPHA_LIGHT
    return resolveNavigationBlurBlendFrame(
        progress = clamped,
        motionTier = motionTier,
        isLightBackground = !isDarkTheme,
        sdkInt = sdkInt,
        maxBlurRadiusPx = radiusPx,
        scrimAlpha = maxScrim * clamped,
    )
}

/** Blur the covered comment content, never the foreground thread or its controls. */
@Composable
internal fun Modifier.commentThreadNavigationBlur(
    progress: () -> Float,
): Modifier {
    val enabled = rememberSettingsNavigationConfiguration().miuixTransitionBlurEnabled &&
        LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    if (!enabled) return this
    val density = LocalDensity.current.density
    val isDarkTheme = isAppInDarkTheme()
    // 玻璃与原生共用；按屏幕密度换算稳定半径（由 BlurRenderEffectCache 缓存）并叠加轻度暗化，
    // 仅在绘制阶段读取连续权重，确保高分屏下楼中楼与主评论分层清晰。
    return navigationBlurBlend {
        resolveCommentThreadBlurFrame(
            progress = progress(),
            density = density,
            isDarkTheme = isDarkTheme,
        )
    }
}
