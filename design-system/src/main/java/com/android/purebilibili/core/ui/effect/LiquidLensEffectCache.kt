package com.android.purebilibili.core.ui.effect

import androidx.compose.ui.graphics.RenderEffect
import java.util.WeakHashMap
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.effect

/**
 * 每个绘制 scope 独立保存镜片效果；弱引用不延长页面和采样源的生命周期。
 * 只缓存效果参数，不缓存背景像素。不同降采样档位分别保存，避免交叉混合时来回重建。
 */
private val lensEffectCaches = ThreadLocal.withInitial {
    WeakHashMap<BackdropEffectScope, Array<LiquidLensEffectCache?>>()
}

internal fun BackdropEffectScope.liquidLensEffectCache(): LiquidLensEffectCache {
    val levels = lensEffectCaches.get().getOrPut(this) { arrayOfNulls(6) }
    val factor = downscaleFactor.coerceAtLeast(1)
    // 常用 1/2/4/8/16 各有一个槽；其他比例共用末槽，仍按完整参数校验。
    val index = if (factor <= 16 && (factor and (factor - 1)) == 0) {
        factor.countTrailingZeroBits()
    } else 5
    return levels[index] ?: LiquidLensEffectCache().also { levels[index] = it }
}

internal class LiquidLensEffectCache {
    private val parameters = FloatArray(15) { Float.NaN }
    private var changed = true
    private var lensEffect: RenderEffect? = null
    private var chainInput: RenderEffect? = null
    private var chainResult: RenderEffect? = null

    val needsUpdate: Boolean get() = changed || lensEffect == null

    operator fun set(index: Int, value: Float) {
        if (parameters[index] != value) {
            parameters[index] = value
            changed = true
        }
    }

    fun store(effect: RenderEffect?) {
        lensEffect = effect
        changed = false
        chainInput = null
        chainResult = null
    }

    fun applyTo(scope: BackdropEffectScope) {
        val lens = lensEffect ?: return
        val input = scope.renderEffect
        if (chainResult == null || chainInput !== input) {
            scope.effect(lens)
            chainInput = input
            chainResult = scope.renderEffect
        } else {
            scope.renderEffect = chainResult
        }
    }
}
