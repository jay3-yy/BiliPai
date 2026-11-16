package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.feature.video.ui.components.LocalVideoSkeletonAnimationAllowed

/** 分栏独立交接，保留同一骨架及其扫光直到正文完全覆盖；材质与分栏尺寸由调用方决定。 */
@Composable
internal fun VideoDetailSkeletonHandoff(
    identity: String,
    ready: Boolean,
    loading: Boolean,
    entryContentReady: Boolean = true,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) = key(identity) {
    val mountContent = ready && entryContentReady
    // 挂载许可不重启动画；已有详情继续沿用原先的揭示时机，不追加一次入场淡入。
    val reveal = rememberVideoDetailLateContentReveal(animateOnMount = false, contentVisible = ready)
    val retainSkeleton by remember(ready, loading, entryContentReady) {
        // 详情先到、挂载许可未到时继续保留骨架，避免分栏短暂露出空白。
        derivedStateOf { loading || (ready && (!entryContentReady || reveal.value < 1f)) }
    }
    Box(modifier) {
        if (retainSkeleton) {
            // 入场等待只保留静态占位；放行后的扫光继续由原有可见性门控控制。
            CompositionLocalProvider(
                LocalVideoSkeletonAnimationAllowed provides
                    (entryContentReady && LocalVideoSkeletonAnimationAllowed.current),
            ) { skeleton() }
        }
        if (mountContent) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = reveal.value }
                    .background(AppSurfaceTokens.surface()),
            ) { content() }
        }
    }
}
