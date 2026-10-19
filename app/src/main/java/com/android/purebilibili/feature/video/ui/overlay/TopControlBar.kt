// File: feature/video/ui/overlay/TopControlBar.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.MediaContrastPalette

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.android.purebilibili.feature.video.ambient.LocalAmbientPresentation
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.components.appPressHighlightPressSpec
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import com.android.purebilibili.core.ui.AppIcons
import com.android.purebilibili.feature.video.ui.feedback.likeBurstAnchor
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Comment
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun shouldShowDislikeInTopControlBar(widthDp: Int): Boolean = widthDp >= 980
internal fun shouldShowInteractiveActionsInTopControlBar(
    showFullscreenActionItems: Boolean
): Boolean = showFullscreenActionItems
/**
 * 顶栏是否加状态栏 padding。
 *
 * 以 [statusBarVisible] 为准；全屏时系统栏通常已隐藏，应传 false。
 * [isFullscreen] 仅作兼容旧调用：未显式传 statusBarVisible 时，全屏仍 pad
 *（隐藏栏时 inset 为 0，无视觉影响）。
 */
internal fun shouldApplyStatusBarPaddingToTopControlBar(
    isFullscreen: Boolean = false,
    statusBarVisible: Boolean = isFullscreen,
): Boolean = com.android.purebilibili.feature.video.screen
    .shouldApplyStatusBarPaddingToVideoPlayerChrome(statusBarVisible = statusBarVisible)
internal fun shouldPollTopControlBarClock(
    showCurrentTime: Boolean,
    hostLifecycleStarted: Boolean
): Boolean = showCurrentTime && hostLifecycleStarted
internal fun shouldPollTopControlBarBattery(
    showBatteryLevel: Boolean,
    hostLifecycleStarted: Boolean
): Boolean = showBatteryLevel && hostLifecycleStarted

internal enum class BatteryChargeTone {
    CRITICAL,
    LOW,
    NORMAL
}

internal data class LandscapeBatteryVisualState(
    val percent: Int,
    val fillFraction: Float,
    val chargeTone: BatteryChargeTone
) {
    val displayText: String
        get() = "$percent%"
}

internal fun resolveLandscapeBatteryVisualState(percent: Int?): LandscapeBatteryVisualState? {
    val normalizedPercent = percent?.coerceIn(0, 100) ?: return null
    val chargeTone = when {
        normalizedPercent <= 15 -> BatteryChargeTone.CRITICAL
        normalizedPercent <= 35 -> BatteryChargeTone.LOW
        else -> BatteryChargeTone.NORMAL
    }
    return LandscapeBatteryVisualState(
        percent = normalizedPercent,
        fillFraction = normalizedPercent / 100f,
        chargeTone = chargeTone
    )
}

internal data class LandscapeTopStatusInfo(
    val battery: LandscapeBatteryVisualState? = null,
    val currentTimeText: String? = null
) {
    val isVisible: Boolean
        get() = battery != null || !currentTimeText.isNullOrBlank()
}

internal fun resolveLandscapeTopStatusInfo(
    showBatteryLevel: Boolean,
    batteryLevelPercent: Int?,
    showCurrentTime: Boolean,
    currentTimeText: String
): LandscapeTopStatusInfo {
    return LandscapeTopStatusInfo(
        battery = if (showBatteryLevel) resolveLandscapeBatteryVisualState(batteryLevelPercent) else null,
        currentTimeText = currentTimeText.takeIf { showCurrentTime }
    )
}

internal fun shouldShowLandscapeMetaRow(
    hasStatusInfo: Boolean,
    onlineCount: String
): Boolean = hasStatusInfo || onlineCount.isNotBlank()

