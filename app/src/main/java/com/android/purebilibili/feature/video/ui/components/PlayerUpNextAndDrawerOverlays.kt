package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.RelatedVideo
import kotlinx.coroutines.launch
import kotlin.math.ceil

internal const val END_SCREEN_COUNTDOWN_WINDOW_MS = 8_000L
internal const val END_SCREEN_MIN_VIDEO_DURATION_MS = 30_000L

internal fun resolveEndScreenNextVideo(
    currentBvid: String,
    relatedVideos: List<RelatedVideo>,
    queuedNext: com.android.purebilibili.feature.video.player.PlaylistItem? = null,
): RelatedVideo? {
    if (queuedNext != null && queuedNext.bvid.isNotBlank() && queuedNext.bvid != currentBvid) {
        return RelatedVideo(
            bvid = queuedNext.bvid,
            cid = queuedNext.cid,
            title = queuedNext.title,
            pic = queuedNext.cover,
            duration = queuedNext.duration.toInt(),
        )
    }
    return relatedVideos.firstOrNull { it.bvid.isNotBlank() && it.bvid != currentBvid }
}

internal fun resolveEndScreenCountdownSeconds(
    positionMs: Long,
    durationMs: Long,
    windowMs: Long = END_SCREEN_COUNTDOWN_WINDOW_MS,
): Int? {
    if (durationMs < END_SCREEN_MIN_VIDEO_DURATION_MS) return null
    val remainingMs = durationMs - positionMs
    if (remainingMs <= 0L || remainingMs > windowMs) return null
    return ceil(remainingMs / 1000.0).toInt().coerceAtLeast(1)
}

internal fun shouldShowEndScreenNextCard(
    enabled: Boolean,
    isInPipMode: Boolean,
    isScreenLocked: Boolean,
    dismissed: Boolean,
    nextVideo: RelatedVideo?,
    countdownSeconds: Int?,
    allowAutoNext: Boolean = true,
    hasNextPage: Boolean = false,
): Boolean {
    return enabled &&
        allowAutoNext &&
        !hasNextPage &&
        !isInPipMode &&
        !isScreenLocked &&
        !dismissed &&
        nextVideo != null &&
        countdownSeconds != null
}

internal enum class FullscreenDrawerTab {
    RELATED,
    QUEUE,
}

internal fun resolveFullscreenDrawerInitialTab(
    hasRelatedVideos: Boolean,
    hasQueuedItems: Boolean,
): FullscreenDrawerTab {
    return if (!hasRelatedVideos && hasQueuedItems) {
        FullscreenDrawerTab.QUEUE
    } else {
        FullscreenDrawerTab.RELATED
    }
}

/**
 * 横屏控制栏显示时的底部轻量上滑引导：
 * 提示用户可从底边上滑（或直接点按）呼出「更多推荐 / 待播队列」抽屉。
 */
