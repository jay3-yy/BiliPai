package com.android.purebilibili.core.ui

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import com.android.purebilibili.core.ui.motion.continuityTween
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 手势只预览部分退出；取消和再次接管均从当前画面继续。 */
class AppDialogBackState {
    var progress by mutableFloatStateOf(0f)
        private set
    var isCompleting by mutableStateOf(false)
        internal set
    private var gestureStart = 0f
    internal var settling: Job? = null

    fun beginGesture() {
        settling?.cancel()
        isCompleting = false
        gestureStart = progress
    }

    fun seek(progress: Float) {
        this.progress = gestureStart + (1f - gestureStart) * progress.coerceIn(0f, 1f) * 0.25f
    }

    fun reset() {
        settling?.cancel()
        progress = 0f
        isCompleting = false
        gestureStart = 0f
    }

    internal suspend fun settle(target: Float, reduced: Boolean) {
        if (progress == target) return
        if (reduced) progress = target
        else animate(progress, target, animationSpec = continuityTween(if (target == 0f) 180 else 200)) { value, _ ->
            progress = value
        }
    }
}

/** 在 Dialog 内容中注册，优先使用该窗口的 dispatcher；菜单保留其所在导航作用域。 */
@Composable
fun AppDialogPredictiveBackHandler(
    state: AppDialogBackState,
    enabled: Boolean = true,
    windowScoped: Boolean = true,
    animateCompletion: Boolean = true,
    gesturesEnabled: Boolean = true,
    retainPreview: () -> Boolean = { false },
    onBackCompleted: () -> Unit,
) {
    val view = LocalView.current
    val localOwner = LocalNavigationEventDispatcherOwner.current
    val owner = if (windowScoped) view.findViewTreeNavigationEventDispatcherOwner() ?: localOwner else localOwner
    val dispatcher = checkNotNull(owner).navigationEventDispatcher
    val scope = rememberCoroutineScope()
    val complete by rememberUpdatedState(onBackCompleted)
    val reduced by rememberUpdatedState(LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current)
    val animateExit by rememberUpdatedState(animateCompletion)
    val gestures by rememberUpdatedState(gesturesEnabled)
    val retaining by rememberUpdatedState(retainPreview)
    val handler = remember(state, scope) {
        object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, false) {
            override fun onBackStarted(event: NavigationEvent) {
                state.beginGesture()
                if (reduced) state.reset() else if (gestures) state.seek(event.progress)
            }

            override fun onBackProgressed(event: NavigationEvent) {
                if (reduced) state.reset() else if (gestures) state.seek(event.progress)
            }

            override fun onBackCancelled() {
                state.settling?.cancel()
                state.settling = scope.launch { state.settle(0f, reduced) }
            }

            override fun onBackCompleted() {
                state.settling?.cancel()
                if (!gestures) return
                if (animateExit) {
                    state.isCompleting = true
                    state.settling = scope.launch {
                        state.settle(1f, reduced)
                        complete()
                        // 关闭请求可能被业务校验保留；仍存在的窗口恢复可交互外观。
                        withFrameNanos { }
                        state.isCompleting = false
                        state.settle(0f, reduced)
                    }
                } else {
                    complete()
                    state.settling = scope.launch {
                        withFrameNanos { }
                        if (!retaining()) state.settle(0f, reduced)
                    }
                }
            }
        }
    }
    SideEffect { handler.isBackEnabled = enabled }
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose { state.settling?.cancel(); handler.remove() }
    }
}

/** 原生和玻璃只共用手势层，不修改各自表面、尺寸与布局参数。 */
@Composable
internal fun Modifier.appDialogBackMotion(
    state: AppDialogBackState? = null,
    progress: () -> Float,
): Modifier {
    val density = LocalDensity.current.density
    val blur = remember { DialogBackBlurCache() }
    return blockAppDialogExitInput(state?.isCompleting == true).graphicsLayer {
        val fraction = progress().coerceIn(0f, 1f)
        scaleX = 1f - 0.2f * fraction
        scaleY = scaleX
        alpha = 1f - fraction
        renderEffect = blur.resolve(8f * density * fraction)
    }
}

private class DialogBackBlurCache {
    private var radius = -1f
    private var effect: BlurEffect? = null

    fun resolve(value: Float): BlurEffect? {
        if (value <= 0.1f) return null
        if (value != radius) {
            radius = value
            effect = BlurEffect(value, value, TileMode.Decal)
        }
        return effect
    }
}
