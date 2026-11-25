package com.android.purebilibili.feature.video.ui.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.android.purebilibili.core.store.LongPressSpeedSlideMode
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import kotlinx.coroutines.delay

// 独立的提示渲染边界，状态与手势处理仍由播放器持有；各材质沿用原有组件与布局。
@Composable
internal fun PlayerSeekPreview(
    visible: Boolean,
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData?,
    player: Player,
    isPortraitFullscreen: Boolean,
    seekTargetTime: Long,
    startPosition: Long,
    modifier: Modifier = Modifier,
) {
    if (visible) {
        // 🖼️ Seek 模式：显示带缩略图的预览气泡
        // zIndex must sit above forced return cover (100f) so landscape seek feedback is never buried.
        BoxWithConstraints(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            if (videoshotData != null && videoshotData.isValid) {
                if (isPortraitFullscreen) {
                    com.android.purebilibili.feature.video.ui.components.CompactSeekPreview(
                        videoshotData = videoshotData,
                        targetPositionMs = seekTargetTime,
                        durationMs = player.duration,
                        // 使用播放器实际视频宽比，避免横屏内容预览被压扁/裁切
                        videoAspectRatio = player.videoSize.takeIf { it.width > 0 && it.height > 0 }
                            ?.let { it.width.toFloat() / it.height.toFloat() }
                            ?: com.android.purebilibili.feature.video.ui.components.PORTRAIT_SEEK_PREVIEW_ASPECT_RATIO,
                        containerHeightDp = maxHeight.value.toInt()
                    )
                } else {
                    // 普通播放器保留横向预览；竖屏全屏统一使用大尺寸 9:16 预览。
                    com.android.purebilibili.feature.video.ui.components.SeekPreviewBubble(
                        videoshotData = videoshotData,
                        targetPositionMs = seekTargetTime,
                        currentPositionMs = startPosition,
                        durationMs = player.duration,
                        offsetX = 0f,
                        containerWidth = 0f,
                        placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Centered,
                        containerHeightDp = maxHeight.value.toInt(),
                    )
                }
            } else {
                com.android.purebilibili.feature.video.ui.components.SeekPreviewBubbleSimple(
                    targetPositionMs = seekTargetTime,
                    currentPositionMs = startPosition,
                    offsetX = 0f,
                    containerWidth = 0f,
                    placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Centered
                )
            }
        }
    }
}

