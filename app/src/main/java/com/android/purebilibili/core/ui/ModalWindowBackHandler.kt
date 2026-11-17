package com.android.purebilibili.core.ui

import android.os.Build
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/** 独立弹窗的返回回调绑定当前窗口 View，避免 Activity 提前处理并关闭外层页面。 */
@Composable
internal fun ModalWindowBackHandler(
    enabled: Boolean = true,
    predictive: Boolean,
    onStarted: () -> Unit,
    onProgress: (Float) -> Unit,
    onCancelled: () -> Unit,
    onCompleted: () -> Unit,
) {
    val view = LocalView.current
    val previewEnabled = predictive && LocalPredictiveBackGestureEnabled.current
    val started = rememberUpdatedState(onStarted)
    val progress = rememberUpdatedState(onProgress)
    val cancelled = rememberUpdatedState(onCancelled)
    val completed = rememberUpdatedState(onCompleted)
    if (enabled && Build.VERSION.SDK_INT >= 33) {
        DisposableEffect(view, previewEnabled) {
            val dispose = registerPopupBackHandler(
                view, previewEnabled, { started.value() }, { progress.value(it) },
                { cancelled.value() }, { completed.value() },
            )
            onDispose { dispose() }
        }
    }
}

@RequiresApi(33)
private fun registerPopupBackHandler(
    view: View,
    predictive: Boolean,
    started: () -> Unit,
    progress: (Float) -> Unit,
    cancelled: () -> Unit,
    completed: () -> Unit,
): () -> Unit {
    val callback = if (Build.VERSION.SDK_INT >= 34 && predictive) {
        popupAnimationCallback(started, progress, cancelled, completed)
    } else OnBackInvokedCallback { completed() }
    var dispatcher: OnBackInvokedDispatcher? = null
    fun unregister() {
        dispatcher?.unregisterOnBackInvokedCallback(callback)
        dispatcher = null
    }
    fun register() {
        val next = view.findOnBackInvokedDispatcher()
        if (next === dispatcher) return
        unregister()
        next?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        dispatcher = next
    }
    val listener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = register()
        override fun onViewDetachedFromWindow(view: View) {
            unregister()
            cancelled()
        }
    }
    view.addOnAttachStateChangeListener(listener)
    if (view.isAttachedToWindow) register()
    return {
        view.removeOnAttachStateChangeListener(listener)
        unregister()
    }
}

@RequiresApi(34)
private fun popupAnimationCallback(
    started: () -> Unit,
    progress: (Float) -> Unit,
    cancelled: () -> Unit,
    completed: () -> Unit,
): OnBackInvokedCallback = object : OnBackAnimationCallback {
    override fun onBackStarted(backEvent: BackEvent) = started()
    override fun onBackProgressed(backEvent: BackEvent) = progress(backEvent.progress.coerceIn(0f, 1f))
    override fun onBackCancelled() = cancelled()
    override fun onBackInvoked() = completed()
}
