package com.android.purebilibili.feature.video.ui.section

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed

/**
 * 弹幕是画面内容，不能跟着窗口焦点停。
 * 小窗/分屏失焦时窗口仍可见，必须继续渲染；PiP 只要求 STARTED，真正进后台才停。
 */
@Composable
internal fun rememberDanmakuRenderWorkActive(isInPipMode: Boolean): Boolean {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return shouldRunDanmakuRenderWork(
        pageActive = LocalVisualWorkAllowed.current,
        resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
        started = lifecycleState.isAtLeast(Lifecycle.State.STARTED),
        isInPipMode = isInPipMode,
    )
}
