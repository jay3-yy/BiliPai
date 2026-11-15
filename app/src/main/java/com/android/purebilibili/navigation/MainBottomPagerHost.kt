package com.android.purebilibili.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.core.ui.components.AppHorizontalPager
import com.android.purebilibili.feature.home.components.BottomNavItem

/**
 * 挂载记录只属于这一次 MainHost 组合。返回或窗口重建时先恢复当前页，
 * 其余页按空闲预算挂载；页面数据和滚动位置由外层的 ViewModel / SaveableStateHolder 保留。
 * 玻璃、MD3 和 Miuix 共用此生命周期，不改变各自的材质与布局参数。
 */
@Composable
internal fun MainBottomPagerHost(
    state: MainBottomPagerState,
    visibleItems: List<BottomNavItem>,
    preloadAllowed: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int, item: BottomNavItem) -> Unit,
) {
    val pager = state.pagerState
    var contentReady by remember { mutableStateOf(false) }
    var mountedItems by remember { mutableStateOf(emptySet<BottomNavItem>()) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        contentReady = true
    }
    LaunchedEffect(pager, state, visibleItems) {
        snapshotFlow {
            setOf(pager.currentPage, state.selectedPage, state.navigationStartPage)
                .mapNotNull { visibleItems.getOrNull(it) }.toSet()
        }.collect { mountedItems = mountedItems + it }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val windowInfo = LocalWindowInfo.current
    val visualWorkAllowed by rememberUpdatedState(LocalVisualWorkAllowed.current)
    val latestPreloadAllowed by rememberUpdatedState(preloadAllowed)
    val allowPreload by remember(lifecycle, windowInfo) {
        derivedStateOf {
            lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && windowInfo.isWindowFocused &&
                visualWorkAllowed && latestPreloadAllowed()
        }
    }
    LaunchedEffect(allowPreload, visibleItems) {
        if (!allowPreload) return@LaunchedEffect
        preloadBottomPagerPages(
            visibleItems = visibleItems,
            preloadedItems = { mountedItems },
            awaitFrame = { withFrameNanos { } },
            awaitIdle = { awaitBottomPagerPreloadIdle() },
            isPreloadAllowed = {
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    windowInfo.isWindowFocused && visualWorkAllowed && latestPreloadAllowed()
            },
            onPreload = { mountedItems = mountedItems + it },
        )
    }
    AppHorizontalPager(
        modifier = modifier,
        state = pager,
        key = { resolveBottomPagerLayoutKey(it, visibleItems) },
        beyondViewportPageCount = resolveBottomPagerBeyondViewportPageCount(
            visibleItems.size, contentReady,
        ).coerceAtMost(BOTTOM_PAGER_MAX_PRELOAD_DISTANCE),
        userScrollEnabled = shouldEnableBottomPagerUserScroll(),
    ) { page ->
        val item = visibleItems.getOrNull(page)
        val mounted by remember(item) { derivedStateOf { item != null && item in mountedItems } }
        if (item != null && shouldComposeBottomPagerPage(
                item, page, pager.currentPage, state.selectedPage, state.isNavigating,
                state.navigationStartPage, mounted,
            )
        ) {
            BottomPagerPageLifecycle(state, page) { content(page, item) }
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

/** 目标页立即订阅数据；只露出的来源页保持 STARTED，离屏预热页保持 CREATED。 */
@Composable
internal fun BottomPagerPageLifecycle(
    state: MainBottomPagerState,
    page: Int,
    content: @Composable () -> Unit,
) {
    val maxLifecycle by remember(state, page) {
        derivedStateOf {
            val pager = state.pagerState
            val visible = kotlin.math.abs(page - pager.currentPage - pager.currentPageOffsetFraction) < 1f
            when {
                page == state.selectedPage && !state.isNavigating && !pager.isScrollInProgress ->
                    Lifecycle.State.RESUMED
                page == state.selectedPage || visible -> Lifecycle.State.STARTED
                else -> Lifecycle.State.CREATED
            }
        }
    }
    val owner = rememberLifecycleOwner(maxLifecycle = maxLifecycle)
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}
