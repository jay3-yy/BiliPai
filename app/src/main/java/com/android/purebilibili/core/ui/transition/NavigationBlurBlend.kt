package com.android.purebilibili.core.ui.transition

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.android.purebilibili.core.ui.adaptive.MotionTier

internal data class NavigationBlurBlendFrame(
    val radiusPx: Float,
    val alpha: Float,
    val contentScale: Float = 1f,
    val scrimAlpha: Float = 0f,
)

/** 半径在当次主题内保持固定；连续进度只改变模糊层的混合权重。 */
internal fun resolveNavigationBlurBlendFrame(
    progress: Float,
    motionTier: MotionTier = MotionTier.Normal,
    isLightBackground: Boolean = false,
    sdkInt: Int = Build.VERSION.SDK_INT,
    maxBlurRadiusPx: Float = resolvePredictiveBackMaxBlurRadiusPx(isLightBackground),
    contentScale: Float = 1f,
    scrimAlpha: Float = 0f,
): NavigationBlurBlendFrame {
    val alpha = if (sdkInt >= Build.VERSION_CODES.S && motionTier != MotionTier.Reduced) {
        progress.coerceIn(0f, 1f)
    } else 0f
    return NavigationBlurBlendFrame(
        radiusPx = if (alpha > 0f) maxBlurRadiusPx.coerceAtLeast(0f) else 0f,
        alpha = alpha,
        contentScale = if (motionTier == MotionTier.Reduced) 1f else contentScale.coerceAtLeast(1f),
        scrimAlpha = scrimAlpha.coerceIn(0f, 1f),
    )
}

/** 实时页面只录制内层图层引用；不读回像素，不冻结内容，也不逐帧创建模糊效果。 */
internal fun Modifier.navigationBlurBlend(
    frameProvider: () -> NavigationBlurBlendFrame,
): Modifier {
    return drawWithCache {
        val source = obtainGraphicsLayer()
        val blurred = obtainGraphicsLayer()
        val effects = BlurRenderEffectCache()
        var effectRecorded = false
        onDrawWithContent {
            val frame = frameProvider()
            val useBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                frame.alpha > 0f && frame.radiusPx > 0.01f
            if (!useBlur) {
                if (effectRecorded) {
                    if (!blurred.isReleased) {
                        blurred.renderEffect = null
                        blurred.record(
                            density = this,
                            layoutDirection = layoutDirection,
                            size = androidx.compose.ui.unit.IntSize(1, 1),
                        ) {}
                    }
                    if (!source.isReleased) {
                        source.record(
                            density = this,
                            layoutDirection = layoutDirection,
                            size = androidx.compose.ui.unit.IntSize(1, 1),
                        ) {}
                    }
                    effects.clear()
                    effectRecorded = false
                }
                if (frame.contentScale > 1.0001f) {
                    clipRect {
                        scale(frame.contentScale, pivot = center) {
                            this@onDrawWithContent.drawContent()
                        }
                    }
                } else {
                    drawContent()
                }
            } else {
                source.record { this@onDrawWithContent.drawContent() }
                if (!effectRecorded) {
                    blurred.record { drawLayer(source) }
                    effectRecorded = true
                }
                blurred.renderEffect = effects.resolve(frame.radiusPx)
                blurred.alpha = frame.alpha
                if (frame.contentScale > 1.0001f) {
                    clipRect {
                        scale(frame.contentScale, pivot = center) {
                            if (frame.alpha < 1f) drawLayer(source)
                            drawLayer(blurred)
                        }
                    }
                } else {
                    if (frame.alpha < 1f) drawLayer(source)
                    drawLayer(blurred)
                }
            }
            if (frame.scrimAlpha > 0.001f) {
                drawRect(Color.Black.copy(alpha = frame.scrimAlpha))
            }
        }
    // 内层缓存正文绘制指令；外层动画重录的是它的引用，内容自身失效仍正常更新。
    }.graphicsLayer()
}
