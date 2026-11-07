package com.android.purebilibili.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope
import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.feature.settings.SettingsContainerAnchor
import com.android.purebilibili.feature.settings.SettingsContainerCompanion
import com.android.purebilibili.feature.settings.SettingsContainerSources
import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlinx.coroutines.flow.first

// 复用导航容器的坐标、快照及失效校验；目录独立于设置动效开关。
internal val LocalLiveCardContainerSources = staticCompositionLocalOf<SettingsContainerSources?> { null }
internal val LocalLiveCardContainerOwner = staticCompositionLocalOf<BiliPaiNavKey?> { null }
internal val LocalLiveCardContainerActive = staticCompositionLocalOf { false }

internal data class LiveCardContainerTransition(
    val modifier: Modifier,
    val onClick: () -> Unit,
    val enabled: Boolean = false,
    val companionModifier: Modifier = Modifier,
    val badgeModifier: Modifier = Modifier,
)

private class LiveAvatarCompanionCapture(val layer: GraphicsLayer) {
    val anchor = SettingsContainerAnchor()

    fun snapshot(origin: Offset): SettingsContainerCompanion? =
        anchor.boundsInRoot?.takeIf { anchor.coordinates?.isAttached == true && anchor.recorded }
            ?.let { SettingsContainerCompanion(layer, it.translate(-origin)) }
}

@Composable
private fun rememberLiveAvatarCompanionCapture(): LiveAvatarCompanionCapture {
    val layer = rememberGraphicsLayer()
    val capture = remember(layer) { LiveAvatarCompanionCapture(layer) }
    DisposableEffect(capture) { onDispose { capture.anchor.detach() } }
    return capture
}

private fun LiveAvatarCompanionCapture.sourceModifier(hidden: () -> Boolean): Modifier = Modifier
    .onGloballyPositioned(anchor::update)
    .drawWithContent {
        // 快照始终保留原始字形，显隐只作用于列表中的原位绘制。
        val hide = hidden()
        if (!hide || !anchor.recorded) {
            layer.record { this@drawWithContent.drawContent() }
            anchor.recorded = true
        }
        if (!hide) drawLayer(layer)
    }

/** 每个实际入口持有自己的快照；同一房间的头像与卡片不会共用落点。 */
@Composable
internal fun rememberLiveCardContainerTransition(
    roomId: Long,
    title: String,
    hostName: String,
    color: Color,
    cornerRadius: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    includeAvatarCompanions: Boolean = false,
    containerTarget: BiliPaiNavKey? = null,
): LiveCardContainerTransition {
    val sources = LocalLiveCardContainerSources.current
    val owner = LocalLiveCardContainerOwner.current
    if (!enabled || sources == null || owner == null || (roomId <= 0 && containerTarget == null)) {
        val disabledTarget = containerTarget
            ?: BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = hostName)
        val disabledSystemSource = com.android.purebilibili.navigation3.rememberSystemWindowCardSource(disabledTarget)
            ?: return LiveCardContainerTransition(Modifier, onClick)
        val disabledRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
        // 容器形变关闭时仍登记系统窗口来源，系统一镜到底不受直播容器开关影响。
        return LiveCardContainerTransition(
            modifier = Modifier
                .onGloballyPositioned { disabledSystemSource.coordinates = it }
                .videoCardSourceAnchor(
                    disabledSystemSource.identity,
                    androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
                ),
            onClick = {
                disabledSystemSource.record(disabledRadiusPx)
                onClick()
            },
        )
    }
    val target = containerTarget ?: BiliPaiNavKey.Live(roomId = roomId.toString(), title = title, uname = hostName)
    val systemWindowSource = com.android.purebilibili.navigation3.rememberSystemWindowCardSource(target)
    val anchor = remember(target, sources, owner) { SettingsContainerAnchor() }
    var selected by remember(anchor) { mutableStateOf(false) }
    val navigationScope by rememberUpdatedState(LocalVideoDetailNavigationTransitionScope.current)
    val nameCapture = if (includeAvatarCompanions) rememberLiveAvatarCompanionCapture() else null
    val badgeCapture = if (includeAvatarCompanions) rememberLiveAvatarCompanionCapture() else null
    LaunchedEffect(selected, anchor) {
        if (!selected) return@LaunchedEffect
        // 先等真实离场，避免点击后的首帧仍处于深度 0 时提前解除选中。
        snapshotFlow { navigationScope?.relativeDepth ?: 0f }.first { it > 0f }
        snapshotFlow { navigationScope?.let { it.relativeDepth <= 0f && !it.isRunning } ?: true }.first { it }
        selected = false
    }
    val layer = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    SideEffect {
        sources.sources[target]?.takeIf { it.anchor === anchor }?.let { source ->
            source.layer = layer
            source.color = color
        }
    }
    DisposableEffect(anchor) {
        onDispose { anchor.detach() }
    }
    return LiveCardContainerTransition(
        modifier = Modifier
            .onGloballyPositioned {
                anchor.update(it)
                systemWindowSource?.coordinates = it
            }
            .then(
                if (systemWindowSource != null) {
                    Modifier.videoCardSourceAnchor(
                        systemWindowSource.identity,
                        androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
                    )
                } else Modifier
            )
            .drawWithContent {
                // 头像飞行期间复用点击时的图层，并隐藏原位头像，避免两份头像同时出现。
                // 封面卡片继续使用原来的绘制策略；音柱本身仍受页面/视口等可见性门控。
                val hideAvatar = includeAvatarCompanions && selected &&
                    navigationScope?.let { it.relativeDepth > 0f || it.isRunning } == true
                if (!hideAvatar || !anchor.recorded) {
                    layer.record { this@drawWithContent.drawContent() }
                    anchor.recorded = true
                }
                if (!hideAvatar &&
                    sources.sources[target]?.takeIf { it.anchor === anchor }?.returnInFlight?.invoke() != true
                ) {
                    drawLayer(layer)
                }
            },
        onClick = {
            // 系统一镜到底来源先于容器捕获登记；未登记成功时保持纯容器路径。
            systemWindowSource?.record(radiusPx)
            // 捕获失败时清除旧落点，深链或未完整显示的卡片回退普通导航。
            sources.sources.remove(target)
            sources.capture(
                target, owner, anchor, layer, color, radiusPx, radiusPx,
                avatarSource = includeAvatarCompanions,
            )
            selected = sources.sources[target]?.anchor === anchor
            sources.sources[target]?.takeIf { selected }?.companions = listOfNotNull(
                nameCapture?.snapshot(sources.hostOrigin),
                badgeCapture?.snapshot(sources.hostOrigin),
            )
            onClick()
        },
        enabled = true,
        // 昵称与标记由导航飞行层重放，原位内容只在来源重新呈现时接回。
        companionModifier = nameCapture?.sourceModifier {
            selected && navigationScope?.let { it.relativeDepth > 0f || it.isRunning } == true
        } ?: Modifier,
        badgeModifier = badgeCapture?.sourceModifier {
            selected && navigationScope?.let { it.relativeDepth > 0f || it.isRunning } == true
        } ?: Modifier,
    )
}
