package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import androidx.media3.common.Player
import com.android.purebilibili.core.ui.components.AnimatedCountText
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.video.danmaku.selectFittingHotDanmaku
import com.android.purebilibili.feature.video.danmaku.selectHotDanmaku
import com.android.purebilibili.feature.video.ui.components.DanmakuSameSendConfirmation
import com.android.purebilibili.danmaku.engine.DanmakuItem

private class HotDanmakuPresentation {
    var heightPx = 0
    private var reportedItems = emptyList<DanmakuItem>()
    private var reportedHeightPx = 0

    fun report(items: List<DanmakuItem>, onChange: (List<DanmakuItem>, Int) -> Unit) {
        val shownItems = if (heightPx > 0) items else emptyList()
        val shownHeight = if (shownItems.isEmpty()) 0 else heightPx
        if (reportedItems == shownItems && reportedHeightPx == shownHeight) return
        reportedItems = shownItems
        reportedHeightPx = shownHeight
        onChange(shownItems, shownHeight)
    }
}

/** Poll fresh snapshots, including replacements with the same size and filter changes. */
@Composable
fun HotDanmakuBar(
    getDanmakuList: () -> List<DanmakuItem>,
    player: Player?,
    likedDanmakuIds: Set<Long>,
    onLikeDanmaku: (Long, Boolean) -> Unit,
    onSendSame: (String) -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    onPresentationChange: (List<DanmakuItem>, Int) -> Unit,
) {
    val latestPresentationChange by rememberUpdatedState(onPresentationChange)
    val presentation = remember { HotDanmakuPresentation() }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val countStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f,
        lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 0.9f,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        shadow = Shadow(Color.Black, Offset(1f, 1f), 3f),
    )
    val countWidths = remember(countStyle, density.density, density.fontScale) { mutableMapOf<Int, Dp>() }
    val countWidthFor: (Long) -> Dp = { count ->
        val digits = count.coerceAtLeast(0L).toString().length
        countWidths.getOrPut(digits) {
            // Cover every decimal length in the animation, including proportional digits.
            val widestTextPx = (1..digits).maxOf { length ->
                ('0'..'9').maxOf { digit ->
                    textMeasurer.measure(AnnotatedString("×" + digit.toString().repeat(length)), style = countStyle).size.width
                }
            }
            // The graphics layer scales both text and its 1dp blur padding.
            with(density) { ceil((widestTextPx + 2 * 1.dp.roundToPx()) * 1.15f).toInt().toDp() }
        }
    }
    val latestGetList by rememberUpdatedState(getDanmakuList)
    var hotItems by remember(player) { mutableStateOf(emptyList<DanmakuItem>()) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    val refresh = {
        hotItems = player?.let { selectHotDanmaku(latestGetList(), it.currentPosition, maxItems = Int.MAX_VALUE) }.orEmpty()
    }
    val latestRefresh by rememberUpdatedState(refresh)
    val samplingActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    // A new loaded window must refresh even when playback is paused.
    val danmakuSnapshot = getDanmakuList()
    LaunchedEffect(player, samplingActive, danmakuSnapshot) {
        if (player == null || !samplingActive) return@LaunchedEffect
        // Playing refreshes the moving window; paused playback waits for seek/events.
        observeVideoPlayerProgress(player, videoDuration = 0L, pollingIntervalMs = 1_000L) {
            latestRefresh()
        }
    }
    if (hotItems.isEmpty()) SideEffect {
        presentation.heightPx = 0
        presentation.report(emptyList(), latestPresentationChange)
    }
    DisposableEffect(Unit) {
        onDispose {
            presentation.heightPx = 0
            latestPresentationChange(emptyList(), 0)
        }
    }
    if (hotItems.isNotEmpty()) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(Color.Black, Offset(1f, 1f), 3f),
            )
            val visibleItems = remember(hotItems, likedDanmakuIds, maxWidth, textStyle, countStyle, density.density, density.fontScale) {
                selectFittingHotDanmaku(
                    items = hotItems,
                    availableWidthPx = with(density) { maxWidth.toPx() - 2 * 1.dp.roundToPx() },
                    minimumGapPx = with(density) { 4.dp.roundToPx().toFloat() },
                ) { item ->
                    val liked = item.danmakuId in likedDanmakuIds
                    val count = item.likeCount + if (liked && item.likeCount < Long.MAX_VALUE) 1L else 0L
                    val textWidth = textMeasurer.measure(AnnotatedString(item.text.orEmpty()), style = textStyle).size.width
                    // Measured text/count slots, two 20dp buttons, a 2dp gap and 1dp item padding.
                    textWidth + with(density) {
                        (2 * 20.dp.roundToPx() + 2.dp.roundToPx() + 2 * 1.dp.roundToPx() + countWidthFor(count).roundToPx()).toFloat()
                    }
                }
            }
            val latestVisibleItems by rememberUpdatedState(visibleItems)
            SideEffect {
                if (visibleItems.isEmpty()) presentation.heightPx = 0
                presentation.report(visibleItems, latestPresentationChange)
            }
            if (visibleItems.isNotEmpty()) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        presentation.heightPx = it.height
                        presentation.report(latestVisibleItems, latestPresentationChange)
                    }
                    .padding(1.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleItems.forEach { item ->
                    key(item.danmakuId) {
                        val liked = item.danmakuId in likedDanmakuIds
                        Row(
                            modifier = Modifier
                                .padding(1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = item.text.orEmpty(),
                                style = textStyle,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                                tapToCopyEnabled = false,
                            )
                            val count = item.likeCount + if (liked && item.likeCount < Long.MAX_VALUE) 1L else 0L
                            val countWidth = countWidthFor(count)
                            // Reserve the measured maximum animated text width, not a fixed slot.
                            Box(
                                modifier = Modifier.width(countWidth),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                AnimatedCountText(
                                    count = count,
                                    prefix = "×",
                                    animateFromZero = true,
                                    blurOnChange = true,
                                    blurPadding = 1.dp,
                                    style = countStyle,
                                    color = Color.White,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(role = Role.Button) { onLikeDanmaku(item.danmakuId, !liked) }
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(
                                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (liked) "取消点赞" else "点赞弹幕",
                                    tint = if (liked) AppContentColors.accent() else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(enabled = !isSending, role = Role.Button) { pendingText = item.text }
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(Icons.Filled.Send, contentDescription = "发一条同款弹幕",
                                    tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    pendingText?.let { text ->
        DanmakuSameSendConfirmation(
            text = text,
            isSending = isSending,
            onDismiss = { pendingText = null },
            onConfirm = { pendingText = null; onSendSame(text) },
        )
    }
}
