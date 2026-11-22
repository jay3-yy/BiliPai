package com.android.purebilibili.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import top.yukonga.miuix.kmp.basic.RefreshState

/**
 * Miuix 原生下拉刷新在松手回弹期间允许再次触摸接管。若新触摸随即上滑，
 * 原生容器会取消回弹弹簧；此时紧接着的松手可能仍被旧的 `isRebounding`
 * 锁拦截，指示器就会停在拉长位置，而列表继续滚动，表现为弹簧不跟卡片。
 *
 * 这个守卫只在“松手后原生回弹弹簧确实开始运行”的短窗口内消费上滑输入，
 * 让回弹完整走完；弹簧把卡片一起带回，避免取消回弹后卡在中间状态。
 * 首次按住下拉时的上滑、触发刷新后的回弹以及重新向下拉都不受影响。
 * 上游修复松手锁竞态后可移除。
 */
internal class MiuixPullReboundGuard(
    private val currentRefreshState: () -> RefreshState,
    private val currentDragOffset: () -> Float,
) {
    var armed by mutableStateOf(false)
        private set
    var armGeneration by mutableIntStateOf(0)
        private set

    private var pointerDown = false
    private var releaseOffset = 0f
    private var armStartOffset = 0f

    fun onPointerEvent(pressed: Boolean) {
        if (pointerDown && !pressed) {
            armIfRebounding()
        }
        pointerDown = pressed
    }

    private fun armIfRebounding() {
        if (currentRefreshState() != RefreshState.Pulling || currentDragOffset() <= 0f) {
            armed = false
            return
        }
        armed = true
        armStartOffset = currentDragOffset()
        releaseOffset = armStartOffset
        armGeneration++
    }

    fun onOffsetChanged(
        offset: Float,
        refreshState: RefreshState,
        isRefreshing: Boolean,
    ) {
        if (!armed) return
        if (isRefreshing || refreshState != RefreshState.Pulling || offset <= 0f) {
            armed = false
            return
        }
        when {
            offset > releaseOffset + OFFSET_EPSILON_PX -> {
                // 用户重新向下拉，后续交给原生容器接管。
                armed = false
            }

            offset < releaseOffset - OFFSET_EPSILON_PX -> {
                releaseOffset = offset
            }
        }
    }

    /**
     * 松手后经过几帧，原生回弹仍没有启动（松手被锁拦截）时解除守卫，
     * 避免继续吞掉用户的上滑。
     */
    fun confirmSpringStarted() {
        if (armed && currentDragOffset() >= armStartOffset - OFFSET_EPSILON_PX) {
            armed = false
        }
    }

    fun shouldConsumeUpwardInput(): Boolean =
        armed &&
            pointerDown &&
            currentDragOffset() > OFFSET_EPSILON_PX &&
            currentRefreshState() == RefreshState.Pulling

    companion object {
        private const val OFFSET_EPSILON_PX = 0.5f
        const val SPRING_START_CONFIRM_FRAMES = 3
    }
}

internal fun miuixReboundGuardConnection(
    guard: MiuixPullReboundGuard,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput &&
            available.y < 0f &&
            guard.shouldConsumeUpwardInput()
        ) {
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }
}
