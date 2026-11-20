package com.android.purebilibili.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope
import kotlinx.coroutines.flow.first

internal class ControlGroupLandingDelivery(private val deliver: () -> Unit) {
    private var delivered = false
    fun land() {
        if (delivered) return
        delivered = true
        deliver()
    }
}

internal class NavigationControlMotionGroupState {
    var systemReturn by mutableStateOf<ControlGroupLandingDelivery?>(null)
}

internal val LocalNavigationControlMotionGroup =
    staticCompositionLocalOf<NavigationControlMotionGroupState?> { null }

/** 只提供分组，不新增布局或材质；手机、大屏和窄窗口使用各自原有排版。 */
@Composable
internal fun NavigationControlMotionGroup(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavigationControlMotionGroup provides rememberNavigationControlMotionGroup(), content = content)
}

@Composable
internal fun rememberNavigationControlMotionGroup(): NavigationControlMotionGroupState {
    val group = remember { NavigationControlMotionGroupState() }
    val entry = rememberUpdatedState(LocalVideoDetailNavigationTransitionScope.current)
    val systemReturn = group.systemReturn
    LaunchedEffect(group, systemReturn) {
        if (systemReturn == null) return@LaunchedEffect
        withFrameNanos { }
        snapshotFlow {
            entry.value?.isTop != false && entry.value?.isRunning != true &&
                SystemWindowControlNeighbors.isGroupRestored(group)
        }.first { it }
        systemReturn.land()
    }
    return group
}
