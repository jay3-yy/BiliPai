package com.android.purebilibili.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
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

/** 原生视频与弹幕表面留在页面层，共享轮廓运动时隐藏并由 Compose 封面承接。 */
internal val LocalNavigationSharedSurfaceVisible = compositionLocalOf { true }

internal fun supportsNavigationSharedContainer(
    target: BiliPaiNavKey,
    owner: BiliPaiNavKey? = null,
): Boolean = when (target) {
    // 视频详情支持从列表页和相关推荐卡片进入；父详情被子共享容器遮挡期间同步隐藏原生 Surface，
    // 避免双播放器表面穿透共享叠层。
    is BiliPaiNavKey.VideoDetail -> true
    // 独立全屏流、离线播放和 WebView 没有可参与此容器绘制的目标。
    is BiliPaiNavKey.Story, is BiliPaiNavKey.OfflineVideoPlayer, is BiliPaiNavKey.Web -> false
    else -> resolveSystemWindowCardIdentity(target) != null
}

internal class NavigationSharedContainerSource(
    val sources: SettingsSharedContainerSources,
    val origin: SettingsSharedContainerOrigin,
) {
    fun activate(target: BiliPaiNavKey): Boolean {
        if (!supportsNavigationSharedContainer(target, origin.owner)) return false
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
        // 来源节点（如底部小横条或回收后重建的列表卡片）返回时重新挂载，
        // 复用原转场登记的 origin，才能重新接回 sharedBounds 而不是退化为淡出。
        val existing = sources.sources.values.firstOrNull {
            it.owner == owner && it.sourceIdentity == identity
        }
        NavigationSharedContainerSource(
            sources,
            existing ?: SettingsSharedContainerOrigin(owner, color, sourceIdentity = identity),
        )
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
    val updateRadius = remember(source, shape, density, direction) {
        { coordinates: LayoutCoordinates ->
            val radius = AppShapes.transitionCornerRadiusPx(
                shape, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()), direction, density,
            ) / density.density
            source.origin.topRadius = radius.dp
            source.origin.bottomRadius = radius.dp
        }
    }
    SideEffect { source.origin.radiusUpdater = updateRadius }
    return Modifier.onGloballyPositioned {
        val origin = source.origin
        origin.coordinates = it
        // 列表静止滚动期不逐帧算可见边界与圆角，仅在点击 capture 与共享转场激活时更新。
        if (origin.sharedBounds != null) {
            origin.available = source.sources.isAvailable(it)
            updateRadius(it)
        }
    }.then(settingsSharedContainerBounds(source.origin.sharedBounds))
}