@Composable
internal fun FullscreenSwipeUpDrawerHint(
    visible: Boolean,
    hasQueuedItems: Boolean,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit = onClick,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(onClick = onClick)
                .pointerInput(onSwipeUp) {
                    var dragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragY = 0f },
                        onVerticalDrag = { change, amount ->
                            dragY += amount
                            if (dragY < -14.dp.toPx()) {
                                change.consume()
                                onSwipeUp()
                            }
                        },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.65f)),
            )
            AppText(
                text = if (hasQueuedItems) "上滑待播队列" else "上滑更多推荐",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun FullscreenSwipeUpRelatedDrawer(
    visible: Boolean,
    relatedVideos: List<RelatedVideo>,
    onVideoClick: (RelatedVideo) -> Unit,
    onDismiss: () -> Unit,
    queuedItems: List<com.android.purebilibili.feature.video.player.PlaylistItem> = emptyList(),
    currentBvid: String = "",
    onQueueItemClick: (com.android.purebilibili.feature.video.player.PlaylistItem) -> Unit = {},
    onRemoveQueueItem: (Int) -> Unit = {},
    onClearUpcomingQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val hasContent = relatedVideos.isNotEmpty() || queuedItems.isNotEmpty()
    val drawerVisible = visible && hasContent
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(drawerVisible) {
        if (drawerVisible) {
            dragOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
            )
        }
    }
    var selectedTab by remember(visible, relatedVideos.isNotEmpty(), queuedItems.isNotEmpty()) {
        mutableStateOf(
            resolveFullscreenDrawerInitialTab(
                hasRelatedVideos = relatedVideos.isNotEmpty(),
                hasQueuedItems = queuedItems.isNotEmpty(),
            )
        )
    }
    AnimatedVisibility(
        visible = drawerVisible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = modifier.fillMaxSize(),
    ) {
        val slideProgress by transition.animateFloat(
            transitionSpec = {
                spring(dampingRatio = 0.86f, stiffness = 520f)
            },
            label = "fullscreenDrawerSlide",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val dragFraction = if (panelHeightPx > 0) {
                            (dragOffset.value / panelHeightPx).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        alpha = slideProgress.coerceIn(0f, 1f) * (1f - dragFraction)
                    }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.72f),
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
            val containerColor = if (liquidGlassEnabled) {
                Color(0xFF141820).copy(alpha = 0.72f)
            } else {
                Color(0xFF121214).copy(alpha = 0.90f)
            }
            val borderColor = if (liquidGlassEnabled) {
                Color.White.copy(alpha = 0.22f)
            } else {
                Color.White.copy(alpha = 0.10f)
            }
            val accentColor = AppContentColors.selectionAccent(liquidGlassEnabled)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (1f - slideProgress) * size.height + dragOffset.value
                    }
                    .onSizeChanged { panelHeightPx = it.height }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(containerColor)
                    .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                    .pointerInput(drawerVisible) {
                        detectVerticalDragGestures(
                            onDragStart = {},
                            onVerticalDrag = { change, amount ->
                                if (amount > 0f || dragOffset.value > 0f) {
                                    change.consume()
                                    scope.launch {
                                        dragOffset.snapTo(
                                            (dragOffset.value + amount).coerceAtLeast(0f)
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                if (panelHeightPx > 0 && dragOffset.value > panelHeightPx * 0.28f) {
                                    onDismiss()
                                } else {
                                    scope.launch {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
                                    )
                                }
                            },
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(top = 10.dp, bottom = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.38f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (relatedVideos.isNotEmpty()) {
                            val isSelected = selectedTab == FullscreenDrawerTab.RELATED
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.24f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { selectedTab = FullscreenDrawerTab.RELATED }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                AppText(
                                    text = "更多推荐",
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            }
                        }
                        if (queuedItems.isNotEmpty()) {
                            val isSelected = selectedTab == FullscreenDrawerTab.QUEUE
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.24f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { selectedTab = FullscreenDrawerTab.QUEUE }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                AppText(
                                    text = "待播队列 (${queuedItems.size})",
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                    if (selectedTab == FullscreenDrawerTab.QUEUE && queuedItems.size > 1) {
                        AppText(
                            text = "清空待播",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onClearUpcomingQueue)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    } else {
                        AppText(
                            text = "下滑收起",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (selectedTab == FullscreenDrawerTab.QUEUE && queuedItems.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            items = queuedItems,
                            key = { index, item -> "${item.bvid}_$index" },
                        ) { index, item ->
                            val isCurrent = item.bvid == currentBvid
                            Column(
                                modifier = Modifier
                                    .width(168.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onQueueItemClick(item) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .then(
                                            if (isCurrent) {
                                                Modifier.border(1.5.dp, accentColor, RoundedCornerShape(10.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    AsyncImage(
                                        model = FormatUtils.fixImageUrl(item.cover),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(5.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(accentColor)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            AppText(
                                                text = "播放中",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(5.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(Color.Black.copy(alpha = 0.68f))
                                                .clickable { onRemoveQueueItem(index) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            AppText(
                                                text = "×",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    if (item.duration > 0L) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(5.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = 0.72f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            AppText(
                                                text = FormatUtils.formatDuration(item.duration.toInt()),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                AppText(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.owner.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AppText(
                                        text = item.owner,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = relatedVideos.take(15),
                            key = { it.bvid.ifBlank { it.aid.toString() } },
                        ) { video ->
                            Column(
                                modifier = Modifier
                                    .width(168.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onVideoClick(video) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                ) {
                                    AsyncImage(
                                        model = FormatUtils.fixImageUrl(video.pic),
                                        contentDescription = video.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (video.duration > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(5.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = 0.72f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            AppText(
                                                text = FormatUtils.formatDuration(video.duration),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                AppText(
                                    text = video.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (video.owner.name.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AppText(
                                        text = video.owner.name,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EndScreenUpNextCountdownCard(
    visible: Boolean,
    nextVideo: RelatedVideo?,
    countdownSeconds: Int?,
    fromQueue: Boolean = false,
    onPlayNow: (RelatedVideo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    AnimatedVisibility(
        visible = visible && nextVideo != null && countdownSeconds != null,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut(animationSpec = tween(160)) + slideOutVertically(targetOffsetY = { it / 3 }),
        modifier = modifier,
    ) {
        if (nextVideo != null && countdownSeconds != null) {
            val surfaceColor = if (liquidGlassEnabled) {
                Color(0xFF151A24).copy(alpha = 0.78f)
            } else {
                Color(0xFF141416).copy(alpha = 0.92f)
            }
            val borderColor = if (liquidGlassEnabled) {
                Color.White.copy(alpha = 0.24f)
            } else {
                Color.White.copy(alpha = 0.12f)
            }
            val accentColor = AppContentColors.selectionAccent(liquidGlassEnabled)

            AppSurface(
                color = surfaceColor,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .width(276.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(88.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            AsyncImage(
                                model = FormatUtils.fixImageUrl(nextVideo.pic),
                                contentDescription = nextVideo.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                text = "${if (fromQueue) "队列下一集" else "即将播放"} · ${countdownSeconds}s",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AppText(
                                text = nextVideo.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                text = "取消",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor)
                                .clickable { onPlayNow(nextVideo) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                text = "立即播放",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
