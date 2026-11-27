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
    WeakHashMap<BackdropEffectScope, MutableMap<Float, BackdropRenderEffectCache>>()
}

private val fusedLensEffectCaches = ThreadLocal.withInitial {
    WeakHashMap<BackdropEffectScope, MutableMap<Float, BackdropRenderEffectCache>>()
}

internal fun BackdropEffectScope.liquidLensEffectCache(): BackdropRenderEffectCache {
    val levels = lensEffectCaches.get().getOrPut(this) { mutableMapOf() }
    return levels.getOrPut(downscaleFactor) { BackdropRenderEffectCache(15) }
}

internal fun BackdropEffectScope.fusedLiquidLensEffectCache(): BackdropRenderEffectCache {
    val levels = fusedLensEffectCaches.get().getOrPut(this) { mutableMapOf() }
    return levels.getOrPut(downscaleFactor) { BackdropRenderEffectCache(12) }
}

/** 缓存单个参数化效果及其上游合成链，不缓存画面；由各效果所有者独立持有。 */
class BackdropRenderEffectCache(parameterCount: Int) {
    private val parameters = FloatArray(parameterCount) { Float.NaN }
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