@Composable
internal fun PlayerSeekCancelHint(
    visible: Boolean,
    gestureMotionSpec: VideoGestureMotionSpec,
    modifier: Modifier = Modifier,
) {
    //  Seek 逃生口提示：拖动进度时手指进入顶部角落，松手取消进退
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(gestureMotionSpec.orientationHintEnterFadeDurationMillis)
        ),
        exit = fadeOut(
            animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)
        )
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 4.dp
        ) {
            AppText(
                text = "松开手指，取消进退",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
internal fun PlayerOrientationHint(
    visible: Boolean,
    orientationHintText: String,
    gestureMotionSpec: VideoGestureMotionSpec,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(gestureMotionSpec.orientationHintEnterFadeDurationMillis)) +
            scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(gestureMotionSpec.orientationHintEnterTransformDurationMillis)
            ) +
            slideInVertically(
                initialOffsetY = { -it / 5 },
                animationSpec = tween(gestureMotionSpec.orientationHintEnterTransformDurationMillis)
            ),
        exit = fadeOut(animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)) +
            scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            AppText(
                text = orientationHintText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
internal fun PlayerSeekFeedback(
    visible: Boolean,
    seekFeedbackText: String?,
    uiLayoutPolicy: VideoPlayerUiLayoutPolicy,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(initialScale = 0.5f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(uiLayoutPolicy.seekFeedbackSizeDp.dp)
                .background(Color.Black.copy(0.75f), AppShapes.container(ContainerLevel.Floating)),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = seekFeedbackText ?: "",
                color = com.android.purebilibili.core.ui.MediaContrastPalette.Foreground,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
internal fun PlayerRestorePictureButton(
    visible: Boolean,
    uiLayoutPolicy: VideoPlayerUiLayoutPolicy,
    onRestore: () -> Unit,
    label: String = "还原画面",
    modifier: Modifier = Modifier,
) {
    //  [新增] 缩放还原按钮 (仅在放大时显示)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        AppButton(
            onClick = onRestore,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(
                horizontal = uiLayoutPolicy.restoreButtonHorizontalPaddingDp.dp,
                vertical = uiLayoutPolicy.restoreButtonVerticalPaddingDp.dp
            ),
            shape = AppShapes.container(ContainerLevel.Floating)
        ) {
            AppIcon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = label,
                modifier = Modifier.size(uiLayoutPolicy.restoreButtonIconSizeDp.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
internal fun PlayerSpeedLockZones(
    visible: Boolean,
    longPressSpeedLockSensitivity: LongPressSpeedLockSensitivityPolicy,
    gestureMotionSpec: VideoGestureMotionSpec,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)),
        exit = fadeOut(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val lockZoneVisual = resolveLongPressSpeedLockZoneVisualPolicy()
            val zoneModifier = Modifier
                .fillMaxWidth()
                .height(longPressSpeedLockSensitivity.lockZoneHeightDp.dp)
            val markerColor = AppContentColors.accent()
            Box(modifier = zoneModifier.align(Alignment.TopCenter)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(lockZoneVisual.edgeGradientHeightDp.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    markerColor.copy(alpha = lockZoneVisual.edgeGradientAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(lockZoneVisual.centerMarkerWidthFraction)
                        .height(lockZoneVisual.centerMarkerHeightDp.dp)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(markerColor.copy(alpha = lockZoneVisual.centerMarkerAlpha))
                )
            }
            Box(
                modifier = zoneModifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = lockZoneVisual.bottomVisualOffsetDp.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(lockZoneVisual.edgeGradientHeightDp.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    markerColor.copy(alpha = lockZoneVisual.edgeGradientAlpha)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(lockZoneVisual.centerMarkerWidthFraction)
                        .height(lockZoneVisual.centerMarkerHeightDp.dp)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(markerColor.copy(alpha = lockZoneVisual.centerMarkerAlpha))
                )
            }
        }
    }
}

/**
 * 带方向性光学模糊的倍速数值切换组件：
 * - 加速时旧值向上散焦淡出、新值从下方聚焦滑入；减速时反向运动；
 * - 全部逐帧位移、透明度、缩放与 RenderEffect 模糊半径均延后到 graphicsLayer 阶段读取，零逐帧重组；
 * - 减少动态效果或 Android 12 以下自动优雅降级，静止时无后台动画循环。
 */
@Composable
internal fun AnimatedBlurredSpeedText(
    speed: Float,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberSystemReduceMotion(),
) {
    val targetLabel = formatLongPressSpeedLabel(speed)
    var displayedLabel by remember { mutableStateOf(targetLabel) }
    var previousLabel by remember { mutableStateOf<String?>(null) }
    var lastSpeed by remember { mutableFloatStateOf(speed) }
    var direction by remember { mutableIntStateOf(1) }
    val progress = remember { Animatable(1f) }
    val blurCache = remember { BlurRenderEffectCache() }
    DisposableEffect(blurCache) {
        onDispose { blurCache.clear() }
    }
    LaunchedEffect(targetLabel, reduceMotion) {
        if (targetLabel == displayedLabel) return@LaunchedEffect
        val increasing = speed >= lastSpeed
        direction = if (increasing) 1 else -1
        lastSpeed = speed
        if (reduceMotion) {
            previousLabel = null
            displayedLabel = targetLabel
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        previousLabel = displayedLabel
        displayedLabel = targetLabel
        progress.snapTo(0f)
        val anchorHit = isLongPressSpeedAnchorTick(speed)
        if (anchorHit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 540f),
            )
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 155, easing = FastOutSlowInEasing),
            )
        }
        previousLabel = null
    }
    Box(
        modifier = modifier.animateContentSize(
            animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 560f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val outgoing = previousLabel
        if (outgoing != null) {
            AppText(
                text = outgoing,
                style = style,
                color = color,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val travelPx = 11.dp.toPx()
                    translationY = -direction * travelPx * p
                    alpha = (1f - p * 1.35f).coerceIn(0f, 1f)
                    val scale = 1f - 0.14f * p
                    scaleX = scale
                    scaleY = scale
                    renderEffect = if (reduceMotion) null else blurCache.resolve(5.5.dp.toPx() * p)
                },
            )
        }
        AppText(
            text = displayedLabel,
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val rawP = progress.value
                val p = rawP.coerceIn(0f, 1f)
                val travelPx = 11.dp.toPx()
                translationY = direction * travelPx * (1f - p)
                alpha = if (outgoing == null) 1f else ((p - 0.06f) / 0.78f).coerceIn(0f, 1f)
                val scale = if (outgoing == null) 1f else (0.88f + 0.12f * rawP).coerceIn(0.86f, 1.07f)
                scaleX = scale
                scaleY = scale
                renderEffect = if (reduceMotion || outgoing == null) {
                    null
                } else {
                    blurCache.resolve(5.5.dp.toPx() * (1f - p))
                }
            },
        )
    }
}

