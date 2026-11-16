// 文件路径: feature/home/components/LastPositionPill.kt
package com.android.purebilibili.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.time.Duration.Companion.milliseconds


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
    refreshKey: Long,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeCoordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    var dissolving by remember(refreshKey) { mutableStateOf(false) }

    // 首次创建即播放进入动画；消失交给调用方移除本组件完成。
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }

    // 胶囊悬浮在内容之上，长时间停留会遮住标题；3 秒无操作自动收起。
    LaunchedEffect(refreshKey) {
        delay(3_000.milliseconds)
        dissolving = true
    }

    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        modifier = modifier,
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
                modifier = Modifier.heightIn(min = AppChromeSizeTokens.MinimumTouchTarget),
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
