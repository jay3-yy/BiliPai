package com.android.purebilibili.core.ui.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavRole

private val ReadyOutsideNavigation: State<Boolean> = mutableStateOf(true)

// 不在导航 entry 内的内容保留立即入场；只传 State 容器，避免导航宿主订阅动画进度。
internal val LocalAppNavigationEntranceReady = staticCompositionLocalOf { ReadyOutsideNavigation }

internal fun isNavigationEntryReadyForEntrance(role: NavRole, isRunning: Boolean): Boolean =
    role == NavRole.Top && !isRunning

/** 内容只等待自身容器就绪；普通导航不串行推迟列表挂载，返回预览不卸载已展示内容。 */
@Composable
internal fun rememberEntryContentReady(entryKey: Any, contentReady: Boolean): Boolean {
    val ready = remember(entryKey) { booleanArrayOf(contentReady) }
    val showContent = contentReady || ready[0]
    SideEffect { ready[0] = showContent }
    return showContent
}

@Composable
internal fun ProvideNavigationEntranceState(content: @Composable () -> Unit) {
    val scope = LocalNavTransitionScope.current
    val ready = remember(scope) {
        derivedStateOf(structuralEqualityPolicy()) {
            // 新页首个准备帧可能尚未启动 settle，必须同时检查角色，不能只看 isRunning。
            isNavigationEntryReadyForEntrance(scope.role, scope.isRunning)
        }
    }
    CompositionLocalProvider(LocalAppNavigationEntranceReady provides ready, content = content)
}
