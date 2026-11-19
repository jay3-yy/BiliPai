package com.android.purebilibili.feature.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.live.formatLiveSuperChatCountdown
import com.android.purebilibili.feature.live.resolveLiveSuperChatColor
import com.android.purebilibili.feature.live.remainingLiveSuperChatSeconds
import com.android.purebilibili.feature.live.resolveLiveSuperChatFlashEndTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

/**
 * SC 左下角浮层与列表复用同一张留言卡片
 *
 * 仅响应实时到达的新 SC（[flashFlow]）：
 * 在画面左下角以 SC 主题色展示 用户名 + 价格 + 内容 + 实时倒计时，
 * 右上角提供独立关闭按钮。不阻塞全屏画面触控与视线，按倒计时自动消失。
 */
@Composable
fun LiveSuperChatFlashOverlay(
    flashFlow: SharedFlow<LiveDanmakuItem>,
    modifier: Modifier = Modifier,
    onUserClick: (Long) -> Unit = {},
    onReport: (LiveDanmakuItem) -> Unit = {},
    persistUntilDismiss: Boolean = false,
    maxWidthDp: Int = 360,
    onCountdownPauseChange: (LiveDanmakuItem, Boolean) -> Unit = { _, _ -> },
    opacity: Float = 1f,
    cardScale: Float = 1f,
) {
    var current by remember { mutableStateOf<LiveDanmakuItem?>(null) }
    var remainingSec by remember { mutableIntStateOf(0) }
    var flashEndTime by remember { mutableLongStateOf(0L) }
    var menuPaused by remember { mutableStateOf(false) }

    LaunchedEffect(flashFlow) {
        flashFlow.collect { item ->
            val now = System.currentTimeMillis() / 1_000L
            flashEndTime = resolveLiveSuperChatFlashEndTime(item.superChatEndTime, now)
            remainingSec = remainingLiveSuperChatSeconds(flashEndTime, now)
            current = item
        }
    }

    // 倒计时与自动消失
    LaunchedEffect(current, flashEndTime, persistUntilDismiss, menuPaused) {
        if (current == null || persistUntilDismiss || menuPaused) return@LaunchedEffect
        while (remainingSec > 0) {
            delay(1_000L)
            remainingSec -= 1
        }
        current = null
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        AnimatedVisibility(
            visible = current != null && (persistUntilDismiss || remainingSec > 0),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .padding(start = 25.dp, bottom = 25.dp)
                .widthIn(max = maxWidthDp.coerceIn(260, 640).dp)
        ) {
            val item = current ?: return@AnimatedVisibility
            Box(Modifier.liveSuperChatAppearance(opacity, cardScale)) {
                LiveSuperChatCard(
                    item = item.copy(superChatEndTime = flashEndTime),
                    onExpired = { current = null },
                    onUserClick = { onUserClick(item.uid) },
                    onReport = { onReport(item) },
                    persistent = persistUntilDismiss,
                    onMenuVisibilityChange = { menuPaused = it },
                    onCountdownPauseChange = { paused -> onCountdownPauseChange(item, paused) },
                )
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)
                        .size(24.dp).clip(CircleShape).background(Color.White)
                        .clickable { current = null },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(Icons.Filled.Close, "关闭", tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
internal fun LiveSuperChatPreview(
    maxWidthDp: Int,
    opacity: Float,
    cardScale: Float,
    modifier: Modifier = Modifier,
) {
    val example = remember {
        LiveDanmakuItem(
            text = "这是一条醒目留言示例",
            uname = "全屏醒目留言预览",
            isSuperChat = true,
            superChatPrice = "¥30",
        )
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomStart) {
        Box(Modifier.width(minOf(maxWidth, maxWidthDp.dp)).liveSuperChatAppearance(opacity, cardScale)) {
            LiveSuperChatCard(
                item = example,
                persistent = true,
                interactive = false,
                onExpired = {},
                onUserClick = {},
                onReport = {},
            )
        }
    }
}

private fun Modifier.liveSuperChatAppearance(opacity: Float, scale: Float): Modifier = graphicsLayer {
    alpha = opacity
    scaleX = scale
    scaleY = scale
    transformOrigin = TransformOrigin(0f, 1f)
    compositingStrategy = CompositingStrategy.ModulateAlpha
}
