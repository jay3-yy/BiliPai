package com.android.purebilibili.feature.video.ui.overlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
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
import com.android.purebilibili.feature.video.ui.feedback.likeBurstAnchor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared Android PiliPlus HeaderControl for inline/fullscreen and portrait/landscape.
 * Each button owns its material; title, status and action rows remain uncontained.
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
    onBack: () -> Unit,
    isLiked: Boolean = false,
    likeStatusKnown: Boolean,
    isCoined: Boolean = false,
    allowDislikeAction: Boolean = true,
    onLikeClick: (() -> Unit)? = null,
    onDislikeClick: (() -> Unit)? = null,
    onCoinClick: (() -> Unit)? = null,
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
    onSponsorSubmitClick: (() -> Unit)? = null,
    showSponsorSegmentsButton: Boolean = false,
    onSponsorSegmentsClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onRelatedVideosClick: (() -> Unit)? = null,
    commentsVisible: Boolean = false,
    relatedVideosVisible: Boolean = false,
    displayCutoutInsets: WindowInsets = WindowInsets.displayCutout,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
    systemGestureInsets: WindowInsets = WindowInsets.systemGestures,
) {
    val policy = resolveTopControlBarLayoutPolicy(
        isPortrait = isPortrait,
        isFullscreen = isFullscreen,
        horizontalScreen = horizontalScreen,
        isFileSource = isFileSource,
        isUgc = isUgc,
        showFullscreenActionItems = showInteractiveActions,
    )
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val clockVisible = policy.showStatus && showCurrentTime
    val batteryVisible = policy.showStatus && showBatteryLevel
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
    val motionEnabled = LocalComponentMotionEnabled.current
    val hasTitle = policy.showTitle && title.isNotEmpty()
    val triplePress = rememberPiliHeaderTriplePressState()
    val likeGesture = if (likeStatusKnown && onLikeClick != null && onLikeLongClick != null) {
        piliHeaderLikeGesture(triplePress, isLiked && isCoined && isFavorited, onLikeClick, onLikeLongClick)
    } else null

    // Media and chrome can be siblings: consume remaining status-bar space here in every mode.
    val horizontalInsets = playerControlHorizontalInsets(
        isLandscape = !isPortrait,
        isFullscreen = isFullscreen,
        displayCutoutInsets = displayCutoutInsets,
        systemGestureInsets = systemGestureInsets,
    )
    val safeInsets = horizontalInsets.let { horizontal ->
        val cutout = if (isFullscreen) {
            horizontal.union(displayCutoutInsets.only(WindowInsetsSides.Top))
        } else horizontal
        if (statusBarVisible) {
            cutout.union(statusBarInsets.only(WindowInsetsSides.Top))
        } else cutout
    }

    PlayerControlTouchBounds(height = policy.buttonHeightDp.dp) {
        Column(
            modifier = modifier.fillMaxWidth()
                .windowInsetsPadding(safeInsets)
                .padding(vertical = policy.verticalPaddingDp.dp),
        ) {
            PiliHeaderSlots(
                hasTitle = hasTitle,
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeaderIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack, iconSize = 15)
                        if (policy.showHome && onHomeClick != null) {
                            HeaderIcon(Icons.Outlined.Home, "返回主页", onHomeClick, iconSize = 15)
                        }
                    }
                },
                right = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (batteryVisible) {
                            batteryPercent?.let { HeaderStatusText("$it%") }
                            Spacer(Modifier.width(10.dp))
                        }
                        if (clockVisible) HeaderStatusText(currentTime)
                        if (policy.showAudio && onAudioClick != null) {
                            HeaderIcon(Icons.Outlined.Headphones, "听音频", onAudioClick)
                        }
                        if (policy.showCast && showCastButton && onCastClick != null) {
                            HeaderIcon(Icons.Outlined.Cast, "投屏", onCastClick)
                        }
                        if (!isFileSource && showSponsorSubmitButton && onSponsorSubmitClick != null) {
                            HeaderButton("提交片段", onSponsorSubmitClick) {
                                PiliPlusDanmakuGlyph("\uE814", "提交片段", Color.White, 20.dp)
                            }
                        }
                        if (showSponsorSegmentsButton && onSponsorSegmentsClick != null) {
                            HeaderButton("片段信息", onSponsorSegmentsClick) { HeaderAdvertisementIcon() }
                        }
                        if (policy.showDanmakuActions) {
                            if (onSendDanmakuClick != null) {
                                HeaderIcon(Icons.Outlined.Comment, "发弹幕", onSendDanmakuClick)
                            }
                            if (onDanmakuToggle != null) {
                                val description = if (danmakuEnabled) "关闭弹幕" else "开启弹幕"
                                HeaderButton(description, onDanmakuToggle) {
                                    PiliPlusDanmakuGlyph(
                                        if (danmakuEnabled) "\uE803" else "\uE802",
                                        description, Color.White, 20.dp,
                                    )
                                }
                            }
                        }
                        if (onDanmakuSettingsClick != null) {
                            HeaderButton("弹幕设置", onDanmakuSettingsClick) {
                                PiliPlusDanmakuSettingsIcon(tint = Color.White, size = 20.dp)
                            }
                        }
                        if (showPipButton && onPipClick != null) {
                            HeaderIcon(Icons.Outlined.PictureInPicture, "画中画", onPipClick)
                        }
                        if (onMoreClick != null) {
                            HeaderIcon(Icons.Outlined.MoreVert, "更多设置", onMoreClick)
                        }
                    }
                },
                title = {
                    Column(
                        Modifier.padding(end = if (isPortrait) 0.dp else 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AppText(
                            text = title, color = Color.White, fontSize = policy.titleFontSp.sp, lineHeight = 19.sp,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            modifier = if (motionEnabled) Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE, spacing = androidx.compose.foundation.MarqueeSpacing(30.dp),
                                velocity = 30.dp,
                            ) else Modifier,
                            tapToCopyEnabled = false,
                        )
                        if (onlineCount.isNotBlank()) {
                            AppText(
                                text = onlineCount, color = Color.White,
                                fontSize = policy.onlineCountFontSp.sp, lineHeight = 13.sp, maxLines = 1,
                                softWrap = false, overflow = TextOverflow.Ellipsis,
                                tapToCopyEnabled = false,
                            )
                        }
                    }
                },
            )
            // 侧栏入口属于浏览功能，不随点赞、投币等互动快捷键一起隐藏。
            if (policy.showEngagement || onCommentClick != null || onRelatedVideosClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (onCommentClick != null) {
                        HeaderIcon(Icons.Outlined.Forum, "评论", onCommentClick,
                            active = commentsVisible, iconSize = 18)
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
                                    onLikeClick, active = likeStatusKnown && isLiked, iconSize = 18,
                                    gestureModifier = likeGesture, progress = triplePress.progress,
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
                            AppIcons.BiliCoin, "投币", onCoinClick, active = isCoined,
                            progress = triplePress.progress, iconSize = 18,
                        )
                        if (onFavoriteClick != null) {
                            HeaderIcon(
                                if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                "收藏", onFavoriteClick, active = isFavorited, iconSize = 18,
                                onLongClick = onFavoriteLongClick, progress = triplePress.progress,
                            )
                        }
                        if (onShareClick != null) HeaderIcon(Icons.Outlined.Share, "分享", onShareClick, iconSize = 18)
                    }
                }
            }
        }
    }
}

