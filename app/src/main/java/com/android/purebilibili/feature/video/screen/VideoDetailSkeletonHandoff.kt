package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.AppSurfaceTokens

/** 分栏独立交接，保留同一骨架及其扫光直到正文完全覆盖；材质与分栏尺寸由调用方决定。 */
@Composable
internal fun VideoDetailSkeletonHandoff(
    identity: String,
    ready: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) = key(identity) {
    val reveal = rememberVideoDetailLateContentReveal(animateOnMount = false, contentVisible = ready)
    val retainSkeleton by remember(ready, loading) {
        derivedStateOf { loading || (ready && reveal.value < 1f) }
    }
    Box(modifier) {
        if (retainSkeleton) skeleton()
        if (ready) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = reveal.value }
                    .background(AppSurfaceTokens.surface()),
            ) { content() }
        }
    }
}
