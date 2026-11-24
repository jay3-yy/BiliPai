package com.android.purebilibili.core.ui.transition

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

import kotlin.math.roundToInt

/**
 * 图层回调在 UI 线程使用。按量化像素半径缓存效果，避免手势往返或动画步进时
 * 重复创建 Android/Compose 效果对象；不存动画进度，也不写入 Snapshot 状态。
 */
internal class BlurRenderEffectCache {
    private val effects = HashMap<Int, ComposeRenderEffect>(16)

    fun resolve(blurRadiusPx: Float): ComposeRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !blurRadiusPx.isFinite() || blurRadiusPx <= 0.01f
        ) {
            return null
        }
        val key = blurRadiusPx.roundToInt().coerceAtLeast(1)
        return effects.getOrPut(key) {
            val radius = key.toFloat()
            RenderEffect.createBlurEffect(
                radius,
                radius,
                Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        }
    }

    fun clear() {
        effects.clear()
    }
}
