package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.android.purebilibili.core.ui.ClientDanmakuActionIcon
import com.android.purebilibili.core.ui.ClientDanmakuGlyph
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.android.purebilibili.core.ui.AppPopupSurface
import com.android.purebilibili.core.ui.AppPopupSurfaceType
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.feature.video.danmaku.DanmakuTapAnchor
import kotlin.math.roundToInt

/** Bounds include only the frozen item's player, never the details area below it. */
internal fun resolveDanmakuToolbarPosition(
    anchor: DanmakuTapAnchor,
    window: IntSize,
    content: IntSize,
    gap: Int,
): IntOffset {
    val minX = anchor.viewportLeft.roundToInt().coerceAtLeast(0) + gap
    val minY = anchor.viewportTop.roundToInt().coerceAtLeast(0) + gap
    val maxX = (anchor.viewportRight.roundToInt().coerceAtMost(window.width) - gap - content.width).coerceAtLeast(minX)
    val maxY = (anchor.viewportBottom.roundToInt().coerceAtMost(window.height) - gap - content.height).coerceAtLeast(minY)
    val below = anchor.bottom.roundToInt() + gap
    val y = if (below <= maxY) below else anchor.top.roundToInt() - content.height - gap
    return IntOffset((anchor.tapX.roundToInt() - content.width / 2).coerceIn(minX, maxX), y.coerceIn(minY, maxY))
}

@Composable
internal fun DanmakuActionToolbar(
    anchor: DanmakuTapAnchor,
    hasLiked: Boolean,
    voteCount: Int,
    canVote: Boolean,
    voteLoading: Boolean,
    canRecall: Boolean,
    hasTimestamp: Boolean,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onCopy: () -> Unit,
    onReportOrRecall: () -> Unit,
    onTimestamp: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!rememberPlayerPopupHostActive(onDismiss)) return
    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val windowSize = LocalWindowInfo.current.containerSize
    val bubbleShape = remember(anchor, windowSize, gap) { DanmakuToolbarBubbleShape(anchor, windowSize, gap) }
    val maxWidth = with(density) { (anchor.viewportRight - anchor.viewportLeft).toDp() - 12.dp }.coerceAtLeast(40.dp)
    val position = remember(anchor, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                resolveDanmakuToolbarPosition(anchor, windowSize, popupContentSize, gap)
        }
    }
    com.android.purebilibili.core.ui.LocalNavigationBackHandler(enabled = true, onBackCompleted = onDismiss)
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        // Keep the player window focused so selecting an item does not suspend playback.
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        // The shared popup surface selects glass explicitly and retains MD3/Miuix rendering otherwise.
        AppPopupSurface(
            type = AppPopupSurfaceType.DIALOG,
            shape = bubbleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = modifier.widthIn(max = maxWidth),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 6.dp)) {
                val countLabel = if (voteCount > 999) "999+" else voteCount.toString()
                Box(Modifier.size(40.dp)) {
                    ToolbarAction(
                        icon = { tint -> ClientDanmakuActionIcon(if (hasLiked) ClientDanmakuGlyph.LIKED else ClientDanmakuGlyph.LIKE, tint) },
                        description = "${if (hasLiked) "取消点赞" else "点赞"}，$countLabel 赞",
                        enabled = canVote && !voteLoading,
                        selected = hasLiked,
                        onClick = onLike,
                    )
                    if (voteCount > 0) AppText(
                        text = countLabel,
                        fontSize = 9.sp,
                        maxLines = 1,
                        tapToCopyEnabled = false,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp),
                    )
                }
                ToolbarAction({ tint -> ClientDanmakuActionIcon(ClientDanmakuGlyph.COPY, tint) }, "复制弹幕", onClick = onCopy)
                ToolbarAction(
                    { tint -> ClientDanmakuActionIcon(if (canRecall) ClientDanmakuGlyph.RECALL else ClientDanmakuGlyph.REPORT, tint) },
                    if (canRecall) "撤回弹幕" else "举报弹幕",
                    onClick = onReportOrRecall,
                )
                if (hasTimestamp) ToolbarAction({ tint -> AppIcon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp), tint = tint) }, "跳转到弹幕时间", onClick = onTimestamp)
                ToolbarAction({ tint -> AppIcon(Icons.Filled.MoreHoriz, null, Modifier.size(20.dp), tint = tint) }, "更多弹幕操作", onClick = onMore)
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: @Composable (Color) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    AppIconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(40.dp).semantics { contentDescription = description }) {
        icon(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
