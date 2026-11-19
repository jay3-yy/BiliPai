package com.android.purebilibili.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 *  共享元素过渡作用域 Provider
 * 
 * 提供 SharedTransitionScope 和 AnimatedVisibilityScope 给整个应用使用，
 * 实现卡片与视频详情页的共享元素过渡动画
 */

/**
 *  CompositionLocal 用于传递 SharedTransitionScope
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 *  CompositionLocal 用于传递 AnimatedVisibilityScope
 * 每个 NavHost composable 块都会提供自己的 AnimatedVisibilityScope
 */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 当前视频详情 entry 的真实导航信号。独立 VideoActivity 不在 NavDisplay 内，保留 null 默认值。
 * 只在 observer 中读取进度/角色，避免让详情页订阅逐帧深度。
 */
internal val LocalVideoDetailNavigationTransitionScope =
    compositionLocalOf<NavigationEntryTransition?> { null }

internal interface NavigationEntryTransition {
    val isTop: Boolean
    val isRunning: Boolean
    val isRemoving: Boolean
    val relativeDepth: Float
}

/**
 * 全局共享元素动画开关，所有来源页和目标页必须共同遵守。
 */
val LocalSharedTransitionEnabled = compositionLocalOf { false }

/**
 *  SharedTransitionLayout 包装器
 * 包裹整个应用的导航，提供共享元素过渡能力
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionProvider(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    SharedTransitionLayout(
        modifier = Modifier.fillMaxSize()  // 📐 [修复] 确保在平板上填充整个屏幕
    ) {
        val sharedTransitionScope = if (enabled) this else null
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedTransitionScope,
            LocalSharedTransitionEnabled provides enabled
        ) {
            content()
        }
    }
}

/**
 *  提供 AnimatedVisibilityScope 的辅助函数
 * 在 NavHost 的 composable 块中调用
 */
@Composable
fun ProvideAnimatedVisibilityScope(
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAnimatedVisibilityScope provides animatedVisibilityScope) {
        content()
    }
}
