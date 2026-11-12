package com.android.purebilibili.core.ui.popup

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 返回进度独立于开合时钟；取消恢复可被下一次手势从当前进度接管。 */
internal class AppGlassPopupBackState {
    var progress by mutableFloatStateOf(0f)
        private set
    private var gestureStart = 0f
    private var restoring: Job? = null

    fun beginGesture() {
        restoring?.cancel()
        gestureStart = progress
    }

    fun seek(fraction: Float) {
        progress = gestureStart + (1f - gestureStart) * fraction.coerceIn(0f, 1f)
    }

    fun reset() {
        restoring?.cancel()
        restoring = null
        progress = 0f
        gestureStart = 0f
    }

    fun restore(scope: kotlinx.coroutines.CoroutineScope, reduced: Boolean) {
        if (reduced || progress == 0f) {
            reset()
            return
        }
        restoring?.cancel()
        restoring = scope.launch {
            animate(
                initialValue = progress,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 246.74f),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
            progress = 0f
            gestureStart = 0f
        }
    }
}

/** 同窗菜单在自身导航作用域接管返回，确认后由原有回收动画接住当前帧。 */
@Composable
internal fun AppGlassPopupPredictiveBackHandler(
    state: AppGlassPopupBackState,
    gesturesEnabled: Boolean,
    onGestureStarted: () -> Unit,
    onBackCompleted: () -> Unit,
) {
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val scope = rememberCoroutineScope()
    val gestures by rememberUpdatedState(gesturesEnabled)
    val reduced = LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current
    val latestReduced by rememberUpdatedState(reduced)
    val start by rememberUpdatedState(onGestureStarted)
    val complete by rememberUpdatedState(onBackCompleted)
    val handler = remember(state, scope) {
        object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, false) {
            override fun onBackStarted(event: NavigationEvent) {
                if (!gestures) return
                start()
                state.beginGesture()
                if (latestReduced) state.reset() else state.seek(event.progress)
            }

            override fun onBackProgressed(event: NavigationEvent) {
                if (!gestures) return
                if (latestReduced) state.reset() else state.seek(event.progress)
            }

            override fun onBackCancelled() {
                state.restore(scope, latestReduced || !gestures)
            }

            override fun onBackCompleted() {
                if (gestures) complete()
            }
        }
    }
    SideEffect {
        // 退场期间继续消费返回，防止第二次返回穿透到后方页面。
        handler.isBackEnabled = true
        if (reduced) state.reset()
    }
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose {
            state.reset()
            handler.remove()
        }
    }
}
