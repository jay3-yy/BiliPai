package com.android.purebilibili.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
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

/** 可随 Compose 图层同步缩放与裁切的 TextureView 表面在自身确定性容器形变期间允许保持可见；被上层子容器覆盖时仍隐藏。 */
internal val LocalNavigationSharedTextureSurfaceVisible = compositionLocalOf { true }

internal fun supportsNavigationSharedContainer(
    target: BiliPaiNavKey,
    owner: BiliPaiNavKey? = null,
): Boolean = when (target) {
    // 视频详情支持从列表页和相关推荐卡片进入；父详情被子共享容器遮挡期间同步隐藏原生 Surface，
    // 避免双播放器表面穿透共享叠层。
    is BiliPaiNavKey.VideoDetail -> true
    // 独立全屏故事流走全屏过渡，离线播放与应用内网页与其他容器入口统一支持共享容器。
    is BiliPaiNavKey.Story -> false
    else -> resolveSystemWindowCardIdentity(target) != null
}

internal class NavigationSharedContainerSource(
    val sources: SettingsSharedContainerSources,
    val origin: SettingsSharedContainerOrigin,
) {
    internal var ownedCoordinates: LayoutCoordinates? = null

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
        // 只复用旧节点已卸载（未附着）的原转场 origin；同页并存多张同 bvid 活跃卡片时各自独立，
        // 避免后布局卡片覆写活跃源卡的坐标与快照层。
        val existing = sources.sources.values.firstOrNull {
            it.owner == owner && it.sourceIdentity == identity && it.coordinates?.isAttached != true
        }
        NavigationSharedContainerSource(
            sources,
            existing ?: SettingsSharedContainerOrigin(owner, color, sourceIdentity = identity),
        )
    }
    SideEffect { source.origin.color = color }
    DisposableEffect(source) {
        onDispose {
            // 列表项重组复用同一 origin 时，只释放仍属于当前实例的坐标，
            // 避免旧节点晚一步 dispose 把新节点的 coordinates 与 available 误清导致返回退化为淡出。
            if (source.origin.coordinates === source.ownedCoordinates) {
                source.origin.coordinates = null
                source.origin.available = false
            }
            source.ownedCoordinates = null
        }
    }
    return source
}

private class SharedSourceLayerHolder(
    private val graphicsContext: GraphicsContext,
) {
    var layer: GraphicsLayer? = null
    var recordedGeneration: Int = 0
    var recordedWidth: Float = 0f
    var recordedHeight: Float = 0f
    var recordedColor: Color = Color.Unspecified

    fun ensureLayer(): GraphicsLayer =
        layer ?: graphicsContext.createGraphicsLayer().also { layer = it }

    fun release(origin: SettingsSharedContainerOrigin) {
        val current = layer ?: return
        if (origin.capturedSourceLayer === current) {
            origin.capturedSourceLayer = null
            origin.capturedSourceLayerWidth = 0f
            origin.capturedSourceLayerHeight = 0f
        }
        graphicsContext.releaseGraphicsLayer(current)
        layer = null
        recordedGeneration = 0
        recordedWidth = 0f
        recordedHeight = 0f
        recordedColor = Color.Unspecified
    }
}

@Composable
internal fun navigationSharedContainerSourceModifier(
    source: NavigationSharedContainerSource?,
    shape: Shape,
    recordSourceLayer: Boolean = true,
): Modifier {
    if (source == null) return Modifier
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val graphicsContext = LocalGraphicsContext.current
    val layerHolder = remember(source, graphicsContext) { SharedSourceLayerHolder(graphicsContext) }
    val hadActiveBounds = remember(source) { mutableStateOf(false) }
    DisposableEffect(source, layerHolder) {
        onDispose {
            layerHolder.release(source.origin)
            if (source.origin.sharedBounds == null) {
                source.origin.capturedSourceBitmap = null
            }
        }
    }
    LaunchedEffect(source, layerHolder) {
        snapshotFlow { source.origin.sharedBounds != null }.collect { active ->
            if (active) {
                hadActiveBounds.value = true
            } else if (hadActiveBounds.value) {
                hadActiveBounds.value = false
                layerHolder.release(source.origin)
                source.origin.capturedSourceBitmap = null
                source.origin.capturedSourceLayerWidth = 0f
                source.origin.capturedSourceLayerHeight = 0f
            }
        }
    }
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
        source.ownedCoordinates = it
        origin.coordinates = it
        // 列表静止滚动期不逐帧算可见边界与圆角，仅在点击 capture 与共享转场激活时更新。
        if (origin.sharedBounds != null) {
            origin.available = source.sources.isAvailable(it)
            updateRadius(it)
        }
    }.drawWithContent {
        val origin = source.origin
        val activeBounds = origin.sharedBounds
        val generation = origin.captureGeneration
        val shouldCapture = recordSourceLayer && generation > 0 && (
            layerHolder.recordedGeneration != generation ||
                (activeBounds != null && activeBounds.deterministicGeometry && (
                    layerHolder.layer == null ||
                        layerHolder.recordedWidth != size.width ||
                        layerHolder.recordedHeight != size.height ||
                        layerHolder.recordedColor != origin.color
                    ))
            )
        var recordedLayer: GraphicsLayer? = null
        if (shouldCapture && size.width > 1f && size.height > 1f) {
            val layer = layerHolder.ensureLayer()
            layer.alpha = 1f
            layer.record {
                this@drawWithContent.drawContent()
            }
            layerHolder.recordedGeneration = generation
            layerHolder.recordedWidth = size.width
            layerHolder.recordedHeight = size.height
            layerHolder.recordedColor = origin.color
            origin.capturedSourceLayerWidth = size.width
            origin.capturedSourceLayerHeight = size.height
            origin.capturedSourceLayer = layer
            recordedLayer = layer
        }
        val hideForDeterministicFlight = activeBounds != null &&
            activeBounds.deterministicGeometry &&
            origin.available &&
            activeBounds.destinationPlaced &&
            origin.resolveSourceRectInHost?.invoke() != null &&
            isDeterministicSharedSourceCardHidden(
                bounds = activeBounds,
                hasSourceOverlay = origin.capturedSourceLayer != null || origin.capturedSourceBitmap != null,
            )
        if (!hideForDeterministicFlight) {
            if (recordedLayer != null) {
                drawLayer(recordedLayer)
            } else {
                drawContent()
            }
        }
    }.then(settingsSharedContainerBounds(source.origin.sharedBounds))
}