@Composable
private fun LongPressSpeedDirectionGlyph(
    speed: Float,
    locked: Boolean,
    manualFeedback: Boolean,
    tint: Color,
    scale: Float,
    active: Boolean,
) {
    if (locked) {
        AppIcon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp * scale),
        )
        return
    }
    if (manualFeedback) {
        AppIcon(
            imageVector = Icons.Outlined.Speed,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp * scale),
        )
        return
    }
    val gate = rememberDecorativeAnimationGate()
    val pulsePhase = remember { Animatable(0f) }
    val shouldPulse = active && gate.active
    LaunchedEffect(shouldPulse) {
        if (!shouldPulse) {
            pulsePhase.snapTo(0.45f)
            return@LaunchedEffect
        }
        while (true) {
            pulsePhase.snapTo(0f)
            pulsePhase.animateTo(1f, tween(durationMillis = 720, easing = LinearEasing))
        }
    }
    val isSlowMotion = speed < 1.0f - 0.01f
    val chevronPath = remember { Path() }
    Canvas(
        modifier = Modifier
            .size(width = 16.dp * scale, height = 13.dp * scale)
            .then(gate.modifier),
    ) {
        val phase = pulsePhase.value
        val w = size.width
        val h = size.height
        val strokePx = (1.8.dp * scale).toPx()
        val arrowW = w * 0.32f
        val arrowH = h * 0.34f
        val cy = h * 0.5f
        val dir = if (isSlowMotion) -1f else 1f
        val centers = if (isSlowMotion) floatArrayOf(w * 0.68f, w * 0.32f) else floatArrayOf(w * 0.32f, w * 0.68f)
        for (i in centers.indices) {
            val wave = ((phase - i * 0.35f) % 1f + 1f) % 1f
            val alpha = 0.38f + 0.62f * kotlin.math.sin(wave * Math.PI).toFloat().coerceIn(0f, 1f)
            val cx = centers[i] + dir * (wave - 0.5f) * (1.8.dp * scale).toPx()
            chevronPath.reset()
            chevronPath.moveTo(cx - dir * arrowW * 0.5f, cy - arrowH)
            chevronPath.lineTo(cx + dir * arrowW * 0.5f, cy)
            chevronPath.lineTo(cx - dir * arrowW * 0.5f, cy + arrowH)
            drawPath(
                path = chevronPath,
                color = tint.copy(alpha = alpha),
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun LongPressSlideSpeedRuler(
    speed: Float,
    mode: LongPressSpeedSlideMode,
    accentColor: Color,
    scale: Float,
    reduceMotion: Boolean,
) {
    val targetFraction = normalizeLongPressSpeedFraction(speed)
    val animatedFraction = animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.78f, stiffness = 540f),
        label = "longPressSpeedRulerFraction",
    )
    val isContinuous = mode == LongPressSpeedSlideMode.CONTINUOUS
    Column(
        modifier = Modifier
            .width(164.dp * scale)
            .padding(
                start = 10.dp * scale,
                end = 10.dp * scale,
                bottom = 6.dp * scale,
            ),
        verticalArrangement = Arrangement.spacedBy(3.dp * scale),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp * scale),
        ) {
            val trackH = (2.5.dp * scale).toPx()
            val cy = size.height * 0.5f
            val corner = CornerRadius(trackH * 0.5f, trackH * 0.5f)
            val frac = animatedFraction.value.coerceIn(0f, 1f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(0f, cy - trackH * 0.5f),
                size = Size(size.width, trackH),
                cornerRadius = corner,
            )
            // 锚点刻度（1.0x / 2.0x / 3.0x / 4.0x / 5.0x）
            val tickCount = if (isContinuous) 9 else LONG_PRESS_SLIDE_SPEED_PRESETS.size
            for (i in 0 until tickCount) {
                val tickFrac = if (isContinuous) {
                    (0.5f + i * 0.5f - LONG_PRESS_CONTINUOUS_SPEED_MIN) /
                        (LONG_PRESS_CONTINUOUS_SPEED_MAX - LONG_PRESS_CONTINUOUS_SPEED_MIN)
                } else {
                    normalizeLongPressSpeedFraction(LONG_PRESS_SLIDE_SPEED_PRESETS[i])
                }
                val tx = tickFrac.coerceIn(0f, 1f) * size.width
                drawCircle(
                    color = Color.White.copy(alpha = if (tickFrac <= frac) 0.75f else 0.32f),
                    radius = (1.4.dp * scale).toPx(),
                    center = Offset(tx, cy),
                )
            }
            val fillW = (size.width * frac).coerceAtLeast(trackH)
            drawRoundRect(
                color = accentColor.copy(alpha = 0.92f),
                topLeft = Offset(0f, cy - trackH * 0.5f),
                size = Size(fillW, trackH),
                cornerRadius = corner,
            )
            val thumbX = (size.width * frac).coerceIn(4.dp.toPx(), (size.width - 4.dp.toPx()).coerceAtLeast(4.dp.toPx()))
            drawCircle(
                color = accentColor.copy(alpha = 0.34f),
                radius = (5.2.dp * scale).toPx(),
                center = Offset(thumbX, cy),
            )
            drawCircle(
                color = Color.White,
                radius = (3.2.dp * scale).toPx(),
                center = Offset(thumbX, cy),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tinyStyle = MaterialTheme.typography.labelSmall.copy(
                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f * scale,
            )
            AppText(text = "0.5x", style = tinyStyle, color = Color.White.copy(alpha = 0.56f), maxLines = 1)
            AppText(
                text = if (isContinuous) "无极 ±0.1x" else "档位换挡",
                style = tinyStyle,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
            )
            AppText(text = "5.0x", style = tinyStyle, color = Color.White.copy(alpha = 0.56f), maxLines = 1)
        }
    }
}

