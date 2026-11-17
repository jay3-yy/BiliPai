package com.android.purebilibili.core.ui.transition

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier

internal data class NavigationBlurBlendFrame(val radiusPx: Float, val alpha: Float)

/** 半径在当次主题内保持固定；连续进度只改变模糊层的混合权重。 */
internal fun resolveNavigationBlurBlendFrame(
    progress: Float,
    motionTier: MotionTier = MotionTier.Normal,
    isLightBackground: Boolean = false,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NavigationBlurBlendFrame {
    val alpha = if (sdkInt >= Build.VERSION_CODES.S && motionTier != MotionTier.Reduced) {
        progress.coerceIn(0f, 1f)
    } else 0f
    return NavigationBlurBlendFrame(
        radiusPx = if (alpha > 0f) resolvePredictiveBackMaxBlurRadiusPx(isLightBackground) else 0f,
        alpha = alpha,
    )
}

/** 实时页面只录制内层图层引用；不读回像素，不冻结内容，也不逐帧创建模糊效果。 */
internal fun Modifier.navigationBlurBlend(
    frameProvider: () -> NavigationBlurBlendFrame,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return drawWithCache {
        val source = obtainGraphicsLayer()
        val blurred = obtainGraphicsLayer()
        val effects = BlurRenderEffectCache()
        var effectRecorded = false
        onDrawWithContent {
            val frame = frameProvider()
            if (frame.alpha <= 0f) {
                drawContent()
            } else {
                source.record { this@onDrawWithContent.drawContent() }
                if (!effectRecorded) {
                    blurred.record { drawLayer(source) }
                    effectRecorded = true
                }
                blurred.renderEffect = effects.resolve(frame.radiusPx)
                blurred.alpha = frame.alpha
                if (frame.alpha < 1f) drawLayer(source)
                drawLayer(blurred)
            }
        }
    // 内层缓存正文绘制指令；外层动画重录的是它的引用，内容自身失效仍正常更新。
    }.graphicsLayer()
}
