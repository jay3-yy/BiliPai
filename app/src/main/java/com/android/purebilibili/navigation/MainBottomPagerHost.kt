package com.android.purebilibili.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.android.purebilibili.core.ui.motion.AppTabIndicatorMotion
import com.android.purebilibili.core.ui.motion.iosMorphTween
import com.android.purebilibili.feature.home.components.BottomNavItem

/**
 * 点击底栏时直接过渡两端内容，不通过分页预跳创建中间栏目。
 * 已退出的页面在转场结束后卸载；数据和可保存状态由外层保留。
 * 玻璃、MD3 和 Miuix 共用此切页边界，页面尺寸不参与动画。
 */
@Composable
internal fun MainBottomPagerHost(
    state: MainBottomPagerState,
    visibleItems: List<BottomNavItem>,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int, item: BottomNavItem) -> Unit,
) {
    state.transition.AnimatedContent(
        modifier = modifier.clipToBounds(),
        contentKey = { resolveBottomPagerLayoutKey(it, visibleItems) },
        transitionSpec = {
            val direction = if (targetState > initialState) {
                AnimatedContentTransitionScope.SlideDirection.Start
            } else {
                AnimatedContentTransitionScope.SlideDirection.End
            }
            val transform = if (state.reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                slideIntoContainer(direction, iosMorphTween(AppTabIndicatorMotion.DurationMillis)) togetherWith
                    slideOutOfContainer(direction, iosMorphTween(AppTabIndicatorMotion.DurationMillis))
            }
            transform.using(null)
        },
    ) { page ->
        val item = visibleItems.getOrNull(page)
        Box(Modifier.fillMaxSize()) {
            if (item != null) {
                BottomPagerPageLifecycle(state, page) { content(page, item) }
            }
        }
    }
}

/** 目标页立即订阅数据；仍在退出的页面保持 STARTED，落位后只保留目标页。 */
@Composable
internal fun BottomPagerPageLifecycle(
    state: MainBottomPagerState,
    page: Int,
    content: @Composable () -> Unit,
) {
    val maxLifecycle = if (page == state.selectedPage && !state.isNavigating) {
        Lifecycle.State.RESUMED
    } else {
        Lifecycle.State.STARTED
    }
    val owner = rememberLifecycleOwner(maxLifecycle = maxLifecycle)
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}
