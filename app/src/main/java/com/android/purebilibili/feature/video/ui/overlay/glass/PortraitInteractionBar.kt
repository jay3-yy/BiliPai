package com.android.purebilibili.feature.video.ui.overlay.glass

import com.android.purebilibili.feature.video.ui.overlay.*

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.Comment
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.android.purebilibili.core.ui.rememberAppCoinIcon
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.ui.components.appActionWithFeedback

import com.android.purebilibili.feature.video.ui.feedback.VideoActionCountRequests
import com.android.purebilibili.feature.video.ui.feedback.VideoActionCountText
import com.android.purebilibili.feature.video.ui.feedback.rememberVideoTriplePressState

internal fun shouldStartPortraitTriplePress(longPressConfirmed: Boolean): Boolean {
    return longPressConfirmed
}

internal fun shouldCancelPortraitTriplePressOnRelease(
    isTriplePressing: Boolean,
    tripleCompleted: Boolean
): Boolean {
    return isTriplePressing && !tripleCompleted
}

/**
 * 竖屏播放器右侧互动栏 (Refined Style)
 *
 * 操作：点赞（长按三连）、投币、评论、收藏、分享
 */
@Composable
fun PortraitInteractionBar(
    isLiked: Boolean,
    likeStatusKnown: Boolean,
    likeCount: Int,
    isCoined: Boolean = false,
    coinCount: Int = 0,
    isFavorited: Boolean,
    favoriteCount: Int,
    commentCount: Int,
    shareCount: Int,
    onLikeClick: () -> Unit,
    onLikeLongClick: () -> Unit = {},
    onCoinClick: () -> Unit = {},
    onFavoriteClick: () -> Unit,
    onFavoriteLongClick: () -> Unit = {},
    onCommentClick: (() -> Unit)?,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolvePortraitInteractionBarLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    val coinIcon = rememberAppCoinIcon()
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
    val countRequests = remember { VideoActionCountRequests() }
    val triplePress = rememberVideoTriplePressState(Unit, likeStatusKnown) {
        countRequests.requestTriple()
        haptic(HapticType.MEDIUM)
        onLikeLongClick()
    }
    val tripleProgress = triplePress.progress
    LaunchedEffect(triplePress.isPressing) {
        if (triplePress.isPressing) haptic(HapticType.LIGHT)
    }

    Column(
        modifier = modifier
            .padding(
                end = layoutPolicy.endPaddingDp.dp,
                bottom = layoutPolicy.bottomPaddingDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(layoutPolicy.itemSpacingDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // 点赞
        InteractionButton(
            icon = if (likeStatusKnown && isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            countText = if (!likeStatusKnown) "待确认" else if (likeCount > 0) FormatUtils.formatStat(likeCount.toLong()) else "点赞",
            isActive = likeStatusKnown && isLiked,
            contentDescription = if (likeStatusKnown) "点赞" else "重新确认状态并点赞",
            activeColor = AppContentColors.accent(),
            layoutPolicy = layoutPolicy,
            progress = tripleProgress,
            countAnimationRequest = countRequests.like,
            countValue = likeCount,
            onClick = {
                haptic(HapticType.LIGHT)
                countRequests.requestLike()
                onLikeClick()
            },
            onLongClick = {
                if (likeStatusKnown) triplePress.start()
            },
            onPressRelease = {
                if (shouldCancelPortraitTriplePressOnRelease(
                        isTriplePressing = triplePress.isPressing,
                        tripleCompleted = triplePress.completed,
                    )) triplePress.release()
            },
        )

        // 投币
        InteractionButton(
            icon = coinIcon,
            countText = when {
                isCoined && coinCount > 0 -> FormatUtils.formatStat(coinCount.toLong())
                isCoined -> "已投"
                coinCount > 0 -> FormatUtils.formatStat(coinCount.toLong())
                else -> "投币"
            },
            isActive = isCoined,
            activeColor = AppContentColors.accent(),
            layoutPolicy = layoutPolicy,
            countAnimationRequest = countRequests.coin,
            countValue = coinCount,
            onClick = {
                haptic(HapticType.LIGHT)
                countRequests.requestCoin()
                onCoinClick()
            }
        )

        if (onCommentClick != null) {
            InteractionButton(
                icon = Icons.AutoMirrored.Rounded.Comment,
                countText = if (commentCount > 0) FormatUtils.formatStat(commentCount.toLong()) else "评论",
                isActive = false,
                layoutPolicy = layoutPolicy,
                onClick = onCommentClick
            )
        }

        // 收藏
        InteractionButton(
            icon = if (isFavorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            countText = if (favoriteCount > 0) FormatUtils.formatStat(favoriteCount.toLong()) else "收藏",
            isActive = isFavorited,
            activeColor = AppContentColors.accent(),
            layoutPolicy = layoutPolicy,
            countAnimationRequest = countRequests.favorite,
            countValue = favoriteCount,
            onClick = {
                haptic(HapticType.LIGHT)
                countRequests.requestFavorite()
                onFavoriteClick()
            },
            onLongClick = {
                countRequests.requestFavorite()
                onFavoriteLongClick()
            },
        )

        // 分享
        InteractionButton(
            icon = Icons.Rounded.Share,
            countText = if (shareCount > 0) FormatUtils.formatStat(shareCount.toLong()) else "分享",
            isActive = false,
            layoutPolicy = layoutPolicy,
            onClick = onShareClick
        )
    }
}

/**
 * 互动按钮组件 (带数字)
 */
@Composable
private fun InteractionButton(
    icon: ImageVector,
    countText: String,
    isActive: Boolean,
    enabled: Boolean = true,
    contentDescription: String? = null,
    activeColor: Color = AppContentColors.accent(),
    layoutPolicy: PortraitInteractionBarLayoutPolicy,
    progress: Float = 0f,
    countAnimationRequest: Int = 0,
    countValue: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPressRelease: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS, onClick)
    val latestOnClick by rememberUpdatedState(feedbackClick)
    val latestOnLongClick by rememberUpdatedState(onLongClick)
    val latestOnPressRelease by rememberUpdatedState(onPressRelease)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (enabled) Modifier.appElasticPress(dragEnabled = onLongClick == null) else Modifier)
            .then(
                if (enabled && onLongClick != null) {
                    Modifier.pointerInput(enabled, onLongClick != null) {
                        detectTapGestures(
                            onTap = { latestOnClick() },
                            onLongPress = { latestOnLongClick?.invoke() },
                            onPress = {
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    latestOnPressRelease?.invoke()
                                }
                            }
                        )
                    }
                } else {
                    Modifier.clickable(
                        enabled = enabled,
                        indication = null,
                        interactionSource = interactionSource,
                    ) { feedbackClick() }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .size(layoutPolicy.iconBackingSizeDp.dp)
                .padding(layoutPolicy.iconBackingInnerPaddingDp.dp),
            contentAlignment = Alignment.Center
        ) {
            if (progress > 0f) {
                Canvas(modifier = Modifier.size(layoutPolicy.iconBackingSizeDp.dp)) {
                    val stroke = 3.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset(
                        x = (size.width - diameter) / 2f,
                        y = (size.height - diameter) / 2f
                    )
                    drawArc(
                        color = activeColor.copy(alpha = 0.22f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            AppIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (!enabled) Color.White.copy(alpha = 0.45f) else if (isActive) activeColor else Color.White,
                modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
            )
        }
        Spacer(modifier = Modifier.height(layoutPolicy.labelTopSpacingDp.dp))
        VideoActionCountText(
            text = countText,
            request = countAnimationRequest,
            countValue = countValue,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = layoutPolicy.labelFontSp.sp),
        )
    }
}