/** Natural-size edge groups leave exactly the remaining width to the scrolling title.
 * When an inline window cannot fit both edge groups, scale them together as PlayerBar does;
 * transformed placement keeps pointer hit testing in the same coordinates as the drawing.
 */
@Composable
private fun PiliHeaderSlots(
    hasTitle: Boolean,
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
        val titlePlaceable = if (hasTitle) measurables[2].measure(
            Constraints(maxWidth = (width - edgeWidth).coerceAtLeast(0), maxHeight = constraints.maxHeight),
        ) else null
        val height = constraints.constrainHeight(maxOf(
            leftPlaceable.height, rightPlaceable.height, titlePlaceable?.height ?: 0,
        ))
        layout(width, height) {
            if (scale < 1f) {
                leftPlaceable.placeRelativeWithLayer(0, ((height - leftPlaceable.height * scale) / 2).toInt()) {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                }
                rightPlaceable.placeRelativeWithLayer(
                    (leftPlaceable.width * scale).toInt(), ((height - rightPlaceable.height * scale) / 2).toInt(),
                ) {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                }
            } else {
                leftPlaceable.placeRelative(0, (height - leftPlaceable.height) / 2)
                rightPlaceable.placeRelative(width - rightPlaceable.width, (height - rightPlaceable.height) / 2)
            }
            titlePlaceable?.placeRelative(leftPlaceable.width, (height - titlePlaceable.height) / 2)
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
) {
    val progressColor = AppContentColors.accent()
    HeaderButton(description, onClick, active, onLongClick, gestureModifier) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderButton(
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    gestureModifier: Modifier? = null,
    content: @Composable () -> Unit,
) {
    PlayerControlSurface(shape = CircleShape) {
        Box(
            modifier = Modifier.size(width = 42.dp, height = 34.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    selected = active
                    if (gestureModifier != null) {
                        role = Role.Button
                        onClick(action = { onClick(); true })
                    }
                }
                .then(gestureModifier ?: Modifier.combinedClickable(
                    role = Role.Button, onClick = onClick, onLongClick = onLongClick,
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
