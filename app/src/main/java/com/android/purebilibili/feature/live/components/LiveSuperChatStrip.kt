package com.android.purebilibili.feature.live.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.live.resolveLiveSuperChatColor
import com.android.purebilibili.feature.live.resolveLiveSuperChatDurationSec
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun LiveSuperChatStrip(
    items: List<LiveDanmakuItem>,
    persistent: Boolean,
    countdownEnabled: Boolean,
    remainingSeconds: (LiveDanmakuItem, Long) -> Int,
    maxCardWidthDp: Int,
    opacity: Float,
    scale: Float,
    onUserClick: (Long) -> Unit,
    onReport: (LiveDanmakuItem) -> Unit,
    onCountdownPauseChange: (LiveDanmakuItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<LiveDanmakuItem?>(null) }
    if (items.isEmpty()) return

    val countdownGate = rememberDecorativeAnimationGate()
    val showProgress = !persistent && LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val ticking = countdownEnabled && showProgress && countdownGate.active
    val nowEpochSeconds = remember { mutableLongStateOf(System.currentTimeMillis() / 1_000L) }
    LaunchedEffect(ticking) {
        if (!ticking) return@LaunchedEffect
        while (isActive) {
            val nowMillis = System.currentTimeMillis()
            nowEpochSeconds.longValue = nowMillis / 1_000L
            delay(1_000L - nowMillis % 1_000L)
        }
    }

    BoxWithConstraints(modifier) {
        val edgePadding = 8.dp
        val maxBadgeWidth = (maxWidth - edgePadding * 2).coerceAtLeast(0.dp)
        LazyRow(
            modifier = Modifier.fillMaxWidth().then(countdownGate.modifier),
            contentPadding = PaddingValues(horizontal = edgePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { index, item ->
                if (item.superChatId > 0L) item.superChatId else "unidentified:$index"
            }) { _, item ->
                val duration = resolveLiveSuperChatDurationSec(item.superChatDuration).toFloat()
                LiveSuperChatBadge(
                    item = item,
                    opacity = opacity,
                    scale = scale,
                    remainingFraction = {
                        if (showProgress) {
                            (remainingSeconds(item, nowEpochSeconds.longValue) / duration).coerceIn(0f, 1f)
                        } else 1f
                    },
                    onClick = { selected = item },
                    modifier = Modifier.widthIn(max = maxBadgeWidth),
                )
            }
        }
    }

    val item = selected?.let { selection ->
        items.firstOrNull { candidate ->
            if (selection.superChatId > 0L) candidate.superChatId == selection.superChatId
            else candidate == selection
        }
    } ?: return
    val dismiss = { selected = null }
    AppAlertDialog(
        onDismissRequest = dismiss,
        title = { AppText("醒目留言") },
        text = {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                LiveSuperChatCard(
                    item = item,
                    persistent = persistent,
                    onExpired = dismiss,
                    onUserClick = {
                        dismiss()
                        onUserClick(item.uid)
                    },
                    onReport = {
                        dismiss()
                        onReport(item)
                    },
                    onCountdownPauseChange = { paused -> onCountdownPauseChange(item, paused) },
                )
            }
        },
        confirmButton = { AppDialogAction(onClick = dismiss) { AppText("关闭") } },
        contentLayout = resolveAppContentDialogLayoutPolicy(maxWidthDp = maxCardWidthDp, minWidthDp = 260),
    )
}

@Composable
private fun LiveSuperChatBadge(
    item: LiveDanmakuItem,
    opacity: Float,
    scale: Float,
    remainingFraction: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = resolveLiveSuperChatColor(item.superChatBackgroundColor)
    val amountColor = if (item.superChatMessageColor != 0) Color(item.superChatMessageColor) else Color.White
    Box(
        modifier = modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .semantics { contentDescription = item.uname }
            .clickable(role = Role.Button, onClickLabel = "查看醒目留言", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .alpha(opacity)
                .clip(RoundedCornerShape(12.dp))
                .drawBehind {
                    val remainingWidth = size.width * remainingFraction()
                    drawRect(background, size = Size(remainingWidth, size.height))
                    if (remainingWidth < size.width) {
                        drawRect(
                            color = background.copy(alpha = background.alpha * 0.55f),
                            topLeft = Offset(remainingWidth, 0f),
                            size = Size(size.width - remainingWidth, size.height),
                        )
                    }
                }
                .padding(horizontal = 8.dp * scale, vertical = 5.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveViewerAvatar(item.superChatFace, Modifier.size(26.dp * scale))
            AppText(
                text = item.superChatPrice,
                color = amountColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp * scale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                tapToCopyEnabled = false,
            )
        }
    }
}
