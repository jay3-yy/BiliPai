package com.android.purebilibili.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.live.rememberLiveCardContainerTransition
import com.android.purebilibili.navigation3.BiliPaiNavKey

/** 卡片详情进入导航栈，来源捕获失败时仍执行普通导航，不回退旧共享元素动画。 */
internal val LocalNativeCardNavigate = staticCompositionLocalOf<((BiliPaiNavKey) -> Unit)?> { null }

/** 内容卡片只跟随视频卡片动效开关，不依赖设置页容器动画。 */
internal val LocalNativeArticleCardMotionEnabled = staticCompositionLocalOf { false }

@Composable
internal fun rememberNativeCardContainerTransition(
    target: BiliPaiNavKey,
    onClick: () -> Unit,
    enabled: Boolean = true,
): com.android.purebilibili.feature.live.LiveCardContainerTransition {
    val resolvedTarget = com.android.purebilibili.feature.bangumi.resolveBangumiPlaybackEntry(target)
    val transition = rememberLiveCardContainerTransition(
        roomId = 0,
        title = "",
        hostName = "",
        color = MaterialTheme.colorScheme.surface,
        cornerRadius = 12.dp,
        containerTarget = resolvedTarget,
        enabled = enabled && LocalNativeCardNavigate.current != null &&
            (if (resolvedTarget is BiliPaiNavKey.ArticleDetail || resolvedTarget is BiliPaiNavKey.DynamicDetail ||
                resolvedTarget is BiliPaiNavKey.BangumiPlayer) {
                LocalNativeArticleCardMotionEnabled.current
            } else com.android.purebilibili.feature.space.LocalShopContainerMotionEnabled.current),
        onClick = LocalNativeCardNavigate.current?.let { navigate -> { navigate(resolvedTarget) } } ?: onClick,
    )
    return transition
}
