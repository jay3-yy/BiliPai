package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.feature.video.ui.components.favoriteFolderMenuSource
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.ui.components.appActionWithFeedback

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.ui.components.rememberFollowActionWithConfirmation
import com.android.purebilibili.feature.video.ui.feedback.followActionAnchor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.currentStateAsState
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppIcons
import androidx.compose.ui.unit.constrainHeight
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuGlyph
import com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSettingsIcon
import com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSendIcon
import com.android.purebilibili.feature.video.ui.feedback.likeBurstAnchor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.outlined.Home

/**
 * Shared Android PiliPlus HeaderControl for inline/fullscreen and portrait/landscape.
 * Each button owns its material; title, status and action rows never become glass capsules.
 * Nullable actions permit isolated previews. Production callers supply every applicable handler.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopControlBar(
    title: String,
    onlineCount: String = "",
    isFullscreen: Boolean,
    statusBarVisible: Boolean = isFullscreen,
    showBatteryLevel: Boolean = false,
    showCurrentTime: Boolean = true,
    showInteractiveActions: Boolean = true,
    likeCount: Long? = null,
    favoriteCount: Long? = null,
    commentCount: Long? = null,
    onBack: () -> Unit,
    isLiked: Boolean = false,
    likeStatusKnown: Boolean,
    isCoined: Boolean = false,
    allowDislikeAction: Boolean = true,
    onLikeClick: (() -> Unit)? = null,
    onDislikeClick: (() -> Unit)? = null,
    onCoinClick: (() -> Unit)? = null,
    shareMenuSource: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    onShareClick: (() -> Unit)? = null,
    onCastClick: (() -> Unit)? = null,
    showCastButton: Boolean = true,
    onMoreClick: (() -> Unit)? = null,
    isFavorited: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isPortrait: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
    horizontalScreen: Boolean = true,
    isFileSource: Boolean = false,
    isUgc: Boolean = true,
    onHomeClick: (() -> Unit)? = null,
    moreMenuSource: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    danmakuMenuSource: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    onAudioClick: (() -> Unit)? = null,
    onSendDanmakuClick: (() -> Unit)? = null,
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: (() -> Unit)? = null,
    onDanmakuSettingsClick: (() -> Unit)? = null,
    showPipButton: Boolean = false,
    onPipClick: (() -> Unit)? = null,
    onLikeLongClick: (() -> Unit)? = null,
    onFavoriteLongClick: (() -> Unit)? = null,
    isDisliked: Boolean = false,
    showSponsorSubmitButton: Boolean = false,
    sponsorSubmitDescription: String = "提交片段",
    onSponsorSubmitClick: (() -> Unit)? = null,
    showSponsorSegmentsButton: Boolean = false,
    onSponsorSegmentsClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onRelatedVideosClick: (() -> Unit)? = null,
    commentsVisible: Boolean = false,
    relatedVideosVisible: Boolean = false,
    simplifiedPortrait: Boolean = false,
    authorKey: String = "",
    authorName: String = "",
    authorFace: String = "",
    isFollowing: Boolean = false,
    followStatusKnown: Boolean = false,
    onFollowClick: () -> Unit = {},
) {
    val policy = resolveTopControlBarLayoutPolicy(
        isPortrait = isPortrait,
        isFullscreen = isFullscreen,
        horizontalScreen = horizontalScreen,
        isFileSource = isFileSource,
        isUgc = isUgc,
        showFullscreenActionItems = showInteractiveActions,
    )
    val visibleCounts = rememberVisibleFullscreenCounts()
    val showCounts = isFullscreen
    fun visibleCount(kind: com.android.purebilibili.core.store.FullscreenActionCount, value: Long?): Long? =
        value.takeIf { showCounts && kind in visibleCounts }
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val clockVisible = !simplifiedPortrait && policy.showStatus && showCurrentTime
    val batteryVisible = !simplifiedPortrait && policy.showStatus && showBatteryLevel
    val currentTime by produceState(initialValue = formatCurrentTime(), clockVisible, hostStarted) {
        if (!shouldPollTopControlBarClock(clockVisible, hostStarted)) return@produceState
        while (true) {
            value = formatCurrentTime()
            delay((60_000L - System.currentTimeMillis() % 60_000L).coerceAtLeast(1_000L))
        }
    }
    val batteryPercent by produceState<Int?>(initialValue = null, batteryVisible, hostStarted) {
        if (!shouldPollTopControlBarBattery(batteryVisible, hostStarted)) {
            value = null
            return@produceState
        }
        while (true) {
            value = resolveBatteryLevelPercent(context)
            delay(30_000L)
        }
    }
    val glass = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    // 仅收紧竖屏内嵌玻璃按钮的组内间距，原生与全屏保留既有几何。
    val buttonSpacing = if (glass) {
        if (!isFullscreen && isPortrait) 2.dp else 4.dp
    } else 0.dp
    val motionEnabled = LocalComponentMotionEnabled.current
    val showPortraitAuthor = simplifiedPortrait && isFullscreen && authorName.isNotBlank()
    val hasTitle = policy.showTitle && (title.isNotEmpty() || showPortraitAuthor)
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
    val likeClick: (() -> Unit)? = onLikeClick?.let { action ->
        { haptic(HapticType.LIGHT); action() }
    }
    val triplePress = rememberPiliHeaderTriplePressState()
    val likeGesture = if (likeStatusKnown && onLikeClick != null && onLikeLongClick != null) {
        piliHeaderLikeGesture(triplePress, isLiked && isCoined && isFavorited, requireNotNull(likeClick), onLikeLongClick)
    } else null

    PlayerControlTouchBounds(height = policy.buttonHeightDp.dp) {
        Column(
            modifier = modifier.fillMaxWidth()
                .windowInsetsPadding(playerControlHorizontalInsets(isFullscreen, isPortrait))
                .playerControlTopSafeArea(isFullscreen, isPortrait, statusBarVisible)
                .padding(vertical = (if (!isFullscreen && isPortrait) 8 else policy.verticalPaddingDp).dp),
        ) {
            PiliHeaderSlots(
                hasTitle = hasTitle,
                allowSeparateTitleRow = isFullscreen && !isPortrait,
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
                        HeaderIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack, iconSize = if (glass) 15 else 20)
                        if (!simplifiedPortrait && policy.showHome && onHomeClick != null) {
                            HeaderIcon(Icons.Outlined.Home, "返回首页", onHomeClick)
                        }
                    }
                },
                right = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
                        if (glass) {
                            if (clockVisible || (batteryVisible && batteryPercent != null)) {
                                PlayerControlGlassDock(
                                    enabled = true,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                ) {
                                    Row(
                                        modifier = Modifier.heightIn(min = policy.buttonHeightDp.dp).padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        if (batteryVisible) batteryPercent?.let { HeaderStatusText("$it%") }
                                        if (clockVisible) HeaderStatusText(currentTime)
                                    }
                                }
                            }
                        } else {
                            if (batteryVisible) {
                                batteryPercent?.let { HeaderStatusText("$it%") }
                                Spacer(Modifier.width(10.dp))
                            }
                            if (clockVisible) HeaderStatusText(currentTime)
                        }
                        if (!simplifiedPortrait && policy.showAudio && onAudioClick != null) {
                            HeaderIcon(Icons.Outlined.Headphones, "听音频", onAudioClick)
                        }
                        if (!simplifiedPortrait && policy.showCast && showCastButton && onCastClick != null) {
                            HeaderIcon(Icons.Outlined.Cast, "投屏", onCastClick)
                        }
                        if (!simplifiedPortrait && !isFileSource && showSponsorSubmitButton && onSponsorSubmitClick != null) {
                            HeaderButton(sponsorSubmitDescription, onSponsorSubmitClick) {
                                PiliPlusDanmakuGlyph("\uE814", sponsorSubmitDescription, Color.White, 20.dp)
                            }
                        }
                        if (!simplifiedPortrait && showSponsorSegmentsButton && onSponsorSegmentsClick != null) {
                            HeaderButton("片段信息", onSponsorSegmentsClick) { HeaderAdvertisementIcon() }
                        }
                        if (!simplifiedPortrait && policy.showDanmakuActions) {
                            if (onSendDanmakuClick != null) {
                                HeaderButton("发弹幕", onSendDanmakuClick) {
                                    PiliPlusDanmakuSendIcon(tint = Color.White, size = 20.dp)
                                }
                            }
                            if (onDanmakuToggle != null) {
                                val description = if (danmakuEnabled) "关闭弹幕" else "开启弹幕"
                                HeaderButton(description, onDanmakuToggle) {
                                    PiliPlusDanmakuGlyph(
                                        if (danmakuEnabled) "\uE803" else "\uE802",
                                        description,
                                        if (danmakuEnabled) com.android.purebilibili.core.ui.AppSurfaceTokens.primary() else Color.White,
                                        20.dp,
                                    )
                                }
                            }
                        }
                        if (!simplifiedPortrait && onDanmakuSettingsClick != null) {
                            HeaderButton("弹幕设置", onDanmakuSettingsClick, modifier = danmakuMenuSource?.let { Modifier.appGlassMenuSource(it, onOpen = onDanmakuSettingsClick) } ?: Modifier) {
                                PiliPlusDanmakuSettingsIcon(tint = Color.White, size = 20.dp)
                            }
                        }
                        if (!simplifiedPortrait && showPipButton && onPipClick != null) {
                            HeaderIcon(Icons.Outlined.PictureInPicture, "画中画", onPipClick)
                        }
                        if (onMoreClick != null) {
                            HeaderIcon(Icons.Outlined.MoreVert, if (simplifiedPortrait) "更多" else "更多设置", onMoreClick, modifier = moreMenuSource?.let { Modifier.appGlassMenuSource(it, onOpen = onMoreClick) } ?: Modifier)
                        }
                    }
                },
                title = {
                    // 精简竖屏排列作者信息与单行标题；其余布局保留各自的标题材质与间距。
                    PlayerControlGlassDock(
                        enabled = glass && !simplifiedPortrait,
                        modifier = Modifier
                            .padding(horizontal = if (simplifiedPortrait) 12.dp else 0.dp)
                            .padding(end = if (isPortrait) 0.dp else 10.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    ) {
                        Column(
                            modifier = if (glass && !simplifiedPortrait) Modifier
                                .heightIn(min = policy.buttonHeightDp.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                            else Modifier,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (showPortraitAuthor) {
                                PortraitPlayerAuthorRow(
                                    authorKey = authorKey,
                                    authorName = authorName,
                                    authorFace = authorFace,
                                    isFollowing = isFollowing,
                                    followStatusKnown = followStatusKnown,
                                    onFollowClick = onFollowClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                )
                            }
                            AppText(
                                text = title, color = Color.White,
                                fontSize = if (showPortraitAuthor) 13.sp else policy.titleFontSp.sp,
                                lineHeight = if (showPortraitAuthor) 17.sp else 19.sp,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                modifier = if (motionEnabled && !simplifiedPortrait) Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE, spacing = androidx.compose.foundation.MarqueeSpacing(30.dp),
                                    velocity = 30.dp,
                                ) else Modifier,
                                tapToCopyEnabled = false,
                            )
                            if (!simplifiedPortrait && onlineCount.isNotBlank()) {
                                AppText(
                                    text = onlineCount, color = Color.White,
                                    fontSize = policy.onlineCountFontSp.sp, lineHeight = 13.sp, maxLines = 1,
                                    softWrap = false, overflow = TextOverflow.Ellipsis,
                                    tapToCopyEnabled = false,
                                )
                            }
                        }
                    }
                },
            )
            // 侧栏入口属于浏览功能，不随点赞、投币等互动快捷键一起隐藏。
            if (!simplifiedPortrait && (policy.showEngagement || onCommentClick != null || onRelatedVideosClick != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 两排玻璃轮廓留出独立呼吸空间；原生控件保持原有间距。
                        .then(if (glass) Modifier.padding(top = 6.dp) else Modifier)
                        .horizontalScroll(rememberScrollState())
                        // 角标伸出按钮的部分也必须位于滚动视口内；两种材质共用此边界。
                        .padding(top = PlayerActionCountTopOverflow, end = PlayerActionCountEndOverflow),
                    horizontalArrangement = if (glass) Arrangement.spacedBy(buttonSpacing, Alignment.End) else Arrangement.End,
                ) {
                    if (onCommentClick != null) {
                        HeaderIcon(Icons.Outlined.Forum, "评论", onCommentClick,
                            active = commentsVisible, iconSize = 18,
                            count = visibleCount(com.android.purebilibili.core.store.FullscreenActionCount.COMMENT, commentCount))
                    }
                    if (onRelatedVideosClick != null) {
                        HeaderIcon(Icons.Outlined.VideoLibrary, "相关推荐", onRelatedVideosClick,
                            active = relatedVideosVisible, iconSize = 18)
                    }
                    if (policy.showEngagement) {
                        if (onLikeClick != null) {
                            Box(Modifier.likeBurstAnchor()) {
                                HeaderIcon(
                                    if (likeStatusKnown && isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    if (likeStatusKnown) "点赞" else "点赞状态尚未确认",
                                    requireNotNull(likeClick), active = likeStatusKnown && isLiked, iconSize = 18,
                                    gestureModifier = likeGesture, progress = triplePress.progress,
                                    count = visibleCount(com.android.purebilibili.core.store.FullscreenActionCount.LIKE, likeCount),
                                )
                            }
                        }
                        if (policy.showDislike && allowDislikeAction && onDislikeClick != null) {
                            HeaderIcon(
                                if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                "点踩", onDislikeClick, active = isDisliked, iconSize = 18,
                            )
                        }
                        if (onCoinClick != null) HeaderIcon(
                            AppIcons.BiliCoin, "投币", { haptic(HapticType.LIGHT); onCoinClick() }, active = isCoined,
                            progress = triplePress.progress, iconSize = 18,
                        )
                        if (onFavoriteClick != null) {
                            HeaderIcon(
                                if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                "收藏", { haptic(HapticType.LIGHT); onFavoriteClick() }, active = isFavorited, iconSize = 18,
                                onLongClick = onFavoriteLongClick, progress = triplePress.progress,
                                modifier = Modifier.favoriteFolderMenuSource(),
                                count = visibleCount(com.android.purebilibili.core.store.FullscreenActionCount.FAVORITE, favoriteCount),
                            )
                        }
                        if (onShareClick != null) HeaderIcon(Icons.Outlined.Share, "分享", onShareClick, iconSize = 18, modifier = shareMenuSource?.let { Modifier.appGlassMenuSource(it, onOpen = onShareClick) } ?: Modifier)
                    }
                }
            }
        }
    }
}

/** 竖屏全屏显示当前 UP 主；未知关注状态与未关注分开呈现，取关复用确认流程。 */
@Composable
private fun PortraitPlayerAuthorRow(
    authorKey: String,
    authorName: String,
    authorFace: String,
    isFollowing: Boolean,
    followStatusKnown: Boolean,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val followAction = rememberFollowActionWithConfirmation(
        targetKey = authorKey,
        userName = authorName,
        isFollowing = followStatusKnown && isFollowing,
        onFollowClick = onFollowClick,
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (authorFace.isNotBlank()) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(authorFace),
                contentDescription = "$authorName 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape),
            )
        } else {
            AppIcon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "$authorName 的头像",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        AppText(
            text = authorName,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            tapToCopyEnabled = false,
        )
        PlayerControlTextAction(
            text = when {
                !followStatusKnown -> "待确认"
                isFollowing -> "已关注"
                else -> "+ 关注"
            },
            description = when {
                !followStatusKnown -> "重新确认关注状态"
                isFollowing -> "已关注，点击取消关注"
                else -> "关注 $authorName"
            },
            onClick = followAction,
            selected = followStatusKnown && isFollowing,
            height = 32.dp,
            modifier = Modifier.followActionAnchor(),
        )
    }
}

