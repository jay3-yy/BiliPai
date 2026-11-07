package com.android.purebilibili.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.effect

/** 经典磨砂保持全分辨率采样，避免大半径降采样放大滚动细节的闪烁。 */
@Composable
internal fun rememberMaterialBackdropBlur(tuning: LiquidGlassTuning): BackdropEffectScope.(Float) -> Unit {
    val cache = remember { BlurRenderEffectCache() }
    return remember(cache, tuning.fixedResolutionBlur) {
        { radiusPx ->
            if (tuning.fixedResolutionBlur) {
                // 与现有模糊的半径到高斯 sigma 换算一致，不改变用户选择的模糊强度。
                val sigma = radiusPx * 0.45f
                padding = maxOf(padding, sigma * 3f)
                downscaleFactor = 1
                noiseCoefficient = 0f
                cache.resolve(sigma)?.let { effect(it) }
            } else {
                blur(radiusPx, radiusPx)
            }
        }
    }
}
