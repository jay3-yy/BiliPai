package com.android.purebilibili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Freeze the displayed state while a navigation transition owns the home surface.
 * The producer keeps its latest value; resuming collects that value without replaying
 * intermediate pages. A different source starts from its own cache, even while paused.
 */
@Composable
internal fun <T> StateFlow<T>.collectAsHomeFeedState(updatesAllowed: Boolean): State<T> {
    val source = this
    return key(source) {
        val updates = remember(source, updatesAllowed) {
            if (updatesAllowed) source else emptyFlow<T>()
        }
        updates.collectAsStateWithLifecycle(initialValue = source.value)
    }
}

private fun isHomeChromeContentReady(content: CategoryContent): Boolean =
    !(content.isLoading && content.videos.isEmpty() && content.liveRooms.isEmpty())

/** Background consumers need readiness, not every page, loading flag or error update. */
@Composable
internal fun StateFlow<CategoryContent>.collectHomeChromeContentReady(
    updatesAllowed: Boolean,
): State<Boolean> {
    val source = this
    return key(source) {
        val updates = remember(source, updatesAllowed) {
            if (updatesAllowed) source.map(::isHomeChromeContentReady).distinctUntilChanged()
            else emptyFlow<Boolean>()
        }
        updates.collectAsStateWithLifecycle(initialValue = isHomeChromeContentReady(source.value))
    }
}

/**
 * 返回保护窗（返回动画标记 / 卡片转场 settle）丢失最终 Idle 通知时的看门狗。
 * 门控本应在几百毫秒的返回动画内清除；独立播放窗口、画中画或 NavDisplay settle
 * 丢失时（issue #1131）会一直挂着，把已挂载分类的数据收集冻结成“下拉刷新无新
 * 内容”。首页可见期间门控持续超过 [HOME_STALE_RETURN_GATE_RECOVERY_MS] 即强制
 * 视为已恢复；门控清除或首页离开时计时重置。
 */
@Composable
internal fun rememberStaleReturnGateRecovered(
    homeVisible: Boolean,
    gateActive: Boolean,
): Boolean {
    var gateStartedAtMs by remember { mutableStateOf(0L) }
    var recovered by remember { mutableStateOf(false) }
    LaunchedEffect(homeVisible, gateActive) {
        if (homeVisible && gateActive) {
            gateStartedAtMs = System.currentTimeMillis()
            recovered = false
            delay(HOME_STALE_RETURN_GATE_RECOVERY_MS)
            if (shouldForceRecoverStaleReturnGate(
                    homeVisible = homeVisible,
                    gateActive = gateActive,
                    gateStartedAtMs = gateStartedAtMs,
                    nowMs = System.currentTimeMillis(),
                )
            ) {
                recovered = true
            }
        } else {
            gateStartedAtMs = 0L
            recovered = false
        }
    }
    return recovered
}
