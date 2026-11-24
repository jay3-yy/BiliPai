package com.android.purebilibili.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.home.resolveNextHomeGlobalScrollOffset
import kotlin.math.abs
import kotlin.math.sign

/** 近顶（首项 + 小偏移）强制显示底栏的阈值。 */
const val BottomBarScrollHideTopRevealPx = 100

/** 累计滚动增量达到该阈值后才翻转显隐，避免一次反向抖动立刻切换。 */
const val BottomBarScrollHideDirectionThresholdPx = 48f

@Stable
data class BottomBarScrollHideState(
    val accumulatedY: Float = 0f,
)

enum class BottomBarScrollHideIntent {
    SHOW,
    HIDE,
}

@Stable
data class BottomBarScrollHideUpdate(
    val state: BottomBarScrollHideState,
    val intent: BottomBarScrollHideIntent?,
)

/**
 * 列表页底栏「向下浏览时隐藏」统一策略。
 *
 * 仅 [SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE] 启用；
 * 其余模式由应用壳决定显隐，子页只维护滚动偏移。
 */
fun shouldAutoHideBottomBarOnScroll(
    visibilityMode: SettingsManager.BottomBarVisibilityMode,
): Boolean {
    return visibilityMode == SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE
}

/**
 * 用 nested-scroll 增量推断底栏显隐。
 *
 * 多列网格首个可见 item 会在 lane 间切换，不能用 index 判断方向；
 * available.y 与布局锚点无关，手机/平板同样稳定。
 * 正 y 表示向上滚回顶部，负 y 表示向下浏览。
 */
fun reduceBottomBarScrollHideDelta(
    previousState: BottomBarScrollHideState,
    deltaY: Float,
    isAtTop: Boolean,
    thresholdPx: Float = BottomBarScrollHideDirectionThresholdPx,
): BottomBarScrollHideUpdate {
    if (isAtTop) {
        return BottomBarScrollHideUpdate(
            state = BottomBarScrollHideState(accumulatedY = 0f),
            intent = BottomBarScrollHideIntent.SHOW,
        )
    }
    if (deltaY == 0f) {
        return BottomBarScrollHideUpdate(state = previousState, intent = null)
    }

    val previousAccumulated = previousState.accumulatedY
    val accumulated = if (
        previousAccumulated == 0f || sign(previousAccumulated) == sign(deltaY)
    ) {
        previousAccumulated + deltaY
    } else {
        // 方向反转后重新累计，避免一次反向抖动立刻翻转显隐。
        deltaY
    }

    val safeThreshold = abs(thresholdPx)
    return when {
        accumulated <= -safeThreshold -> BottomBarScrollHideUpdate(
            state = BottomBarScrollHideState(accumulatedY = 0f),
            intent = BottomBarScrollHideIntent.HIDE,
        )
        accumulated >= safeThreshold -> BottomBarScrollHideUpdate(
            state = BottomBarScrollHideState(accumulatedY = 0f),
            intent = BottomBarScrollHideIntent.SHOW,
        )
        else -> BottomBarScrollHideUpdate(
            state = BottomBarScrollHideState(accumulatedY = accumulated),
            intent = null,
        )
    }
}

/**
 * 自动折叠期间钉住已预留的底部空间，底栏只做显隐、不拉动列表布局。
 */
@Composable
fun rememberStickyBottomBarContentPadding(
    autoHideEnabled: Boolean,
    liveBottomPadding: androidx.compose.ui.unit.Dp,
    isBottomBarVisible: Boolean,
): androidx.compose.ui.unit.Dp {
    var stickyBottomPadding by remember { mutableStateOf(liveBottomPadding) }
    LaunchedEffect(liveBottomPadding, isBottomBarVisible, autoHideEnabled) {
        if (!autoHideEnabled || isBottomBarVisible) {
            stickyBottomPadding = liveBottomPadding
        }
    }
    return if (autoHideEnabled) stickyBottomPadding else liveBottomPadding
}

