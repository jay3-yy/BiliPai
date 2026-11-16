package com.android.purebilibili.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.android.purebilibili.core.ui.components.AppHorizontalPager
import com.android.purebilibili.feature.home.components.BottomNavItem

/**
 * 只创建当前页和切页参与者，不在空闲时挂载全部离屏页面树。
 * 页面数据和可保存状态由外层的 ViewModel / SaveableStateHolder 保留。
 * 玻璃、MD3 和 Miuix 共用此生命周期，不改变各自的材质与布局参数。
 */
@Composable
internal fun MainBottomPagerHost(
    state: MainBottomPagerState,
    visibleItems: List<BottomNavItem>,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int, item: BottomNavItem) -> Unit,
) {
    val pager = state.pagerState
    AppHorizontalPager(
        modifier = modifier,
        state = pager,
        key = { resolveBottomPagerLayoutKey(it, visibleItems) },
        beyondViewportPageCount = 0,
        userScrollEnabled = shouldEnableBottomPagerUserScroll(),
    ) { page ->
        val item = visibleItems.getOrNull(page)
        // 只订阅进入/离开视口的边界，不让切页的逐帧偏移驱动整页重组。
        val composePage by remember(page, state) {
            derivedStateOf {
                shouldComposeBottomPagerPage(
                    page, pager.currentPage, state.selectedPage, state.isNavigating,
                    state.navigationStartPage, pager.currentPageOffsetFraction,
                )
            }
        }
        if (item != null && composePage) {
            BottomPagerPageLifecycle(state, page) { content(page, item) }
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

/** 目标页立即订阅数据；切页中仍露出的来源页保持 STARTED。 */
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