/** 竖屏全屏仅保留常用互动；固定槽位让未知、零值和长计数不改变布局。 */
@Composable
internal fun PortraitEngagementRail(
    isLiked: Boolean,
    likeStatusKnown: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    likeCount: Long?,
    commentCount: Long?,
    favoriteCount: Long?,
    onLike: () -> Unit,
    onTriple: () -> Unit,
    onCoin: () -> Unit,
    onComment: () -> Unit,
    onFavorite: () -> Unit,
    onQuickFavorite: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    shareMenuSource: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
) {
    val glassDock = LocalPlayerControlGlassEnabled.current
    val visibleCounts = rememberVisibleFullscreenCounts()
    val triplePress = rememberPiliHeaderTriplePressState()
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
    val likeClick = { haptic(HapticType.LIGHT); onLike() }
    val gesture = if (likeStatusKnown) {
        piliHeaderLikeGesture(triplePress, isLiked && isCoined && isFavorited, likeClick, onTriple)
    } else null
    // 玻璃专属：整列共用一个外壳，子按钮不再各自绘制圆形玻璃；原生保留独立按钮。
    Box(modifier = modifier) {
        PlayerControlGlassGroup(enabled = glassDock) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .then(if (glassDock) Modifier.padding(vertical = 8.dp) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (glassDock) 8.dp else 12.dp),
            ) {
                Box(Modifier.likeBurstAnchor()) {
                    HeaderIcon(
                        if (likeStatusKnown && isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        if (likeStatusKnown) "点赞" else "点赞状态尚未确认", onLike,
                        active = likeStatusKnown && isLiked, iconSize = 24, buttonSizeDp = 44,
                        gestureModifier = gesture, progress = triplePress.progress,
                        count = likeCount.takeIf { com.android.purebilibili.core.store.FullscreenActionCount.LIKE in visibleCounts },
                    )
                }
                if (glassDock) {
                    HeaderIcon(AppIcons.BiliCoin, "投币", onCoin, active = isCoined,
                        iconSize = 24, buttonSizeDp = 44, progress = triplePress.progress)
                } else {
                    HeaderIcon(Icons.Outlined.Forum, "评论", onComment, iconSize = 24, buttonSizeDp = 44,
                        count = commentCount.takeIf { com.android.purebilibili.core.store.FullscreenActionCount.COMMENT in visibleCounts })
                }
                HeaderIcon(
                    if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    "收藏", onFavorite, active = isFavorited, iconSize = 24, buttonSizeDp = 44,
                    onLongClick = onQuickFavorite, modifier = Modifier.favoriteFolderMenuSource(),
                    count = favoriteCount.takeIf { com.android.purebilibili.core.store.FullscreenActionCount.FAVORITE in visibleCounts },
                )
                if (glassDock) {
                    HeaderIcon(Icons.Outlined.Forum, "评论", onComment, iconSize = 24, buttonSizeDp = 44,
                        count = commentCount.takeIf { com.android.purebilibili.core.store.FullscreenActionCount.COMMENT in visibleCounts })
                }
                HeaderIcon(Icons.Outlined.Share, "分享", onShare, iconSize = 24, buttonSizeDp = 44,
                    modifier = shareMenuSource?.let { Modifier.appGlassMenuSource(it, onOpen = onShare) } ?: Modifier)
            }
        }
    }
}

