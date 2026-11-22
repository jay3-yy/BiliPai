// File: feature/video/ui/section/VideoActionSection.kt
package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.android.purebilibili.core.theme.LocalAppUiStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppDownloadIcon
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.ui.rememberAppWatchLaterIcon
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.components.FavoriteFolderMenuControl
import com.android.purebilibili.feature.video.ui.components.favoriteFolderMenuSource
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.data.model.response.ViewInfo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.android.purebilibili.feature.video.ui.feedback.VideoActionCountRequests
import com.android.purebilibili.feature.video.ui.feedback.VideoActionCountText
import com.android.purebilibili.feature.video.ui.feedback.rememberVideoTriplePressState
import com.android.purebilibili.feature.video.ui.feedback.downloadActionAnchor
import com.android.purebilibili.feature.video.ui.feedback.favoriteActionAnchor
import com.android.purebilibili.feature.video.ui.feedback.likeBurstAnchor
import com.android.purebilibili.feature.video.ui.feedback.resolveVideoDetailActionActiveColors
import com.android.purebilibili.feature.video.ui.feedback.resolveVideoActionCountTint
import com.android.purebilibili.feature.video.ui.feedback.resolveVideoActionTint

internal fun shouldStartTriplePress(longPressConfirmed: Boolean): Boolean {
    return longPressConfirmed
}

internal fun shouldCancelTriplePressOnRelease(
    isTriplePressing: Boolean,
    tripleCompleted: Boolean
): Boolean {
    return isTriplePressing && !tripleCompleted
}

internal fun resolveVideoDetailShareActionText(shareCount: Int): String {
    return if (shareCount > 0) {
        FormatUtils.formatStat(shareCount.toLong())
    } else {
        "分享"
    }
}

internal fun resolveVideoDetailActionRowItemSpacing(actionCount: Int): Dp {
    return if (actionCount >= 6) 0.dp else 2.dp
}

internal fun resolveVideoDetailActionButtonHorizontalPadding(actionCount: Int): Dp {
    return if (actionCount >= 6) 2.dp else 4.dp
}

/**
 * Video Action Section Components
 *
 * Contains components for user interaction:
 * - ActionButtonsRow: Like, coin, favorite, triple, comment buttons
 * - BiliActionButton: Bilibili official style button
 * - ActionButton: Enhanced action button with animations
 *
 * Requirement Reference: AC3.2 - User action components in dedicated file
 */

/**
 * Action Buttons Row (Bilibili official style: icon + number, no circle background)
 */
