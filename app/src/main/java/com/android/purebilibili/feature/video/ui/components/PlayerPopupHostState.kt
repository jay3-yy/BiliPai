package com.android.purebilibili.feature.video.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.core.ui.LocalAppDialogExitRunning
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed

/** 宿主退场或脱离窗口后停止创建原生弹窗；焦点由弹窗自行管理，不作为宿主退出依据。 */
@Composable
internal fun rememberPlayerPopupHostActive(onDismiss: () -> Unit): Boolean {
    val view = LocalView.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val exiting = LocalAppDialogExitRunning.current || !LocalVisualWorkAllowed.current ||
        lifecycle == Lifecycle.State.DESTROYED
    val dismiss by rememberUpdatedState(onDismiss)
    var attached by remember(view) { mutableStateOf(view.isAttachedToWindow) }
    DisposableEffect(view) {
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { attached = true }
            override fun onViewDetachedFromWindow(v: View) { attached = false }
        }
        view.addOnAttachStateChangeListener(listener)
        attached = view.isAttachedToWindow
        onDispose { view.removeOnAttachStateChangeListener(listener) }
    }
    LaunchedEffect(exiting) { if (exiting) dismiss() }
    return !exiting && attached && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
}