/** 听视频小横条悬浮在内容上方时，列表/悬浮按钮需要上浮的额外避让高度。 */
val NowPlayingBarOverlayAvoidancePadding: androidx.compose.ui.unit.Dp =
    androidx.compose.ui.unit.Dp(76f)

/** 订阅听视频小横条是否悬浮在内容上方（AppNavigation 全局发布）。 */
@Composable
fun rememberNowPlayingBarOverlayVisible(): Boolean {
    return com.android.purebilibili.feature.audio.player.AudioNowPlayingSession
        .barOverlayVisible
        .collectAsStateWithLifecycle()
        .value
}

/**
 * 列表页共用 nested-scroll：连续累计偏移（搜索胶囊）+ 底栏显隐意图。
 */
@Stable
class BottomBarScrollHideConnection(
    private val chromeScrollOffset: MutableFloatState,
    private val autoHideEnabled: () -> Boolean,
    private val isAtTop: () -> Boolean,
    private val isActivePage: () -> Boolean,
    private val onVisibilityIntent: (BottomBarScrollHideIntent) -> Unit,
    private val hideState: MutableState<BottomBarScrollHideState>,
    private val feedScrollInProgress: MutableState<Boolean>? = null,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // 预加载或保留页面不能更新底栏连续偏移，也不能参与显隐判断。
        if (!isActivePage()) return Offset.Zero
        val nextOffset = resolveNextHomeGlobalScrollOffset(
            currentOffset = chromeScrollOffset.floatValue,
            scrollDeltaY = available.y,
            liquidGlassEnabled = false,
        )
        if (nextOffset != null) {
            // 与偏移更新在同一快照周期标记正在滚动，避免 SideEffect 滞后或子网格未监听导致小横条合体丢帧。
            feedScrollInProgress?.value = true
            chromeScrollOffset.floatValue = nextOffset
        }
        if (!autoHideEnabled()) return Offset.Zero

        val update = reduceBottomBarScrollHideDelta(
            previousState = hideState.value,
            deltaY = available.y,
            isAtTop = isAtTop(),
        )
        hideState.value = update.state
        when (update.intent) {
            BottomBarScrollHideIntent.SHOW -> onVisibilityIntent(BottomBarScrollHideIntent.SHOW)
            BottomBarScrollHideIntent.HIDE -> onVisibilityIntent(BottomBarScrollHideIntent.HIDE)
            null -> Unit
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(
        consumed: androidx.compose.ui.unit.Velocity,
        available: androidx.compose.ui.unit.Velocity,
    ): androidx.compose.ui.unit.Velocity {
        if (isActivePage()) {
            feedScrollInProgress?.value = false
        }
        return androidx.compose.ui.unit.Velocity.Zero
    }
}

/**
 * 创建列表页底栏显隐 + 连续偏移连接。调用方负责 dispose 时恢复底栏与偏移。
 */
@Composable
fun rememberBottomBarScrollHideConnection(
    chromeScrollOffset: MutableFloatState,
    autoHideEnabled: Boolean,
    isAtTop: () -> Boolean,
    isActivePage: Boolean,
    onVisibilityIntent: (BottomBarScrollHideIntent) -> Unit,
    hideState: MutableState<BottomBarScrollHideState> = remember { mutableStateOf(BottomBarScrollHideState()) },
    feedScrollInProgress: MutableState<Boolean>? = null,
): NestedScrollConnection {
    val currentAutoHideEnabled by rememberUpdatedState(autoHideEnabled)
    val currentIsAtTop by rememberUpdatedState(isAtTop)
    val currentIsActivePage by rememberUpdatedState(isActivePage)
    val currentOnVisibilityIntent by rememberUpdatedState(onVisibilityIntent)
    return remember(chromeScrollOffset, hideState, feedScrollInProgress) {
        BottomBarScrollHideConnection(
            chromeScrollOffset = chromeScrollOffset,
            autoHideEnabled = { currentAutoHideEnabled },
            isAtTop = { currentIsAtTop() },
            isActivePage = { currentIsActivePage },
            onVisibilityIntent = { currentOnVisibilityIntent(it) },
            hideState = hideState,
            feedScrollInProgress = feedScrollInProgress,
        )
    }
}