@Composable
fun ActionButtonsRow(
    info: ViewInfo,
    isFavorited: Boolean = false,
    isLiked: Boolean = false,
    likeStatusKnown: Boolean = false,
    isDisliked: Boolean = false,
    coinCount: Int = 0,
    downloadProgress: Float = -1f,  //  -1 = 未下载, 0-1 = 进度, 1 = 已完成
    isInWatchLater: Boolean = false,  //  稍后再看状态
    onFavoriteClick: () -> Unit = {},
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onCoinClick: () -> Unit = {},
    onTripleClick: () -> Unit = {},
    onCommentClick: () -> Unit,
    onDownloadClick: () -> Unit = {},  //  下载点击
    onWatchLaterClick: () -> Unit = {},  //  稍后再看点击
    onFavoriteLongClick: () -> Unit = {}, // [New] 长按收藏
    onShareClick: () -> Unit = {},
    showCommentAction: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiStyle = LocalAppUiStyle.current
    val activeColors = remember(
        uiStyle,
        colorScheme.primary,
        colorScheme.secondary
    ) {
        resolveVideoDetailActionActiveColors(
            primary = colorScheme.primary,
            secondary = colorScheme.secondary,
            uiStyle = uiStyle,
        )
    }
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
    val countRequests = remember(info.bvid) { VideoActionCountRequests() }
    val latestOnLikeClick by rememberUpdatedState(onLikeClick)
    val triplePress = rememberVideoTriplePressState(info.bvid, likeStatusKnown) {
        countRequests.requestTriple()
        haptic(HapticType.MEDIUM)
        onTripleClick()
    }
    val tripleProgress = triplePress.progress
    LaunchedEffect(triplePress.isPressing) {
        if (triplePress.isPressing) haptic(HapticType.LIGHT)
    }

    val shareIcon = rememberAppShareIcon()
    val watchLaterIcon = rememberAppWatchLaterIcon()
    val downloadIcon = rememberAppDownloadIcon()
    val actionCount = 7 + if (showCommentAction) 1 else 0 // like/dislike/coin/fav/share/watchLater/cache[+comment]
    val itemSpacing = resolveVideoDetailActionRowItemSpacing(actionCount)
    val buttonHorizontalPadding = resolveVideoDetailActionButtonHorizontalPadding(actionCount)

    // 不再对整行做 animateContentSize：状态回填时动画尺寸会放大“跳动”观感，
    // 行高本身由各按钮的固定尺寸保证稳定。
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like - 支持长按触发三连
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                // 点赞状态未确认时不再渲染空图标占位：直接按“未点赞”的默认样式渲染，
                // 图标/文案使用主题未激活色，状态回填后由 Crossfade 平滑切换。
                // 这样首帧就是一个完整可读的按钮，避免空白→图标 pop-in 造成的闪烁。
                TripleProgressActionButton(
                    icon = if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                    text = FormatUtils.formatStat(info.stat.like.toLong()),
                    isActive = isLiked,
                    activeColor = activeColors.primaryAction,
                    progress = tripleProgress,
                    countAnimationRequest = countRequests.like,
                    countValue = info.stat.like,
                    countIdentity = info.bvid,
                    onClick = {
                        if (likeStatusKnown) {
                            haptic(HapticType.LIGHT)
                            countRequests.requestLike()
                            latestOnLikeClick()
                        }
                    },
                    horizontalPadding = buttonHorizontalPadding,
                    modifier = Modifier
                        .likeBurstAnchor()
                        .pointerInput(info.bvid, likeStatusKnown) {
                            if (!likeStatusKnown) return@pointerInput
                            // Progress must not be a pointerInput key: its frame updates would
                            // cancel the very long press that is filling the progress rings.
                            detectTapGestures(
                                onTap = {
                                    haptic(HapticType.LIGHT)
                                    countRequests.requestLike()
                                    latestOnLikeClick()
                                },
                                onLongPress = { triplePress.start() },
                                onPress = {
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        triplePress.release()
                                    }
                                },
                            )
                        },
                    disableInternalClick = true
                )
            }

            // Dislike - 点踩（与点赞互斥，官方不展示点踩数，固定显示文字）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                TripleProgressActionButton(
                    icon = if (isDisliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                    text = "点踩",
                    isActive = isDisliked,
                    activeColor = activeColors.primaryAction,
                    progress = 0f,
                    onClick = onDislikeClick,
                    horizontalPadding = buttonHorizontalPadding
                )
            }

            // Coin
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                TripleProgressActionButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_video_detail_coin_outline_24),
                    text = FormatUtils.formatStat(info.stat.coin.toLong()),
                    isActive = coinCount > 0,
                    activeColor = activeColors.primaryAction,
                    progress = tripleProgress,
                    countAnimationRequest = countRequests.coin,
                    countValue = info.stat.coin,
                    countIdentity = info.bvid,
                    onClick = {
                        haptic(HapticType.LIGHT)
                        countRequests.requestCoin()
                        onCoinClick()
                    },
                    horizontalPadding = buttonHorizontalPadding
                )
            }

            if (showCommentAction) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BiliActionButton(
                        icon = rememberAppCommentIcon(),
                        text = "评论 ${FormatUtils.formatStat(info.stat.reply.toLong())}",
                        isActive = false,
                        activeColor = activeColors.primaryAction,
                        onClick = onCommentClick,
                        horizontalPadding = buttonHorizontalPadding
                    )
                }
            }

            // Favorite
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                TripleProgressActionButton(
                    icon = if (isFavorited) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                    text = FormatUtils.formatStat(info.stat.favorite.toLong()),
                    isActive = isFavorited,
                    activeColor = activeColors.primaryAction,
                    progress = tripleProgress,
                    countAnimationRequest = countRequests.favorite,
                    countValue = info.stat.favorite,
                    countIdentity = info.bvid,
                    onClick = {
                        haptic(HapticType.LIGHT)
                        countRequests.requestFavorite()
                        onFavoriteClick()
                    },
                    onLongClick = {
                        countRequests.requestFavorite()
                        onFavoriteLongClick()
                    },
                    horizontalPadding = buttonHorizontalPadding,
                    modifier = Modifier.favoriteFolderMenuSource(FavoriteFolderMenuControl.DETAIL).favoriteActionAnchor()
                )
            }

            // Share
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                com.android.purebilibili.feature.video.share.VideoShareActionAnchor(onShareClick) { anchoredShare ->
                    BiliActionButton(
                        icon = shareIcon,
                        text = resolveVideoDetailShareActionText(info.stat.share),
                        isActive = false,
                        activeColor = activeColors.primaryAction,
                        onClick = anchoredShare,
                        horizontalPadding = buttonHorizontalPadding
                    )
                }
            }

            //  稍后再看
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                BiliActionButton(
                    icon = watchLaterIcon,
                    text = if (isInWatchLater) "已添加" else "稍后看",
                    isActive = isInWatchLater,
                    activeColor = activeColors.watchLater,
                    onClick = onWatchLaterClick,
                    horizontalPadding = buttonHorizontalPadding
                )
            }

            //  Download
            val downloadText = when {
                downloadProgress >= 1f -> "已缓存"
                downloadProgress >= 0f -> "${(downloadProgress * 100).toInt()}%"
                else -> "缓存"
            }
            val isDownloaded = downloadProgress >= 1f
            val isDownloading = downloadProgress in 0f..0.99f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.downloadActionAnchor()) {
                    BiliActionButton(
                        icon = if (isDownloaded) Icons.Outlined.Check else downloadIcon,
                        text = downloadText,
                        isActive = isDownloaded || isDownloading,
                        activeColor = if (isDownloaded) {
                            activeColors.downloaded
                        } else {
                            activeColors.downloadInProgress
                        },
                        onClick = onDownloadClick,
                        horizontalPadding = buttonHorizontalPadding
                    )
                }
            }

        }
        if (com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED) {
            com.android.purebilibili.feature.nearby.NearbyDevicesButton(
                modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TripleProgressActionButton(
    icon: ImageVector,
    text: String,
    isActive: Boolean,
    activeColor: Color,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    disableInternalClick: Boolean = false,
    countAnimationRequest: Int = 0,
    countIdentity: Any? = Unit,
    countValue: Int? = null,
    horizontalPadding: Dp = 4.dp
) {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS, onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val inactiveTint = AppContentColors.secondaryText()
    val iconTint = resolveVideoActionTint(
        isActive = isActive,
        activeColor = activeColor,
        inactiveColor = inactiveTint
    )
    val textTint = resolveVideoActionCountTint(
        isActive = isActive,
        activeColor = activeColor,
        inactiveColor = inactiveTint
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .appElasticPress(
                enabled = !disableInternalClick,
                dragEnabled = onLongClick == null,
            )
            .then(
                if (disableInternalClick) {
                    Modifier
                } else {
                    Modifier.appCombinedClickable(
                        longPressScene = com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = feedbackClick,
                        onLongClick = onLongClick
                    )
                }
            )
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (progress > 0f) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    val stroke = 2.5.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)

                    drawArc(
                        color = activeColor.copy(alpha = 0.18f),
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
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }

            // 图标按主题色渲染；状态回填导致图标变化(如点赞/点踩/收藏)时用 Crossfade 过渡，
            // 避免图标硬切造成的闪烁感。
            Crossfade(
                targetState = icon,
                animationSpec = tween(durationMillis = 180, easing = AppMotionEasing.M1Standard),
                label = "videoActionIcon"
            ) { targetIcon ->
                AppIcon(
                    imageVector = targetIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        VideoActionCountText(
            text = text,
            request = countAnimationRequest,
            identity = countIdentity,
            countValue = countValue,
            color = textTint,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * 一键三连长按按钮 - 长按显示点赞、投币、收藏三个图标的圆形进度条
 */
@Composable
private fun TripleLikeActionButton(
    isLiked: Boolean,
    likeCount: String,
    coinCount: String,
    isFavorited: Boolean,
    favoriteCount: String,
    hasCoin: Boolean,
    onLikeClick: () -> Unit,
    onTripleComplete: () -> Unit,
    onProgressChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)

    // 长按进度状态
    var isLongPressing by remember { mutableStateOf(false) }
    val progressDuration = 1500 // 1.5 秒
    val latestOnProgressChange by rememberUpdatedState(onProgressChange)

    // 进度动画
    val animatedProgressState = animateFloatAsState(
        targetValue = if (isLongPressing) 1f else 0f,
        animationSpec = if (isLongPressing) {
            tween(durationMillis = progressDuration, easing = LinearEasing)
        } else {
            tween(durationMillis = 200, easing = AppMotionEasing.M1Standard)
        },
        label = "tripleLikeProgress",
        finishedListener = { progress ->
            if (progress >= 1f && isLongPressing) {
                haptic(HapticType.MEDIUM)
                onTripleComplete()
                isLongPressing = false
            }
        }
    )

    LaunchedEffect(animatedProgressState) {
        snapshotFlow { animatedProgressState.value }.collect { progress ->
            latestOnProgressChange(progress)
        }
    }

    val longPressProgress = animatedProgressState.value

    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            haptic(HapticType.LIGHT)
        }
    }

    // 显示三个图标的进度
    Row(
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isLongPressing = true
                        val released = tryAwaitRelease()
                        isLongPressing = false
                        if (released && longPressProgress < 0.1f) {
                            onLikeClick()
                        }
                    }
                )
            }
    ) {
        // 点赞图标
        TripleProgressIcon(
            icon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            text = likeCount,
            progress = longPressProgress,
            progressColor = AppContentColors.accent(),
            isActive = isLiked
        )

        // 投币图标 (只在长按时显示)
        androidx.compose.animation.AnimatedVisibility(
            visible = longPressProgress > 0.05f,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
        ) {
            TripleProgressIcon(
                icon = ImageVector.vectorResource(R.drawable.ic_video_detail_coin_outline_24),
                text = coinCount,
                progress = longPressProgress,
                progressColor = AppContentColors.accent(),
                isActive = hasCoin
            )
        }

        // 收藏图标 (只在长按时显示)
        androidx.compose.animation.AnimatedVisibility(
            visible = longPressProgress > 0.1f,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
        ) {
            TripleProgressIcon(
                icon = if (isFavorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                text = favoriteCount,
                progress = longPressProgress,
                progressColor = AppContentColors.accent(),
                isActive = isFavorited
            )
        }
    }
}

/**
 * 带圆形进度环的图标
 */
@Composable
fun TripleProgressIcon(
    icon: ImageVector,
    text: String,
    progress: Float,
    progressColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val inactiveTint = AppContentColors.secondaryText()
    val iconTint = resolveVideoActionTint(
        isActive = isActive,
        activeColor = progressColor,
        inactiveColor = inactiveTint
    )
    val textTint = resolveVideoActionCountTint(
        isActive = isActive,
        activeColor = progressColor,
        inactiveColor = inactiveTint
    )
    val iconSize = 24.dp
    val ringSize = iconSize
    val strokeWidth = 2.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            // 进度环
            if (progress > 0f) {
                Canvas(modifier = Modifier.size(ringSize)) {
                    val stroke = strokeWidth.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)

                    // 背景环
                    drawArc(
                        color = progressColor.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    // 进度环
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }

            // 图标
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
        AppText(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textTint,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Bilibili Official Style Action Button - icon + number, no circle background
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BiliActionButton(
    icon: ImageVector,
    text: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null, // [New] Long click support
    enableActivePulse: Boolean = false,
    horizontalPadding: Dp = 4.dp,
    enabled: Boolean = true,
) {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS, onClick)
    val interactionSource = remember { MutableInteractionSource() }

    // Active state pulse animation: impulse-driven single spring cycle (no mid-air pause)
    val pulseScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var wasActive by remember { mutableStateOf(isActive) }
    LaunchedEffect(isActive, enableActivePulse) {
        val activated = isActive && !wasActive
        wasActive = isActive
        if (!enableActivePulse) {
            pulseScale.snapTo(1f)
        } else if (activated) {
            pulseScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = 700f,
                ),
                initialVelocity = 4.2f,
            )
        }
    }

    val inactiveTint = AppContentColors.secondaryText()
    val iconTint = resolveVideoActionTint(
        isActive = isActive,
        activeColor = activeColor,
        inactiveColor = inactiveTint
    )
    val textTint = resolveVideoActionCountTint(
        isActive = isActive,
        activeColor = activeColor,
        inactiveColor = inactiveTint
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .appElasticPress(enabled = enabled, dragEnabled = onLongClick == null)
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .appCombinedClickable(
                longPressScene = com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = feedbackClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        AppText(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textTint,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/**
 * Enhanced Action Button - with press animation and colored icon
 */
@Composable
fun ActionButton(
    icon: ImageVector,
    text: String,
    isActive: Boolean = false,
    iconColor: Color = Color.Unspecified,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit = {}
) {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS, onClick)
    val resolvedIconColor = iconColor.takeOrElse {
        if (isActive) AppContentColors.accent() else AppContentColors.secondaryText()
    }
    val isDark = com.android.purebilibili.core.ui.isAppInDarkTheme()

    val interactionSource = remember { MutableInteractionSource() }

    // Heartbeat pulse animation - triggered only when transitioning from inactive to active
    val pulseScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var wasActive by remember { mutableStateOf(isActive) }
    LaunchedEffect(isActive) {
        val activated = isActive && !wasActive
        wasActive = isActive
        if (activated) {
            pulseScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.52f,
                    stiffness = 650f,
                ),
                initialVelocity = 5.4f,
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .width(56.dp)
            .appElasticPress()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { feedbackClick() }
    ) {
        // Icon container - uses colored background, higher alpha in dark mode
        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                }
                .clip(CircleShape)
                .background(resolvedIconColor.copy(alpha = if (isDark) 0.15f else 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedIconColor,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        AppText(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) resolvedIconColor else AppContentColors.secondaryText(),
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}
