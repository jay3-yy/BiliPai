package com.android.purebilibili.core.ui

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** 窗口遮罩随卡片一起退出，避免系统窗口动画再补一遍淡出。 */
@Composable
fun AppDialogWindowExitEffect(progress: State<Float>) {
    val view = LocalView.current
    DisposableEffect(view, progress) {
        val window = ((view.parent as? DialogWindowProvider) ?: (view as? DialogWindowProvider))?.window
        if (window == null) return@DisposableEffect onDispose { }
        val originalDim = window.attributes.dimAmount
        window.setWindowAnimations(0)
        var applied = -1f
        val listener = ViewTreeObserver.OnPreDrawListener {
            val dim = originalDim * (1f - progress.value.coerceIn(0f, 1f))
            if (dim != applied) {
                applied = dim
                window.attributes = window.attributes.also { it.dimAmount = dim }
            }
            true
        }
        val observer = view.viewTreeObserver
        observer.addOnPreDrawListener(listener)
        onDispose {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            else view.viewTreeObserver.removeOnPreDrawListener(listener)
            // 此窗口随退出画面一起销毁；不在 dismiss 前恢复系统动画，避免二次退出。
        }
    }
}
