package com.android.purebilibili.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Stops decorative loops on retained offscreen pages and behind unfocused windows. */
internal class DecorativeAnimationGate {
    var inViewport by mutableStateOf(false)
    var resumed by mutableStateOf(false)
    var allowed by mutableStateOf(false)
    val active: Boolean get() = inViewport && resumed && allowed

    val modifier = Modifier.onVisibilityChanged(minFractionVisible = 0f) { visible ->
        inViewport = visible
    }
}

@Composable
internal fun rememberDecorativeAnimationGate(
    // 用户触发的有限动作可独立于卡片进场设置，仍遵守系统减弱动效与可见性。
    respectComponentMotion: Boolean = true,
): DecorativeAnimationGate {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gate = remember(lifecycle) { DecorativeAnimationGate() }
    val allowed = (!respectComponentMotion || LocalComponentMotionEnabled.current) && !LocalSystemReduceMotion.current &&
        LocalWindowInfo.current.isWindowFocused && rememberVisualWorkActive()
    androidx.compose.runtime.SideEffect { gate.allowed = allowed }
    DisposableEffect(lifecycle, gate) {
        gate.resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, _ ->
            gate.resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return gate
}