/**
 * 左右操作先按自然尺寸测量；横屏全屏的标题空间不足时独占下一行。
 * 由实际播放器宽度判断，因此左右侧栏、窄窗口和大字体共用同一条布局路径。
 * 材质只由各控件负责，不改变玻璃、MD3、Miuix 的字号、按钮尺寸或默认间距。
 */
@Composable
private fun PiliHeaderSlots(
    hasTitle: Boolean,
    allowSeparateTitleRow: Boolean,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    title: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { left() }
            Box { right() }
            if (hasTitle) Box { title() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val natural = Constraints(maxHeight = constraints.maxHeight)
        val leftPlaceable = measurables[0].measure(natural)
        val rightPlaceable = measurables[1].measure(natural)
        val edgeWidth = leftPlaceable.width + rightPlaceable.width
        val width = constraints.maxWidth
        val scale = if (edgeWidth > width) width.toFloat() / edgeWidth else 1f
        // 至少为八个标题字及材质内边距留出空间，避免标题退化成竖长窄条。
        val minimumTitleWidth = 128.sp.roundToPx() + 34.dp.roundToPx()
        val separateTitleRow = hasTitle && allowSeparateTitleRow &&
            width - edgeWidth < minimumTitleWidth
        val edgeHeight = maxOf(leftPlaceable.height, rightPlaceable.height)
        val titleRowGap = if (separateTitleRow) 4.dp.roundToPx() else 0
        val titlePlaceable = if (hasTitle) measurables[2].measure(
            Constraints(
                maxWidth = if (separateTitleRow) width else (width - edgeWidth).coerceAtLeast(0),
                maxHeight = constraints.maxHeight,
            ),
        ) else null
        val rowHeight = if (separateTitleRow) edgeHeight else maxOf(edgeHeight, titlePlaceable?.height ?: 0)
        val height = constraints.constrainHeight(
            rowHeight + if (separateTitleRow) titleRowGap + (titlePlaceable?.height ?: 0) else 0,
        )
        layout(width, height) {
            if (scale < 1f) {
                leftPlaceable.placeRelativeWithLayer(0, ((rowHeight - leftPlaceable.height * scale) / 2).toInt()) {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                }
                rightPlaceable.placeRelativeWithLayer(
                    (leftPlaceable.width * scale).toInt(), ((rowHeight - rightPlaceable.height * scale) / 2).toInt(),
                ) {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                }
            } else {
                leftPlaceable.placeRelative(0, (rowHeight - leftPlaceable.height) / 2)
                rightPlaceable.placeRelative(width - rightPlaceable.width, (rowHeight - rightPlaceable.height) / 2)
            }
            titlePlaceable?.placeRelative(
                if (separateTitleRow) 0 else leftPlaceable.width,
                if (separateTitleRow) rowHeight + titleRowGap else (rowHeight - titlePlaceable.height) / 2,
            )
        }
    }
}

