package com.android.purebilibili.feature.dynamic.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import kotlinx.coroutines.delay
import kotlin.math.abs

internal fun resolveImagePreviewThumbnailScrubIndex(
    pointerX: Float,
    itemHorizontalBounds: List<Pair<Float, Float>>,
    containerWidth: Float,
    imageCount: Int,
): Int {
    if (imageCount <= 1) return 0
    if (itemHorizontalBounds.size == imageCount) {
        itemHorizontalBounds.forEachIndexed { index, (start, end) ->
            if (pointerX in start..end) return index
        }
        var nearestIndex = 0
        var nearestDistance = Float.MAX_VALUE
        itemHorizontalBounds.forEachIndexed { index, (start, end) ->
            val center = (start + end) * 0.5f
            val distance = abs(pointerX - center)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        return nearestIndex
    }
    if (containerWidth <= 0f) return 0
    return ((pointerX / containerWidth) * imageCount).toInt().coerceIn(0, imageCount - 1)
}

/**
 * 多图预览底部缩略图拖拽条：手指按住并左右滑动可连续切换图片，
 * 拖拽期间在当前选中缩略图上方展示悬浮页码气泡，松手后自动淡出。
 */
@Composable
internal fun ImagePreviewThumbnailScrubber(
    images: List<String>,
    currentIndex: Int,
    imageLoader: ImageLoader,
    onSelectIndex: (Int) -> Unit,
    onScrubbingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (images.size <= 1) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = rememberHapticFeedback()
    val scrollState = rememberScrollState()
    val latestCurrentIndex by rememberUpdatedState(currentIndex)
    val latestOnSelectIndex by rememberUpdatedState(onSelectIndex)
    val latestOnScrubbingChange by rememberUpdatedState(onScrubbingChange)
    val itemStarts = remember(images.size) { FloatArray(images.size) { Float.NaN } }
    val itemEnds = remember(images.size) { FloatArray(images.size) { Float.NaN } }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var stripLeftInContainerPx by remember { mutableFloatStateOf(0f) }
    var bubbleWidthPx by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var bubbleVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrubbing, currentIndex) {
        if (isScrubbing) {
            bubbleVisible = true
        } else if (bubbleVisible) {
            delay(520L)
            bubbleVisible = false
        }
    }

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (bubbleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (bubbleVisible) 120 else 220),
        label = "thumbnailScrubBubbleAlpha",
    )

    LaunchedEffect(currentIndex, images.size) {
        val start = itemStarts.getOrNull(currentIndex) ?: Float.NaN
        val end = itemEnds.getOrNull(currentIndex) ?: Float.NaN
        if (!start.isNaN() && !end.isNaN() && scrollState.maxValue > 0) {
            val itemCenter = (start + end) * 0.5f
            val viewportHalf = with(density) { 160.dp.toPx() }
            val targetScroll = (itemCenter - viewportHalf).toInt().coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(targetScroll)
        }
    }

    val headers = remember {
        NetworkHeaders.Builder()
            .set("Referer", "https://www.bilibili.com/")
            .build()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 拖拽期间跟随当前缩略图上方的悬浮页码气泡（在 graphicsLayer 中定位，避免每帧重组）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (bubbleAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { bubbleWidthPx = it.size.width.toFloat() }
                        .graphicsLayer {
                            alpha = bubbleAlpha
                            val start = itemStarts.getOrNull(currentIndex) ?: Float.NaN
                            val end = itemEnds.getOrNull(currentIndex) ?: Float.NaN
                            val centerInStrip = if (!start.isNaN() && !end.isNaN()) {
                                (start + end) * 0.5f - scrollState.value
                            } else {
                                rowWidthPx * 0.5f
                            }
                            val rawX = stripLeftInContainerPx + centerInStrip - bubbleWidthPx * 0.5f
                            val maxX = (rowWidthPx - bubbleWidthPx).coerceAtLeast(0f)
                            translationX = rawX.coerceIn(0f, maxX)
                            val scale = 0.92f + 0.08f * bubbleAlpha
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(MediaContrastPalette.Scrim.copy(alpha = 0.78f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = "${currentIndex + 1} / ${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowWidthPx = it.size.width.toFloat() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .onGloballyPositioned { stripLeftInContainerPx = it.positionInParent().x }
                    .horizontalScroll(scrollState)
                    .pointerInput(images.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isScrubbing = true
                            latestOnScrubbingChange(true)
                            try {
                                fun resolveIndex(localX: Float): Int {
                                    val contentX = localX + scrollState.value
                                    val bounds = if (itemStarts.all { !it.isNaN() } && itemEnds.all { !it.isNaN() }) {
                                        List(images.size) { index -> itemStarts[index] to itemEnds[index] }
                                    } else {
                                        emptyList()
                                    }
                                    return resolveImagePreviewThumbnailScrubIndex(
                                        pointerX = contentX,
                                        itemHorizontalBounds = bounds,
                                        containerWidth = rowWidthPx,
                                        imageCount = images.size,
                                    )
                                }

                                var lastSelected = resolveIndex(down.position.x)
                                if (lastSelected != latestCurrentIndex) {
                                    haptic(HapticType.LIGHT)
                                    latestOnSelectIndex(lastSelected)
                                }
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val nextIndex = resolveIndex(change.position.x)
                                    if (nextIndex != lastSelected) {
                                        lastSelected = nextIndex
                                        haptic(HapticType.LIGHT)
                                        latestOnSelectIndex(nextIndex)
                                    }
                                    change.consume()
                                }
                            } finally {
                                isScrubbing = false
                                latestOnScrubbingChange(false)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    images.forEachIndexed { index, rawUrl ->
                        val isSelected = index == currentIndex
                        val thumbWidth by animateDpAsState(
                            targetValue = if (isSelected) 40.dp else 28.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "thumbWidth",
                        )
                        val thumbHeight by animateDpAsState(
                            targetValue = if (isSelected) 44.dp else 38.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "thumbHeight",
                        )
                        val extraHorizontalPadding by animateDpAsState(
                            targetValue = if (isSelected) 4.dp else 0.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "thumbGap",
                        )
                        val thumbAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.72f,
                            label = "thumbAlpha",
                        )
                        val shape = RoundedCornerShape(if (isSelected) 6.dp else 4.dp)
                        val normalizedUrl = remember(rawUrl) { normalizeImageUrl(rawUrl) }
                        val request = remember(normalizedUrl) {
                            ImageRequest.Builder(context)
                                .data(normalizedUrl)
                                .httpHeaders(headers)
                                .size(160, 160)
                                .crossfade(false)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = extraHorizontalPadding)
                                .width(thumbWidth)
                                .height(thumbHeight)
                                .onGloballyPositioned { coords ->
                                    val start = coords.positionInParent().x
                                    itemStarts[index] = start
                                    itemEnds[index] = start + coords.size.width
                                }
                                .graphicsLayer { alpha = thumbAlpha }
                                .clip(shape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.5.dp, Color.White, shape)
                                    } else {
                                        Modifier
                                    }
                                ),
                        ) {
                            AsyncImage(
                                model = request,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(thumbWidth)
                                    .height(thumbHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}