@Composable
internal fun PlayerLongPressSpeedHint(
    visible: Boolean,
    longPressSpeedLocked: Boolean,
    effectiveLongPressSpeed: Float,
    longPressSpeedHintScale: Float,
    longPressSpeedHintAlpha: Float,
    longPressSpeedHintCloseEnabled: Boolean,
    gestureMotionSpec: VideoGestureMotionSpec,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isManualSwitchFeedback: Boolean = false,
    slideMode: LongPressSpeedSlideMode = LongPressSpeedSlideMode.OFF,
    isSlidingSpeed: Boolean = false,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val enterProgress = remember { Animatable(if (visible) 1f else 0f) }
    val containerBlurCache = remember { BlurRenderEffectCache() }
    DisposableEffect(containerBlurCache) {
        onDispose { containerBlurCache.clear() }
    }
    LaunchedEffect(visible, reduceMotion) {
        if (visible) {
            if (reduceMotion) {
                enterProgress.snapTo(1f)
            } else {
                enterProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 460f),
                )
            }
        } else {
            if (reduceMotion) {
                enterProgress.snapTo(0f)
            } else {
                enterProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = gestureMotionSpec.longPressHintDurationMillis.coerceIn(130, 210),
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }
    if (!visible && enterProgress.value <= 0.001f) return

    val isSlowMotion = effectiveLongPressSpeed < 1.0f - 0.01f
    val indicatorTint = when {
        longPressSpeedLocked -> AppContentColors.accent()
        isSlowMotion -> Color(0xFF7DD3FC)
        else -> AppContentColors.accent()
    }
    val shape = AppShapes.scaledContainer(ContainerLevel.Field, longPressSpeedHintScale)
    val closeVisible = !isManualSwitchFeedback && shouldShowLongPressSpeedHintCloseButton(longPressSpeedHintCloseEnabled)
    val statusText = when {
        isManualSwitchFeedback -> "倍速"
        longPressSpeedLocked -> "已锁定"
        isSlowMotion -> "慢速播放"
        else -> "倍速播放"
    }

    Box(
        modifier = modifier.graphicsLayer {
            val rawP = enterProgress.value
            val p = rawP.coerceIn(0f, 1f)
            alpha = p
            val scale = (0.86f + 0.14f * rawP).coerceIn(0.84f, 1.04f)
            scaleX = scale
            scaleY = scale
            translationY = -14.dp.toPx() * (1f - p)
            renderEffect = if (reduceMotion) {
                null
            } else {
                val maxBlurDp = if (visible) 8.dp else 6.dp
                containerBlurCache.resolve((1f - p) * maxBlurDp.toPx())
            }
        },
    ) {
        AppSurface(
            shape = shape,
            color = Color.Black.copy(alpha = (longPressSpeedHintAlpha * 1.12f).coerceIn(0.32f, 0.90f)),
            contentColor = Color.White,
            tonalElevation = 0.dp,
            modifier = Modifier.border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = shape,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp * longPressSpeedHintScale),
                    modifier = Modifier.padding(
                        start = 10.dp * longPressSpeedHintScale,
                        end = if (closeVisible) 2.dp else 10.dp * longPressSpeedHintScale,
                        top = 6.dp * longPressSpeedHintScale,
                        bottom = 6.dp * longPressSpeedHintScale,
                    ),
                ) {
                    LongPressSpeedDirectionGlyph(
                        speed = effectiveLongPressSpeed,
                        locked = longPressSpeedLocked,
                        manualFeedback = isManualSwitchFeedback,
                        tint = indicatorTint,
                        scale = longPressSpeedHintScale,
                        active = visible && !longPressSpeedLocked && !isManualSwitchFeedback,
                    )
                    AppText(
                        text = statusText,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * longPressSpeedHintScale,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    AnimatedBlurredSpeedText(
                        speed = effectiveLongPressSpeed,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * longPressSpeedHintScale,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                        reduceMotion = reduceMotion,
                    )
                    if (closeVisible) {
                        AppIconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp * longPressSpeedHintScale),
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭倍速提示",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp * longPressSpeedHintScale),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = slideMode != LongPressSpeedSlideMode.OFF &&
                        !longPressSpeedLocked &&
                        !isManualSwitchFeedback &&
                        isSlidingSpeed,
                    enter = fadeIn(tween(140)) + expandVertically(tween(170)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
                ) {
                    LongPressSlideSpeedRuler(
                        speed = effectiveLongPressSpeed,
                        mode = slideMode,
                        accentColor = indicatorTint,
                        scale = longPressSpeedHintScale,
                        reduceMotion = reduceMotion,
                    )
                }
            }
        }
    }
}

