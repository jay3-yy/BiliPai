package com.android.purebilibili.core.ui.blur

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.core.ui.AdaptiveScaffoldHazeHandle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Collections
import java.util.WeakHashMap

/**
 * Haze 2 移除了 [HazeState.blurEnabled]。后台/前台门控按 state 记录，
 * 在各处 [dev.chrisbanes.haze.blur.hazeBlur] 经 [recoverableBlurEnabled] 应用。
 */
private val recoverableBlurGates: MutableMap<HazeState, MutableState<Boolean>> =
    Collections.synchronizedMap(WeakHashMap())

@Composable
fun recoverableBlurEnabled(state: HazeState): Boolean {
    val gate = recoverableBlurGates[state] ?: return true
    return gate.value
}

/** 供 design-system 共享脚手架读取的响应式门控；未注册的 state 视为始终允许。 */
@Composable
fun recoverableBlurEnabledState(state: HazeState): State<Boolean> {
    val gate = recoverableBlurGates[state]
    return gate ?: remember(state) { mutableStateOf(true) }
}

/**
 * 共享脚手架顶栏模糊的 app 端句柄工厂：state 带后台门控与回前台重建策略，
 * 用户关闭「顶部模糊」时不创建（design-system 侧保持原生容器色）。
 */
@Composable
fun rememberAppScaffoldHazeHandle(userEnabled: Boolean): AdaptiveScaffoldHazeHandle? {
    if (!userEnabled) return null
    val state = rememberRecoverableHazeState(userEnabled = userEnabled, initialBlurEnabled = true)
    val blurEnabled = recoverableBlurEnabledState(state)
    return remember(state) { AdaptiveScaffoldHazeHandle(state, blurEnabled) }
}

/** 与玻璃采样共用页面曝光边界；返回手势中仍露出的页面继续保留采样。 */
@Composable
fun Modifier.hazeSourceCompat(state: HazeState): Modifier {
    if (!shouldAllowRenderEffectBackedHazeEffect(Build.VERSION.SDK_INT)) return this
    if (!LocalChromeBackdropRecordingAllowed.current) return this
    return hazeSource(state)
}

internal fun shouldEnableRecoverableHeavyVisualEffects(
    userEnabled: Boolean,
    isAppInBackground: Boolean
): Boolean {
    return userEnabled && !isAppInBackground
}

/**
 * 回前台时重建 haze state 的 SDK 门槛。
 *
 * 只在「回前台」重建：进后台时界面不可见，重建没有收益，还会把一次全屏重录的成本
 * 推到回前台后的第一帧上。
 */
internal fun shouldRecreateRecoverableHazeState(
    sdkInt: Int
): Boolean {
    return sdkInt >= 36
}

internal fun shouldAllowHomeChromeLiquidGlass(
    sdkInt: Int
): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU
}

internal fun resolveHomeChromeLiquidGlassEnabled(
    userEnabled: Boolean,
    sdkInt: Int
): Boolean {
    return userEnabled && shouldAllowHomeChromeLiquidGlass(sdkInt)
}

internal fun shouldAllowRuntimeShaderBackedHazeEffect(
    sdkInt: Int
): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU
}

internal fun shouldAllowRenderEffectBackedHazeEffect(
    sdkInt: Int
): Boolean {
    return sdkInt >= Build.VERSION_CODES.S
}

internal fun shouldAllowDirectHazeLiquidGlassFallback(
    sdkInt: Int
): Boolean {
    return shouldAllowHomeChromeLiquidGlass(sdkInt) && sdkInt < 36
}

@Composable
fun rememberRecoverableHazeState(
    userEnabled: Boolean = true,
    initialBlurEnabled: Boolean = true,
    sdkInt: Int = Build.VERSION.SDK_INT
): HazeState {
    var isAppInBackground by remember { mutableStateOf(BackgroundManager.isInBackground) }
    var recreationKey by remember { mutableIntStateOf(0) }
    val shouldRecreateState = shouldRecreateRecoverableHazeState(sdkInt)
    val hazeState = remember(recreationKey) {
        HazeState()
    }
    val blurGate = remember(hazeState, initialBlurEnabled) {
        mutableStateOf(initialBlurEnabled).also { recoverableBlurGates[hazeState] = it }
    }

    DisposableEffect(hazeState) {
        recoverableBlurGates[hazeState] = blurGate
        onDispose {
            recoverableBlurGates.remove(hazeState)
        }
    }

    DisposableEffect(shouldRecreateState) {
        val listener = object : BackgroundManager.BackgroundStateListener {
            override fun onEnterBackground() {
                isAppInBackground = true
                // 进后台时不重建：界面不可见，重录全屏 haze source 没有意义，反而会让回前台
                // 后的第一帧额外承担一次重建成本。blurGate 已经通过 isAppInBackground 关掉了。
            }

            override fun onEnterForeground() {
                isAppInBackground = false
                if (shouldRecreateState) {
                    recreationKey += 1
                }
            }
        }
        BackgroundManager.addListener(listener)
        onDispose {
            BackgroundManager.removeListener(listener)
        }
    }

    SideEffect {
        blurGate.value = shouldEnableRecoverableHeavyVisualEffects(
            userEnabled = userEnabled,
            isAppInBackground = isAppInBackground
        )
    }

    return hazeState
}
