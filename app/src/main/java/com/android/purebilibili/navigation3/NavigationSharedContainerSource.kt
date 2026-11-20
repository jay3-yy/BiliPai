package com.android.purebilibili.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.feature.settings.LocalSettingsContainerOwner
import com.android.purebilibili.feature.settings.SettingsSharedContainerOrigin
import com.android.purebilibili.feature.settings.SettingsSharedContainerSources

internal val LocalNavigationSharedContainerSources =
    staticCompositionLocalOf<SettingsSharedContainerSources?> { null }

/** 原生视频表面留在页面层，共享轮廓运动时由 Compose 封面承接。 */
internal val LocalNavigationSharedSurfaceVisible = staticCompositionLocalOf { true }

internal fun supportsNavigationSharedContainer(target: BiliPaiNavKey): Boolean = when (target) {
    // 视频详情暂时使用普通页面转场，避免共享录制卷入播放器的渲染树。
    is BiliPaiNavKey.VideoDetail -> false
    // 独立全屏流和 WebView 没有可参与此容器绘制的目标。
    is BiliPaiNavKey.Story, is BiliPaiNavKey.OfflineVideoPlayer, is BiliPaiNavKey.Web -> false
    else -> resolveSystemWindowCardIdentity(target) != null
}

internal class NavigationSharedContainerSource(
    val sources: SettingsSharedContainerSources,
    val origin: SettingsSharedContainerOrigin,
) {
    fun activate(target: BiliPaiNavKey): Boolean {
        if (!supportsNavigationSharedContainer(target)) return false
        sources.capture(target, origin)
        return sources.sources[target] === origin
    }
}

@Composable
internal fun rememberNavigationSharedContainerSource(identity: Any): NavigationSharedContainerSource? {
    val sources = LocalNavigationSharedContainerSources.current ?: return null
    val owner = LocalSettingsContainerOwner.current ?: return null
    val color = AppSurfaceTokens.groupedListContainer()
    val source = remember(sources, owner, identity) {
        NavigationSharedContainerSource(sources, SettingsSharedContainerOrigin(owner, color))
    }
    SideEffect { source.origin.color = color }
    DisposableEffect(source) {
        onDispose {
            source.origin.coordinates = null
            source.origin.available = false
        }
    }
    return source
}

@Composable
internal fun navigationSharedContainerSourceModifier(
    source: NavigationSharedContainerSource?,
    shape: Shape,
): Modifier {
    if (source == null) return Modifier
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return Modifier.onGloballyPositioned {
        val origin = source.origin
        origin.coordinates = it
        origin.available = source.sources.isAvailable(it)
        val radius = AppShapes.transitionCornerRadiusPx(
            shape, Size(it.size.width.toFloat(), it.size.height.toFloat()), direction, density,
        ) / density.density
        origin.topRadius = radius.dp
        origin.bottomRadius = radius.dp
    }.then(settingsSharedContainerBounds(source.origin.sharedBounds))
}
