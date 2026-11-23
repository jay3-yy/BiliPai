package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import androidx.media3.common.Player
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.AnimatedCountText
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.withTabularNumbers
import com.android.purebilibili.feature.video.danmaku.HotDanmakuIndex
import com.android.purebilibili.feature.video.danmaku.selectFittingHotDanmaku
import com.android.purebilibili.danmaku.engine.DanmakuItem

private val hotDanmakuLikeSize = 20.dp
private val hotDanmakuSendWidth = 36.dp
private val hotDanmakuSendHeight = 24.dp
private val hotDanmakuActionGap = 4.dp
private val hotDanmakuItemPadding = 1.dp
private val hotDanmakuSlotMinHeight = 30.dp

private fun countWithOneMore(count: Long): Long = count.coerceAtMost(Long.MAX_VALUE - 1L) + 1L

/**
 * Minimum one-line height for the bar itself. The engine slot uses the same
 * value so the reserved band and the rendered content cannot drift apart.
 */
@Composable
internal fun hotDanmakuBarMinHeightPx(): Float {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val titleMediumLineHeight = typography.titleMedium.lineHeight
    val titleLargeLineHeight = typography.titleLarge.lineHeight
    return remember(density, titleMediumLineHeight, titleLargeLineHeight) {
        with(density) {
            maxOf(
                titleMediumLineHeight.toPx(),
                (titleLargeLineHeight * 0.9f).toPx(),
                hotDanmakuSendHeight.toPx(),
            ) + 6.dp.toPx()
        }
    }
}

@Composable
internal fun HotDanmakuBar(
    getDanmakuIndex: () -> HotDanmakuIndex?,
    player: Player?,
    likedDanmakuIds: Set<Long>,
    onLikeDanmaku: (Long, Boolean) -> Unit,
    onSendSame: ((String) -> Unit)?,
    isSending: Boolean,
    opacity: Float = 1f,
    slotHeight: Dp = hotDanmakuSlotMinHeight,
    modifier: Modifier = Modifier,
    onPresentationChange: (List<DanmakuItem>) -> Unit,
) {
    val latestPresentationChange by rememberUpdatedState(onPresentationChange)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val countStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f,
        lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 0.9f,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        shadow = Shadow(Color.Black, Offset(1f, 1f), 3f),
    ).withTabularNumbers()
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
    val samplingActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val index = getDanmakuIndex()?.takeUnless { it.isEmpty }
    var sampledItems by remember(player) { mutableStateOf(emptyList<DanmakuItem>()) }
    val hotItems = if (index == null) emptyList() else sampledItems
    // 分段和过滤结果更新时立即查询，播放中随进度查询，暂停时等待播放器事件。
    LaunchedEffect(player, samplingActive, index) {
        if (player == null || !samplingActive || index == null) {
            sampledItems = emptyList()
            return@LaunchedEffect
        }
        observeVideoPlayerProgress(player, videoDuration = 0L, pollingIntervalMs = 1_000L) {
            sampledItems = index.select(player.currentPosition)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            latestPresentationChange(emptyList())
        }
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().heightIn(min = slotHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(Color.Black, Offset(1f, 1f), 3f),
            )
            val visibleItems = remember(hotItems, maxWidth, textStyle, countStyle, density.density, density.fontScale) {
                selectFittingHotDanmaku(
                    items = hotItems,
                    availableWidthPx = with(density) { maxWidth.toPx() - 2 * 1.dp.roundToPx() },
                    minimumGapPx = with(density) { 4.dp.roundToPx().toFloat() },
                ) { item ->
                    val textWidth = textMeasurer.measure(AnnotatedString(item.text.orEmpty()), style = textStyle).size.width
                    val countWidth = countWidthFor(countWithOneMore(item.likeCount))
                    textWidth + with(density) {
                        (hotDanmakuLikeSize.roundToPx() + hotDanmakuSendWidth.roundToPx() +
                            hotDanmakuActionGap.roundToPx() + 2 * hotDanmakuItemPadding.roundToPx() +
                            countWidth.roundToPx()).toFloat()
                    }
                }
            }
            LaunchedEffect(visibleItems) {
                latestPresentationChange(visibleItems)
            }
            if (visibleItems.isNotEmpty()) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = opacity.coerceIn(0f, 1f) }
                    .padding(1.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleItems.forEach { item ->
                    key(item.danmakuId) {
                        val liked = item.danmakuId in likedDanmakuIds
                        Row(
                            modifier = Modifier
                                .padding(hotDanmakuItemPadding),
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
                            val likedCount = countWithOneMore(item.likeCount)
                            val count = if (liked) likedCount else item.likeCount
                            Box(
                                // Keep room for the next digit even before the user taps like.
                                modifier = Modifier.width(countWidthFor(likedCount)),
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
                                    .size(hotDanmakuLikeSize)
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
                            Spacer(Modifier.width(hotDanmakuActionGap))
                            Box(
                                modifier = Modifier
                                    .size(hotDanmakuSendWidth, hotDanmakuSendHeight)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .border(1.dp, Color.White.copy(alpha = 0.36f), CircleShape)
                                    .clickable(enabled = onSendSame != null && !isSending, role = Role.Button) {
                                        item.text?.let { onSendSame?.invoke(it) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(
                                    painter = painterResource(R.drawable.ic_hot_danmaku_send_plus_one),
                                    contentDescription = stringResource(R.string.hot_danmaku_send_plus_one),
                                    tint = Color.White.copy(alpha = if (isSending) 0.45f else 1f),
                                    modifier = Modifier.size(width = 24.dp, height = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