/** 聚合长按无极调速与手动切换倍速的会话状态，避免主播放器函数膨胀超过 JVM 方法字节码上限。 */
internal class LongPressSlideSpeedSessionState(
    initialSpeed: Float,
    initialObservedSpeed: Float,
) {
    var isSlidingSpeed by mutableStateOf(false)
    var lastAppliedSpeed by mutableFloatStateOf(initialSpeed)
    var lastAppliedAtMs by mutableLongStateOf(0L)
    var lastContinuousHapticAtMs by mutableLongStateOf(0L)
    var manualFeedbackVisible by mutableStateOf(false)
    var manualFeedbackSpeed by mutableFloatStateOf(initialObservedSpeed)
    var manualFeedbackGeneration by mutableLongStateOf(0L)
    var lastObservedManualSpeed by mutableFloatStateOf(initialObservedSpeed)
    var hasObservedInitialManualSpeed by mutableStateOf(false)

    fun onLongPressStart(appliedSpeed: Float, nowMs: Long) {
        isSlidingSpeed = false
        lastAppliedSpeed = appliedSpeed
        lastAppliedAtMs = nowMs
        manualFeedbackVisible = false
    }

    fun onLongPressEnd() {
        isSlidingSpeed = false
    }

    fun flushPendingSpeedIfNeeded(currentSpeed: Float, applySpeed: (Float) -> Unit) {
        isSlidingSpeed = false
        if (kotlin.math.abs(currentSpeed - lastAppliedSpeed) > 0.001f) {
            applySpeed(currentSpeed)
            lastAppliedSpeed = currentSpeed
        }
    }

    fun handleHorizontalSlide(
        mode: LongPressSpeedSlideMode,
        baseSpeed: Float,
        currentSpeed: Float,
        accumulatedDragXPx: Float,
        deadzonePx: Float,
        continuousStepPx: Float,
        presetStepPx: Float,
        nowMs: Long,
        haptic: HapticFeedback,
        onSpeedChanged: (Float) -> Unit,
        applySpeed: (Float) -> Unit,
    ) {
        if (mode == LongPressSpeedSlideMode.OFF) return
        val isContinuous = mode == LongPressSpeedSlideMode.CONTINUOUS
        val slideSpeed = if (isContinuous) {
            resolveLongPressContinuousSlideSpeed(
                initialSpeed = baseSpeed,
                accumulatedDragXPx = accumulatedDragXPx,
                deadzonePx = deadzonePx,
                stepPx = continuousStepPx,
            )
        } else {
            resolveLongPressSlideSpeed(
                initialSpeed = baseSpeed,
                accumulatedDragXPx = accumulatedDragXPx,
                stepPx = presetStepPx,
            )
        }
        if (kotlin.math.abs(accumulatedDragXPx) > deadzonePx) {
            isSlidingSpeed = true
        }
        if (kotlin.math.abs(slideSpeed - currentSpeed) > 0.001f) {
            onSpeedChanged(slideSpeed)
            val anchorHit = isLongPressSpeedAnchorTick(slideSpeed)
            if (!isContinuous || anchorHit ||
                nowMs - lastAppliedAtMs >= LONG_PRESS_CONTINUOUS_SPEED_APPLY_THROTTLE_MS
            ) {
                applySpeed(slideSpeed)
                lastAppliedSpeed = slideSpeed
                lastAppliedAtMs = nowMs
            }
            if (!isContinuous || anchorHit ||
                nowMs - lastContinuousHapticAtMs >= LONG_PRESS_CONTINUOUS_SPEED_HAPTIC_THROTTLE_MS
            ) {
                lastContinuousHapticAtMs = nowMs
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

@Composable
internal fun BindLongPressSlideSpeedEffects(
    session: LongPressSlideSpeedSessionState,
    isLongPressing: Boolean,
    longPressSpeedLocked: Boolean,
    slideMode: LongPressSpeedSlideMode,
    effectiveLongPressSpeed: Float,
    observedPlaybackSpeed: Float,
    onApplySpeed: (Float) -> Unit,
) {
    LaunchedEffect(session.manualFeedbackGeneration) {
        if (session.manualFeedbackGeneration > 0L) {
            delay(950L)
            session.manualFeedbackVisible = false
        }
    }
    LaunchedEffect(
        isLongPressing,
        longPressSpeedLocked,
        slideMode,
        effectiveLongPressSpeed,
    ) {
        if (
            isLongPressing &&
            !longPressSpeedLocked &&
            slideMode == LongPressSpeedSlideMode.CONTINUOUS &&
            kotlin.math.abs(effectiveLongPressSpeed - session.lastAppliedSpeed) > 0.001f
        ) {
            delay(LONG_PRESS_CONTINUOUS_SPEED_APPLY_THROTTLE_MS)
            if (
                isLongPressing &&
                !longPressSpeedLocked &&
                kotlin.math.abs(effectiveLongPressSpeed - session.lastAppliedSpeed) > 0.001f
            ) {
                onApplySpeed(effectiveLongPressSpeed)
                session.lastAppliedSpeed = effectiveLongPressSpeed
                session.lastAppliedAtMs = android.os.SystemClock.elapsedRealtime()
            }
        }
    }
    LaunchedEffect(observedPlaybackSpeed, isLongPressing, longPressSpeedLocked) {
        if (!session.hasObservedInitialManualSpeed) {
            session.hasObservedInitialManualSpeed = true
            session.lastObservedManualSpeed = observedPlaybackSpeed
            return@LaunchedEffect
        }
        if (isLongPressing || longPressSpeedLocked) {
            session.lastObservedManualSpeed = observedPlaybackSpeed
            session.manualFeedbackVisible = false
            return@LaunchedEffect
        }
        if (kotlin.math.abs(observedPlaybackSpeed - session.lastObservedManualSpeed) > 0.01f) {
            session.lastObservedManualSpeed = observedPlaybackSpeed
            session.manualFeedbackSpeed = observedPlaybackSpeed
            session.manualFeedbackVisible = true
            session.manualFeedbackGeneration += 1L
        }
    }
}

@Composable
internal fun PlayerLongPressSpeedLockPrompt(
    visible: Boolean,
    hintScale: Float,
    hintAlpha: Float,
    gestureMotionSpec: VideoGestureMotionSpec,
    onEnableLock: () -> Unit,
    onDismissForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)) +
            slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)),
    ) {
        AppSurface(
            shape = AppShapes.scaledContainer(ContainerLevel.Floating, hintScale),
            color = Color.Black.copy(alpha = (0.62f * hintAlpha).coerceIn(0f, 1f)),
            contentColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    horizontal = 12.dp * hintScale,
                    vertical = 8.dp * hintScale,
                ),
            ) {
                AppText(
                    text = "需要长按锁定倍速吗？",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * hintScale,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextButton(
                        onClick = onEnableLock,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        AppText("开启锁定")
                    }
                    AppTextButton(
                        onClick = onDismissForever,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        AppText("不再提示")
                    }
                }
            }
        }
    }
}