/**
 * Top Control Bar Component
 *
 * Redesigned to match official Bilibili landscape layout:
 * - Left: Back button, bounded fullscreen title/status dock; inline title marquee
 * - Right: Action buttons (Like, Dislike, Coin, Share, Cast, More)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopControlBar(
    title: String,
    onlineCount: String = "",
    isFullscreen: Boolean,
    /** 系统状态栏是否仍显示；显示时顶栏必须 statusBarsPadding。 */
    statusBarVisible: Boolean = isFullscreen,
    showBatteryLevel: Boolean = false,
    showCurrentTime: Boolean = true,
    showInteractiveActions: Boolean = true,
    onBack: () -> Unit,
    // Interactions
    isLiked: Boolean = false,
    likeStatusKnown: Boolean,
    isCoined: Boolean = false,
    allowDislikeAction: Boolean = true,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onCoinClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onCastClick: () -> Unit = {}, // Added Cast callback
    showCastButton: Boolean = true,
    onMoreClick: () -> Unit = {},
    isLoggedIn: Boolean = true,
    backButtonStartPaddingDp: Int? = null,
    actionEndPaddingDp: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayCutoutInsets = WindowInsets.displayCutout
    val configuration = LocalConfiguration.current
    val ambient = LocalAmbientPresentation.current
    var rowOrigin by remember { mutableStateOf<Offset?>(null) }
    val videoContentBounds = remember(ambient) {
        derivedStateOf {
            ambient?.current?.contentBoundsInWindow ?: ambient?.videoBoundsInWindow?.invoke()
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val layoutPolicy = remember(configuration.screenWidthDp, isFullscreen) {
        val base = resolveTopControlBarLayoutPolicy(widthDp = configuration.screenWidthDp)
        if (isFullscreen) base.copy(
            horizontalPaddingDp = PlayerChromeLayout.EdgePaddingDp,
            verticalPaddingDp = 8,
            buttonSizeDp = PlayerChromeLayout.CircularButtonSizeDp,
            iconSizeDp = 24,
            titleFontSp = 15,
            timeFontSp = 12,
            actionSpacingDp = PlayerChromeLayout.ActionGapDp,
            backToTitleSpacingDp = PlayerChromeLayout.ActionGapDp,
            onlineCountStartPaddingDp = 60,
        ) else base
    }
    val showDislikeAction = remember(configuration.screenWidthDp, allowDislikeAction, showInteractiveActions) {
        allowDislikeAction &&
            showInteractiveActions &&
            shouldShowDislikeInTopControlBar(widthDp = configuration.screenWidthDp)
    }
    val showInteractiveActionGroup = remember(showInteractiveActions) {
        shouldShowInteractiveActionsInTopControlBar(
            showFullscreenActionItems = showInteractiveActions
        )
    }
    val currentTimeText by produceState(initialValue = formatCurrentTime(), showCurrentTime, hostLifecycleStarted) {
        if (!shouldPollTopControlBarClock(showCurrentTime, hostLifecycleStarted)) {
            value = formatCurrentTime()
            return@produceState
        }
        while (true) {
            value = formatCurrentTime()
            val now = System.currentTimeMillis()
            val nextMinuteDelay = (60_000L - (now % 60_000L)).coerceAtLeast(1_000L)
            delay(nextMinuteDelay)
        }
    }
    val batteryLevelPercent by produceState<Int?>(initialValue = null, showBatteryLevel, hostLifecycleStarted) {
        if (!shouldPollTopControlBarBattery(showBatteryLevel, hostLifecycleStarted)) {
            value = null
            return@produceState
        }
        while (true) {
            value = resolveBatteryLevelPercent(context)
            delay(30_000L)
        }
    }
    val statusInfo = remember(currentTimeText, batteryLevelPercent, showBatteryLevel, showCurrentTime) {
        resolveLandscapeTopStatusInfo(
            showBatteryLevel = showBatteryLevel,
            batteryLevelPercent = batteryLevelPercent,
            showCurrentTime = showCurrentTime,
            currentTimeText = currentTimeText
        )
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val leftSectionStartOffset = if (isFullscreen && backButtonStartPaddingDp != null) {
        val cutoutStart = with(density) {
            displayCutoutInsets.getLeft(density, layoutDirection).toDp()
        }
        backButtonStartPaddingDp.dp - cutoutStart - layoutPolicy.horizontalPaddingDp.dp
    } else 0.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)) else Modifier)
            .then(
                if (
                    shouldApplyStatusBarPaddingToTopControlBar(
                        isFullscreen = isFullscreen,
                        statusBarVisible = statusBarVisible,
                    )
                ) {
                    Modifier.statusBarsPadding()
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = layoutPolicy.horizontalPaddingDp.dp,
                vertical = layoutPolicy.verticalPaddingDp.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowOrigin = it.positionInWindow() }
                .layout { measurable, constraints ->
                    val bounds = videoContentBounds.value
                    val origin = rowOrigin
                    val alignedEndInset = if (isFullscreen && actionEndPaddingDp != null) {
                        (actionEndPaddingDp.dp.roundToPx() -
                            displayCutoutInsets.getRight(this, layoutDirection) -
                            layoutPolicy.horizontalPaddingDp.dp.roundToPx()).coerceAtLeast(0)
                    } else 0
                    val videoInset = if (isFullscreen && bounds != null && origin != null && constraints.hasBoundedWidth) {
                        resolveTopActionsVideoInsetPx(
                            rowLeftPx = origin.x,
                            rowWidthPx = constraints.maxWidth,
                            videoRightPx = bounds.right,
                            clearancePx = 12.dp.roundToPx(),
                        )
                    } else 0
                    val inset = maxOf(alignedEndInset, videoInset)
                    val width = (constraints.maxWidth - inset).coerceAtLeast(0)
                    val childConstraints = if (inset > 0) constraints.copy(
                        minWidth = (constraints.minWidth - inset).coerceAtLeast(0),
                        maxWidth = width,
                    ) else constraints
                    val placeable = measurable.measure(childConstraints)
                    layout(placeable.width + inset, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Left Section: Back & Info ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .absoluteOffset(x = leftSectionStartOffset)
            ) {
                // Back Button
                PlayerControlGlassDock(
                    enabled = isFullscreen,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ) {
                    AppIconButton(
                        onClick = onBack,
                        modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                    ) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(layoutPolicy.backToTitleSpacingDp.dp))

                if (isFullscreen) {
                    AdaptivePlayerTitleInfoDock(
                        title = title,
                        statusInfo = statusInfo,
                        onlineCount = onlineCount,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // 标题与右侧图标保持同一行
                    AppText(
                        text = title,
                        color = Color.White,
                        fontSize = layoutPolicy.titleFontSp.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee()
                    )
                }
            }

            Spacer(modifier = Modifier.width(layoutPolicy.sectionGapDp.dp)) // Space between text and actions

            // --- Right Section: Actions ---
            PlayerControlGlassDock(
                enabled = isFullscreen,
                modifier = (if (isFullscreen) Modifier.layout { measurable, constraints ->
                    // Reserve the back button and minimum title dock before measuring actions.
                    val reservedWidth = (layoutPolicy.buttonSizeDp * 2 +
                        layoutPolicy.backToTitleSpacingDp + layoutPolicy.sectionGapDp).dp.roundToPx()
                    val actionWidth = (constraints.maxWidth - reservedWidth).coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = actionWidth))
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                } else Modifier),
            ) {
                Row(
                    modifier = if (isFullscreen) Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                    else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layoutPolicy.actionSpacingDp.dp)
                ) {
                    if (showInteractiveActionGroup) {
                        // Like
                        Box(modifier = Modifier.likeBurstAnchor()) {
                            ActionIcon(
                                icon = if (likeStatusKnown && isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = if (likeStatusKnown) "点赞" else "点赞状态尚未确认",
                                isActive = likeStatusKnown && isLiked,
                                onClick = onLikeClick,
                                buttonSizeDp = layoutPolicy.buttonSizeDp,
                                iconSizeDp = layoutPolicy.iconSizeDp
                            )
                        }

                        if (showDislikeAction) {
                            // Dislike
                            ActionIcon(
                                icon = Icons.Outlined.ThumbDown,
                                contentDescription = "不喜欢",
                                isActive = false,
                                onClick = onDislikeClick,
                                buttonSizeDp = layoutPolicy.buttonSizeDp,
                                iconSizeDp = layoutPolicy.iconSizeDp
                            )
                        }

                        // Coin
                        ActionIcon(
                            icon = AppIcons.BiliCoin,
                            contentDescription = "投币",
                            isActive = isCoined,
                            onClick = onCoinClick,
                            buttonSizeDp = layoutPolicy.buttonSizeDp,
                            iconSizeDp = layoutPolicy.iconSizeDp
                        )

                        // Share
                        ActionIcon(
                            icon = Icons.Outlined.Share,
                            contentDescription = "分享",
                            isActive = false,
                            onClick = onShareClick,
                            buttonSizeDp = layoutPolicy.buttonSizeDp,
                            iconSizeDp = layoutPolicy.iconSizeDp
                        )
                    }

                    if (showCastButton) {
                        ActionIcon(
                            icon = Icons.Outlined.Cast,
                            contentDescription = "投屏",
                            isActive = false,
                            onClick = onCastClick,
                            buttonSizeDp = layoutPolicy.buttonSizeDp,
                            iconSizeDp = layoutPolicy.iconSizeDp
                        )
                    }

                    ActionIcon(
                        icon = Icons.Outlined.Comment,
                        contentDescription = "评论区",
                        isActive = false,
                        onClick = onCommentClick,
                        buttonSizeDp = layoutPolicy.buttonSizeDp,
                        iconSizeDp = layoutPolicy.iconSizeDp
                    )

                    // More (Three dots)
                    AppIconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(layoutPolicy.buttonSizeDp.dp)
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多",
                            tint = Color.White,
                            modifier = Modifier.size(layoutPolicy.iconSizeDp.dp)
                        )
                    }
                }
            }
        }

        if (!isFullscreen && shouldShowLandscapeMetaRow(statusInfo.isVisible, onlineCount)) {
            Column(
                modifier = Modifier.padding(
                    start = layoutPolicy.onlineCountStartPaddingDp.dp,
                    top = layoutPolicy.timeBottomSpacingDp.dp
                ),
                verticalArrangement = Arrangement.spacedBy(layoutPolicy.onlineCountTopPaddingDp.dp)
            ) {
                statusInfo.battery?.let { battery ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BatteryStatusIcon(
                            state = battery,
                            modifier = Modifier.size(
                                width = (layoutPolicy.timeFontSp + 14).dp,
                                height = (layoutPolicy.timeFontSp + 4).dp
                            )
                        )
                        AppText(
                            text = battery.displayText,
                            color = resolveBatteryStatusTint(battery).copy(alpha = 0.95f),
                            fontSize = layoutPolicy.timeFontSp.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        statusInfo.currentTimeText?.let { timeText ->
                            AppText(
                                text = timeText,
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = layoutPolicy.timeFontSp.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } ?: statusInfo.currentTimeText?.let { timeText ->
                    AppText(
                        text = timeText,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = layoutPolicy.timeFontSp.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (onlineCount.isNotEmpty()) {
                    AppText(
                        text = onlineCount,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = layoutPolicy.onlineCountFontSp.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Collapse the whole horizontal title capsule before the action dock squeezes its content. */
@Composable
private fun AdaptivePlayerTitleInfoDock(
    title: String,
    statusInfo: LandscapeTopStatusInfo,
    onlineCount: String,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = LocalComponentMotionEnabled.current
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(
        modifier = modifier.heightIn(min = 48.dp),
        contentAlignment = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        // Use the actual player slot: side panes can leave much less room than the window.
        // Keep a circular glass affordance at the collapsed endpoint so the title does not
        // vanish into an unexplained gap beside the back button.
        val hasRoom = maxWidth >= 160.dp
        val collapsedSize = PlayerChromeLayout.CircularButtonSizeDp.dp
        val targetWidth = if (hasRoom) maxWidth.coerceAtMost(420.dp) else collapsedSize
        val animatedWidth = animateFloatAsState(
            targetValue = targetWidth.value,
            animationSpec = if (motionEnabled) appPressHighlightPressSpec() else snap(),
            label = "playerTitleHorizontalWidth",
        )
        val expandedFraction = animateFloatAsState(
            targetValue = if (hasRoom) 1f else 0f,
            animationSpec = if (motionEnabled) appPressHighlightPressSpec() else snap(),
            label = "playerTitleHorizontalContent",
        )
        val dockWidth = animatedWidth.value
            .coerceIn(collapsedSize.value, maxWidth.coerceAtLeast(collapsedSize).value)
            .dp
        Box(
            modifier = Modifier
                .width(dockWidth)
                // The dock owns its rounded shell and elastic transform. Clipping this
                // stationary slot cuts off the capsule and labels during either drag axis.
                .heightIn(min = 48.dp),
            contentAlignment = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                Alignment.CenterEnd
            } else {
                Alignment.CenterStart
            },
        ) {
            PlayerTitleInfoDock(
                title = title,
                statusInfo = statusInfo,
                onlineCount = onlineCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = expandedFraction.value.coerceIn(0f, 1f) },
            )
            PlayerControlGlassDock(
                enabled = true,
                modifier = Modifier
                    .requiredSize(collapsedSize)
                    .graphicsLayer { alpha = (1f - expandedFraction.value).coerceIn(0f, 1f) },
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "视频标题",
                        tint = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * A finite width keeps the title from displacing the action dock. Long titles scroll inside that
 * width while motion is enabled (the pre-liquid-glass behaviour), and fall back to a single-line
 * ellipsis when motion is off.
 */
@Composable
private fun PlayerTitleInfoDock(
    title: String,
    statusInfo: LandscapeTopStatusInfo,
    onlineCount: String,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = LocalComponentMotionEnabled.current
    PlayerControlGlassDock(
        enabled = true,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                // 长标题在胶囊宽度内滚动；关闭动效后回到单行省略号，保持原有静止行为。
                modifier = if (motionEnabled) Modifier.basicMarquee() else Modifier,
            )
            if (shouldShowLandscapeMetaRow(statusInfo.isVisible, onlineCount)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    statusInfo.battery?.let { battery ->
                        BatteryStatusIcon(
                            state = battery,
                            modifier = Modifier.size(width = 22.dp, height = 13.dp),
                        )
                        AppText(
                            text = battery.displayText,
                            color = resolveBatteryStatusTint(battery).copy(alpha = 0.95f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    statusInfo.currentTimeText?.let { time ->
                        AppText(
                            text = time,
                            color = Color.White.copy(alpha = 0.86f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (onlineCount.isNotBlank()) {
                        AppText(
                            text = onlineCount,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

private fun formatCurrentTime(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date())
}

private fun resolveBatteryLevelPercent(context: Context): Int? {
    return runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return@runCatching null
        (level * 100f / scale).toInt().coerceIn(0, 100)
    }.getOrNull()
}

@Composable
private fun BatteryStatusIcon(
    state: LandscapeBatteryVisualState,
    modifier: Modifier = Modifier
) {
    val tint = resolveBatteryStatusTint(state)
    Canvas(modifier = modifier) {
        val strokeWidth = size.height * 0.12f
        val terminalWidth = size.width * 0.09f
        val bodyWidth = size.width - terminalWidth - strokeWidth
        val bodyHeight = size.height * 0.76f
        val bodyTop = (size.height - bodyHeight) / 2f
        val cornerRadius = CornerRadius(bodyHeight * 0.18f, bodyHeight * 0.18f)
        val innerPadding = strokeWidth * 1.1f
        val fillWidth = ((bodyWidth - innerPadding * 2f) * state.fillFraction).coerceAtLeast(0f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(0f, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(bodyWidth + strokeWidth * 0.35f, size.height * 0.34f),
            size = Size(terminalWidth, size.height * 0.28f),
            cornerRadius = CornerRadius(terminalWidth / 2f, terminalWidth / 2f)
        )
        if (fillWidth > 0f) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(innerPadding, bodyTop + innerPadding),
                size = Size(fillWidth, bodyHeight - innerPadding * 2f),
                cornerRadius = CornerRadius(cornerRadius.x / 1.5f, cornerRadius.y / 1.5f)
            )
        }
    }
}

private fun resolveBatteryStatusTint(
    state: LandscapeBatteryVisualState
): Color {
    return when (state.chargeTone) {
        BatteryChargeTone.CRITICAL -> MediaContrastPalette.Error
        BatteryChargeTone.LOW -> MediaContrastPalette.Warning
        BatteryChargeTone.NORMAL -> Color.White.copy(alpha = 0.92f)
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    buttonSizeDp: Int = 32,
    iconSizeDp: Int = 24,
    enabled: Boolean = true
) {
    AppIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSizeDp.dp)
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (!enabled) Color.White.copy(alpha = 0.38f) else if (isActive) AppContentColors.accent() else Color.White,
            modifier = Modifier.size(iconSizeDp.dp)
        )
    }
}

/** Reserve the real video's right black bar plus a small margin for glass refraction. */
internal fun resolveTopActionsVideoInsetPx(
    rowLeftPx: Float,
    rowWidthPx: Int,
    videoRightPx: Float,
    clearancePx: Int,
): Int = kotlin.math.ceil((rowLeftPx + rowWidthPx - videoRightPx + clearancePx).toDouble())
    .toInt()
    .coerceIn(0, (rowWidthPx * 0.4f).toInt().coerceAtLeast(0))