@Composable
private fun HeaderStatusText(text: String) {
    AppText(
        text = text, color = Color.White, fontSize = 13.sp, maxLines = 1,
        softWrap = false, tapToCopyEnabled = false,
    )
}

@Composable
private fun HeaderAdvertisementIcon() {
    Box(Modifier.size(width = 19.dp, height = 15.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                Color.White,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                style = Stroke(1.4.dp.toPx()),
            )
        }
        AppText(
            text = "AD", color = Color.White, fontSize = 8.sp, lineHeight = 10.sp,
            maxLines = 1, softWrap = false, tapToCopyEnabled = false,
        )
    }
}

@Composable
private fun HeaderIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    iconSize: Int = 19,
    onLongClick: (() -> Unit)? = null,
    gestureModifier: Modifier? = null,
    progress: Float = 0f,
    count: Long? = null,
    modifier: Modifier = Modifier,
    buttonSizeDp: Int? = null,
) {
    val progressColor = AppContentColors.accent()
    CountedPlayerAction(count = count, iconSize = iconSize.dp, modifier = modifier) {
        HeaderButton(description, onClick, active, onLongClick, gestureModifier, buttonSizeDp = buttonSizeDp) {
            if (progress > 0f) Canvas(Modifier.size(28.dp)) {
                drawArc(progressColor, -90f, -360f * progress, useCenter = false, style = Stroke(2.dp.toPx()))
            }
            AppIcon(
                imageVector = icon, contentDescription = null,
                tint = if (active) AppContentColors.accent() else Color.White,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderButton(
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    gestureModifier: Modifier? = null,
    modifier: Modifier = Modifier,
    buttonSizeDp: Int? = null,
    content: @Composable () -> Unit,
) {
    val feedbackClick = appActionWithFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS, onClick)
    PlayerControlGlassDock(enabled = true, modifier = modifier, shape = CircleShape) {
        Box(
            modifier = Modifier
                .then(if (buttonSizeDp != null) {
                    Modifier.size(buttonSizeDp.dp)
                } else if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
                    Modifier.size(PlayerGlassIconButtonSize)
                } else {
                    Modifier.size(34.dp)
                })
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    selected = active
                    if (gestureModifier != null) {
                        role = Role.Button
                        onClick(action = { feedbackClick(); true })
                    }
                }
                .then(gestureModifier ?: Modifier.combinedClickable(
                    role = Role.Button, onClick = feedbackClick, onLongClick = onLongClick,
                )),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

private fun formatCurrentTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun resolveBatteryLevelPercent(context: Context): Int? = runCatching {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    if (level < 0 || scale <= 0) null else (level * 100f / scale).toInt().coerceIn(0, 100)
}.getOrNull()
