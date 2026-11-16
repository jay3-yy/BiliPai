// 文件路径: feature/home/components/LastPositionPill.kt
package com.android.purebilibili.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.animation.DissolvableVideoCard
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import kotlin.math.roundToInt


private const val CloseHitTargetSizeDp = 22
private const val CloseIconSizeDp = 14
private const val TextCloseGapDp = 6

/**
 * 「定位上次刷新」悬浮胶囊。
 *
 * 是否显示由调用方决定：这一轮不需要定位就不要创建它。创建之后，胶囊自己负责
 * 进入动画、停留计时、自动收起、粒子消散和 × 的点击，结束时通过 [onDismiss]
 * 通知调用方移除自己。
 *
 * @param refreshKey 刷新轮次标识。同一轮内胶囊只出现一次，键变化时重新计时；
 * 同时用于生成粒子消散的任务标识，避免不同轮次互相干扰。
 * @param onDismiss 胶囊彻底消失（消散完成或点击定位）后的回调。调用方应在此停止
 * 创建本组件，并让其状态能跨页面销毁恢复，否则切页回来会重新弹出。
 * @param onClick 点击胶囊本体的定位动作，例如滚动到上次刷新位置。触发后胶囊立即收起，
 * 不播放消散动画。
 * @param modifier 外部布局修饰符，负责胶囊的停靠位置与底部避让。
 */
@Composable
internal fun LastPositionPill(
    refreshKey: Any,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadedSettings by remember(context) { SettingsManager.getHomeSettings(context) }
        .collectAsStateWithLifecycle<HomeSettings?>(initialValue = null)
    val settings = loadedSettings ?: return
    val position = remember {
        RefreshLocatorDragPosition(settings.refreshLocatorPositionX, settings.refreshLocatorPositionY)
    }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(settings.refreshLocatorPositionX, settings.refreshLocatorPositionY) {
        if (!dragging) {
            position.x = settings.refreshLocatorPositionX
            position.y = settings.refreshLocatorPositionY
        }
    }
    val closeCoordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    var dissolving by rememberSaveable(refreshKey) { mutableStateOf(false) }

    // 首次创建即播放进入动画；消失交给调用方移除本组件完成。
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }

    // 拖动时不自动收起，松手或取消后重新计算完整停留时间；0 表示由用户关闭。
    LaunchedEffect(refreshKey, settings.refreshLocatorDurationSeconds, dragging, visible) {
        if (!visible || dragging || dissolving || settings.refreshLocatorDurationSeconds == 0) return@LaunchedEffect
        delay(settings.refreshLocatorDurationSeconds * 1_000L)
        dissolving = true
    }

    // 调用方提供当前页面的内容区及底部避让；拖动只改变测量阶段的位置，三种材质共用。
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                position.availableWidth = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                position.availableHeight = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    if (visible) {
                        placeable.place(
                            (position.availableWidth * position.x).roundToInt(),
                            (position.availableHeight * position.y).roundToInt(),
                        )
                    }
                }
            },
    ) {
        DissolvableVideoCard(
            isDissolving = dissolving,
            onDissolveComplete = onDismiss,
            cardId = "old-content-locator-$refreshKey",
            preset = DissolveAnimationPreset.TELEGRAM_FAST,
            collapseAfterDissolve = false,
            publishGlobalDissolveState = false,
            keepInvisibleAfterDissolve = true,
            // 自动收起与手动关闭共用关闭按钮中心。
            reverseSweep = true,
            originInWindow = {
                closeCoordinates[0]?.takeIf { it.isAttached }?.boundsInWindow()?.center
                    ?.let { android.graphics.PointF(it.x, it.y) }
            },
        ) {
            HomeOverlayPillButton(
                onClick = {
                    if (!dissolving) {
                        onClick()
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
                    .pointerInput(position, dissolving) {
                        if (dissolving) return@pointerInput
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val x = position.x
                                val y = position.y
                                scope.launch { SettingsManager.setRefreshLocatorPosition(context, x, y) }
                            },
                            onDragCancel = { dragging = false },
                        ) { change, delta ->
                            change.consume()
                            if (position.availableWidth > 0) {
                                position.x = (position.x + delta.x / position.availableWidth).coerceIn(0f, 1f)
                            }
                            if (position.availableHeight > 0) {
                                position.y = (position.y + delta.y / position.availableHeight).coerceIn(0f, 1f)
                            }
                        }
                    },
            ) {
                AppText("定位上次刷新")
                Spacer(modifier = Modifier.width(TextCloseGapDp.dp))
                Box(
                    modifier = Modifier
                        .size(CloseHitTargetSizeDp.dp)
                        .onGloballyPositioned { closeCoordinates[0] = it }
                        .clip(CircleShape)
                        .clickable(enabled = !dissolving) { dissolving = true },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(CloseIconSizeDp.dp)
                    )
                }
            }
        }
    }
}

private class RefreshLocatorDragPosition(initialX: Float, initialY: Float) {
    var x by mutableFloatStateOf(initialX)
    var y by mutableFloatStateOf(initialY)
    var availableWidth = 0
    var availableHeight = 0
}
