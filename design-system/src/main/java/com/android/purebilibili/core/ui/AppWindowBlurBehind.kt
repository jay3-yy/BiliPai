package com.android.purebilibili.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import android.view.ViewTreeObserver
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 对弹窗背后的 Activity 内容层应用实时 RenderEffect（API 31+）。
 * 弹窗保留独立的原生窗口，不被一起模糊；不依赖系统跨窗口模糊开关。
 * 嵌套弹窗按拥有者计数，最后一个弹窗退出才恢复内容层。
 */
@Composable
fun ModalWindowBlurBehindEffect(
    enabled: Boolean = true,
    radius: Dp = 24.dp,
    progress: () -> Float = { 1f },
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val density = LocalDensity.current
    val latestProgress by rememberUpdatedState(progress)
    DisposableEffect(view, enabled, radius, density) {
        val activity = findModalActivity(view.context)
        val contentView = activity?.findViewById<View>(android.R.id.content)
        if (enabled && contentView != null) {
            val owner = Any()
            val maxRadius = with(density) { radius.toPx() }
            fun updateBlur() {
                val fraction = latestProgress().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
                ModalContentBlur.acquire(contentView, owner, maxRadius * fraction)
            }
            updateBlur()
            // Read the native sheet's current position at draw time, including spring/drag frames.
            val observer = view.viewTreeObserver
            val listener = ViewTreeObserver.OnPreDrawListener { updateBlur(); true }
            observer.addOnPreDrawListener(listener)
            return@DisposableEffect onDispose {
                if (observer.isAlive) observer.removeOnPreDrawListener(listener)
                else view.viewTreeObserver.removeOnPreDrawListener(listener)
                ModalContentBlur.release(contentView, owner)
            }
        }
        val window = ((view.parent as? DialogWindowProvider) ?: (view as? DialogWindowProvider))?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        val windowManager = window.windowManager
        val originalFlags = window.attributes.flags
        val originalRadius = window.attributes.blurBehindRadius
        val blurRadiusPx = with(density) { radius.roundToPx() }
        var blurAvailable = false
        var appliedRadius = -1
        fun updateWindowBlur() {
            val available = blurAvailable
            val fraction = latestProgress().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            val currentRadius = if (available) (blurRadiusPx * fraction).toInt() else 0
            if (appliedRadius == currentRadius) return
            appliedRadius = currentRadius
            window.attributes = window.attributes.also { attrs ->
                attrs.flags = if (currentRadius > 0) {
                    attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                } else {
                    attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                }
                attrs.blurBehindRadius = currentRadius
            }
        }
        val listener = java.util.function.Consumer<Boolean> { available ->
            blurAvailable = available
            updateWindowBlur()
        }
        val observer = view.viewTreeObserver
        val frameListener = ViewTreeObserver.OnPreDrawListener { updateWindowBlur(); true }
        observer.addOnPreDrawListener(frameListener)
        windowManager.addCrossWindowBlurEnabledListener(view.context.mainExecutor, listener)
        onDispose {
            if (observer.isAlive) observer.removeOnPreDrawListener(frameListener)
            else view.viewTreeObserver.removeOnPreDrawListener(frameListener)
            windowManager.removeCrossWindowBlurEnabledListener(listener)
            window.attributes = window.attributes.also { attrs ->
                attrs.flags = (attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()) or
                    (originalFlags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attrs.blurBehindRadius = originalRadius
            }
        }
    }
}

private fun findModalActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}

/** All callers run on the UI thread through DisposableEffect. */
private object ModalContentBlur {
    private val owners = java.util.WeakHashMap<View, MutableMap<Any, Float>>()
    private val appliedRadii = java.util.WeakHashMap<View, Float>()

    fun acquire(view: View, owner: Any, radius: Float) {
        owners.getOrPut(view) { mutableMapOf() }[owner] = radius
        update(view)
    }

    fun release(view: View, owner: Any) {
        owners[view]?.remove(owner)
        update(view)
        if (owners[view].isNullOrEmpty()) {
            owners.remove(view)
            appliedRadii.remove(view)
        }
    }

    private fun update(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val rawRadius = owners[view]?.values?.maxOrNull() ?: 0f
        // 按整像素量化半径，避免弹窗拖动/弹簧尾段因亚像素浮点变化每帧重建 RenderEffect。
        val radius = if (rawRadius <= 0.5f) 0f else kotlin.math.round(rawRadius)
        if (appliedRadii[view] == radius) return
        appliedRadii[view] = radius
        view.setRenderEffect(
            if (radius > 0f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            else null
        )
    }
}
