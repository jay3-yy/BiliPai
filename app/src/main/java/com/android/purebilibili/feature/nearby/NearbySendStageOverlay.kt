package com.android.purebilibili.feature.nearby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppPopupSurface
import com.android.purebilibili.core.ui.AppPopupSurfaceType
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private val NearbyGestureFlingEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val NearbyButtonLaunchEasing = CubicBezierEasing(0.32f, 0f, 0.18f, 1f)

internal data class NearbyStageLaunchFrame(
    val translationYPx: Float,
    val scale: Float,
    val alpha: Float,
    val rotationXDeg: Float,
    val blurFraction: Float,
    val scrimAlpha: Float,
    val backdropBlurFraction: Float,
    val ambientGlowAlpha: Float,
    val haloProgress: Float,
    val haloAlpha: Float,
    val targetPillImpactScale: Float,
)

/**
 * 双曲橡皮筋阻尼：未超过柔和区（60% 限幅）时 1:1 线性跟手，越接近极限阻力越强，消除硬截断撞墙感。
 */
internal fun applyNearbyRubberBand(rawOffsetPx: Float, limitPx: Float): Float {
    val maxLimit = abs(limitPx)
    if (maxLimit <= 0f) return 0f
    val absRaw = abs(rawOffsetPx)
    val softZone = maxLimit * 0.62f
    if (absRaw <= softZone) return rawOffsetPx
    val excess = absRaw - softZone
    val remaining = maxLimit - softZone
    val damped = softZone + remaining * (1f - 1f / (1f + excess / remaining))
    return damped * sign(rawOffsetPx)
}

/** 同时支持「位移过阈值」与「向上快速一甩（flick）」触发发送。 */
internal fun shouldTriggerNearbyFlingSend(
    dragYPx: Float,
    velocityYPxPerSec: Float,
    distanceThresholdPx: Float,
    velocityThresholdPxPerSec: Float,
    minFlingDistancePx: Float,
): Boolean {
    if (dragYPx <= -distanceThresholdPx) return true
    return dragYPx <= -minFlingDistancePx && velocityYPxPerSec <= -velocityThresholdPxPerSec
}

/** 甩出速度越快，起飞收束时长越短（210..310ms），保证物理速度连续无断档。 */
internal fun resolveNearbyLaunchDurationMs(
    velocityYPxPerSec: Float,
    fromButton: Boolean,
): Int {
    if (fromButton) return 310
    val speed = abs(velocityYPxPerSec).coerceIn(0f, 3600f)
    val factor = (speed / 3600f).coerceIn(0f, 1f)
    return (300f - 90f * factor).roundToInt().coerceIn(210, 310)
}

/**
 * 从当前手势松手位置无缝起飞到顶边目标设备胶囊的逐帧计算：
 * - 采用平滑三次贝塞尔曲线（手势甩出强调初速度衔接，按钮触发强调蓄力抛射），全程 C2 连续无速度折角；
 * - 尾段触发顶部目标设备胶囊的「吸入微压」物理回弹（[targetPillImpactScale]），
 *   同时背后页面高斯模糊与中央封面光晕平滑消散。
 */
internal fun resolveNearbyStageLaunchFrame(
    launchProgress: Float,
    startY: Float,
    startScale: Float,
    targetY: Float,
    reduceMotion: Boolean,
    startRotationXDeg: Float = 0f,
    fromButton: Boolean = false,
): NearbyStageLaunchFrame {
    val p = launchProgress.coerceIn(0f, 1f)
    if (reduceMotion) {
        return NearbyStageLaunchFrame(
            translationYPx = 0f,
            scale = 1f,
            alpha = 1f - p,
            rotationXDeg = 0f,
            blurFraction = 0f,
            scrimAlpha = 0.45f * (1f - p),
            backdropBlurFraction = 0f,
            ambientGlowAlpha = 0f,
            haloProgress = 0f,
            haloAlpha = 0f,
            targetPillImpactScale = 1f,
        )
    }
    val easing = if (fromButton) NearbyButtonLaunchEasing else NearbyGestureFlingEasing
    val eased = easing.transform(p)
    val y = startY + (targetY - startY) * eased
    val scale = startScale + (0.24f - startScale) * eased
    val rotationX = startRotationXDeg + (16f - startRotationXDeg) * eased
    val alpha = (1f - ((p - 0.52f).coerceAtLeast(0f) / 0.48f)).coerceIn(0f, 1f)
    val blur = (eased * 0.90f).coerceIn(0f, 1f)
    val scrim = 0.54f * (1f - eased)
    val backdropBlur = (1f - eased).coerceIn(0f, 1f)
    val ambientGlow = 0.72f * (1f - eased)
    val haloAlpha = nearbyFlightEnvelope(p) * 0.88f
    // 卡片在 p = 0.52..0.94 撞入顶部目标胶囊时，目标胶囊做一次微妙的「吸入微压回弹」
    val impactWindow = ((p - 0.52f) / 0.42f).coerceIn(0f, 1f)
    val impactScale = 1f - 0.07f * sin(PI.toFloat() * impactWindow).coerceAtLeast(0f)
    return NearbyStageLaunchFrame(
        translationYPx = y,
        scale = scale,
        alpha = alpha,
        rotationXDeg = rotationX,
        blurFraction = blur,
        scrimAlpha = scrim,
        backdropBlurFraction = backdropBlur,
        ambientGlowAlpha = ambientGlow,
        haloProgress = eased,
        haloAlpha = haloAlpha,
        targetPillImpactScale = impactScale,
    )
}

/**
 * 第二步：全屏中央大视频卡片流转舞台：
 * - 底层页面实时高斯模糊 + 中央无缝羽化封面氛围光晕；
 * - 顶部目标设备胶囊充当空间「接收口」，拖近高亮、吸入微压回弹；
 * - 中央 16:9 大卡片支持橡皮筋上滑 + 快甩抛飞 + 悬浮投影，底部主操作区上提便于单手触达。
 */
@Composable
internal fun NearbySendStageOverlay(
    peer: NearbyPeer,
    video: NearbyVideo,
    canSwitchDevice: Boolean,
    onBindBackdropBlurProvider: ((() -> Float)?) -> Unit,
    onSwitchDevice: () -> Unit,
    onConfirmSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val blurEnabled = !reduceMotion && !isLowBlurBudgetForced()
    val haptic = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val launchRichHaptic = com.android.purebilibili.core.ui.rememberRichHapticFeedback(AppHapticScene.NAVIGATION)
    val hapticEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
    val accent = AppContentColors.accent()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stageBackdropSource = if (glassEnabled) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    } else null
    val stageBackdrop = stageBackdropSource?.takeIf { it.isReady }?.backdrop

    val reveal = remember(peer.pin, video.contentKey) { Animatable(0f) }
    val dragY = remember(peer.pin, video.contentKey) { Animatable(0f) }
    val launchProgress = remember(peer.pin, video.contentKey) { Animatable(0f) }

    var armedToSend by remember(peer.pin, video.contentKey) { mutableStateOf(false) }
    var launching by remember(peer.pin, video.contentKey) { mutableStateOf(false) }
    var launchFromButton by remember(peer.pin, video.contentKey) { mutableStateOf(false) }
    var launchStartY by remember(peer.pin, video.contentKey) { mutableFloatStateOf(0f) }
    var launchStartScale by remember(peer.pin, video.contentKey) { mutableFloatStateOf(1f) }
    var launchStartRotationX by remember(peer.pin, video.contentKey) { mutableFloatStateOf(0f) }

    val latestConfirmSend by rememberUpdatedState(onConfirmSend)
    val latestCancel by rememberUpdatedState(onCancel)
    val latestBindBlur by rememberUpdatedState(onBindBackdropBlurProvider)

    LaunchedEffect(peer.pin, video.contentKey, reduceMotion) {
        if (reduceMotion) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(
                1f,
                spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val dismissWithAnim: () -> Unit = {
        if (!launching) {
            scope.launch {
                if (!reduceMotion) {
                    reveal.animateTo(0f, continuityTween(180))
                }
                latestCancel()
            }
        }
    }

    BackHandler(enabled = !launching) { dismissWithAnim() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sendThresholdPx = minOf(with(density) { 98.dp.toPx() }, constraints.maxHeight * 0.16f)
        val minFlingDistancePx = with(density) { 28.dp.toPx() }
        val flingVelocityThresholdPx = with(density) { 750.dp.toPx() }
        val cancelThresholdPx = with(density) { 78.dp.toPx() }
        val targetTopY = -(constraints.maxHeight * 0.43f).coerceAtLeast(180f)
        val isCompactHeight = maxHeight < 520.dp
        // 中央卡片与下方按钮作为一个视觉整体居中，卡片光心相对屏幕中线略微上移，保持光晕同心。
        val cardOpticalLiftPx = with(density) { (if (isCompactHeight) 30.dp else 36.dp).toPx() }

        DisposableEffect(peer.pin, video.contentKey, targetTopY, cancelThresholdPx, reduceMotion) {
            latestBindBlur {
                if (reduceMotion) 0f
                else if (launching) {
                    resolveNearbyStageLaunchFrame(
                        launchProgress.value,
                        launchStartY,
                        launchStartScale,
                        targetTopY,
                        false,
                        launchStartRotationX,
                        launchFromButton,
                    ).backdropBlurFraction
                } else {
                    val downFade = (dragY.value / (cancelThresholdPx * 2.2f)).coerceIn(0f, 0.45f)
                    (reveal.value * (1f - downFade)).coerceIn(0f, 1f)
                }
            }
            onDispose { latestBindBlur(null) }
        }

        fun triggerLaunch(
            fromY: Float,
            fromScale: Float,
            fromRotationX: Float,
            fromButton: Boolean,
            velocityYPxPerSec: Float = 0f,
        ) {
            if (launching) return
            launching = true
            armedToSend = true
            launchFromButton = fromButton
            launchStartY = fromY
            launchStartScale = fromScale
            launchStartRotationX = fromRotationX
            if (hapticEnabled) launchRichHaptic(com.android.purebilibili.core.ui.AppRichHapticPattern.GLASS_SNAP)
            scope.launch {
                if (reduceMotion) {
                    launchProgress.animateTo(1f, continuityTween(140))
                } else {
                    if (fromButton) {
                        // 点击按钮时先做轻快蓄力微弹，再无缝衔接入贝塞尔抛射曲线
                        dragY.animateTo(
                            -with(density) { 14.dp.toPx() },
                            continuityTween(100),
                        )
                        launchStartY = dragY.value
                        launchStartScale = 1.035f
                        launchStartRotationX = 3f
                    }
                    val durationMs = resolveNearbyLaunchDurationMs(velocityYPxPerSec, fromButton)
                    launchProgress.animateTo(1f, continuityTween(durationMs))
                }
                latestConfirmSend()
            }
        }

        // 1. 全屏柔和暗角衬底
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (launching) {
                        resolveNearbyStageLaunchFrame(
                            launchProgress.value,
                            launchStartY,
                            launchStartScale,
                            targetTopY,
                            reduceMotion,
                            launchStartRotationX,
                            launchFromButton,
                        ).scrimAlpha
                    } else {
                        val downFade = (dragY.value / (cancelThresholdPx * 2.2f)).coerceIn(0f, 0.45f)
                        0.54f * (reveal.value * (1f - downFade)).coerceIn(0f, 1f)
                    }
                }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !launching,
                    onClick = dismissWithAnim,
                ),
        )

        // 2. 中央封面无缝羽化氛围高斯光晕（位于顶部胶囊与卡片下方）
        NearbyAmbientCoverGlow(
            coverUrl = video.cover,
            accent = accent,
            blurEnabled = blurEnabled,
            alphaProvider = {
                if (launching) {
                    resolveNearbyStageLaunchFrame(
                        launchProgress.value,
                        launchStartY,
                        launchStartScale,
                        targetTopY,
                        reduceMotion,
                        launchStartRotationX,
                        launchFromButton,
                    ).ambientGlowAlpha
                } else {
                    val downFade = (dragY.value / (cancelThresholdPx * 2f)).coerceIn(0f, 0.6f)
                    val armBoost = if (armedToSend) 0.14f else 0f
                    ((0.62f + armBoost) * reveal.value * (1f - downFade)).coerceIn(0f, 0.80f)
                }
            },
            translationYProvider = {
                val baseY = if (launching) {
                    resolveNearbyStageLaunchFrame(
                        launchProgress.value,
                        launchStartY,
                        launchStartScale,
                        targetTopY,
                        reduceMotion,
                        launchStartRotationX,
                        launchFromButton,
                    ).translationYPx
                } else {
                    dragY.value
                }
                baseY - cardOpticalLiftPx
            },
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(width = 560.dp, height = 460.dp)
                .then(stageBackdropSource?.modifier ?: Modifier),
        )

        // 3. 屏幕顶部：目标设备接收口胶囊（上滑吸附高亮 + 吸入微压回弹）
        val targetArmedScale by animateFloatAsState(
            targetValue = if (armedToSend) 1.06f else 1f,
            animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMedium),
            label = "nearbyTargetPillScale",
        )
        val targetBorderColor by animateColorAsState(
            targetValue = if (armedToSend) accent else Color.White.copy(alpha = if (glassEnabled) 0.24f else 0.18f),
            animationSpec = continuityTween(if (reduceMotion) 0 else 160),
            label = "nearbyTargetPillBorder",
        )
        val targetPillShape = RoundedCornerShape(999.dp)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .graphicsLayer {
                    val launchFrame = if (launching) {
                        resolveNearbyStageLaunchFrame(
                            launchProgress.value,
                            launchStartY,
                            launchStartScale,
                            targetTopY,
                            reduceMotion,
                            launchStartRotationX,
                            launchFromButton,
                        )
                    } else null
                    val baseAlpha = if (launching) {
                        (1f - ((launchProgress.value - 0.72f).coerceAtLeast(0f) / 0.28f)).coerceIn(0f, 1f)
                    } else {
                        reveal.value
                    }
                    val combinedScale = targetArmedScale * (launchFrame?.targetPillImpactScale ?: 1f)
                    alpha = baseAlpha
                    scaleX = combinedScale
                    scaleY = combinedScale
                    translationY = if (reduceMotion) 0f else (1f - reveal.value) * -28f
                },
        ) {
            val topPillContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 6.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (armedToSend) accent else accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = nearbyDeviceIcon(peer.formFactor),
                            contentDescription = null,
                            tint = if (armedToSend) AppContentColors.onAccent() else accent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        AppText(
                            text = "流转至 ${peer.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (glassEnabled) Color.White else AppContentColors.primaryText(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppText(
                            text = when {
                                launching -> "正在发送接力…"
                                armedToSend -> "松手立即接力播放"
                                else -> "${peer.formFactor.label} · 向上拖动或轻甩发送"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (armedToSend) accent else if (glassEnabled) Color.White.copy(alpha = 0.72f) else AppContentColors.secondaryText(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!launching) {
                        AppTextButton(onClick = onSwitchDevice) {
                            AppText(
                                text = if (canSwitchDevice) "切换" else "设备",
                                color = if (glassEnabled) Color.White else Color.Unspecified,
                            )
                        }
                    }
                }
            }
            if (glassEnabled) {
                NearbyGlassPill(
                    nativeContainerColor = AppSurfaceTokens.surfaceContainerHigh(),
                    backdrop = stageBackdrop,
                    shape = targetPillShape,
                    glassContainerColor = Color.Black.copy(alpha = 0.34f),
                    borderColor = targetBorderColor,
                    borderWidth = if (armedToSend) 1.5.dp else 0.8.dp,
                    blurRadius = 18.dp,
                    modifier = Modifier.widthIn(max = 400.dp),
                ) { topPillContent() }
            } else {
                AppPopupSurface(
                    type = AppPopupSurfaceType.MENU,
                    shape = targetPillShape,
                    containerColor = AppSurfaceTokens.surfaceContainerHigh(),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .border(if (armedToSend) 1.5.dp else 1.dp, targetBorderColor, targetPillShape),
                ) { topPillContent() }
            }
        }

        // 4. 屏幕中央视觉单元：16:9 精致视频卡片 + 紧贴其下的单手主操作区
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 16.dp else 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = if (isCompactHeight) 360.dp else 430.dp)
                    .fillMaxWidth()
                    .then(if (isCompactHeight) Modifier.heightIn(max = 210.dp) else Modifier)
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        cameraDistance = 14f * density.density
                        if (launching) {
                            val frame = resolveNearbyStageLaunchFrame(
                                launchProgress.value,
                                launchStartY,
                                launchStartScale,
                                targetTopY,
                                reduceMotion,
                                launchStartRotationX,
                                launchFromButton,
                            )
                            translationY = frame.translationYPx
                            scaleX = frame.scale
                            scaleY = frame.scale
                            rotationX = frame.rotationXDeg
                            alpha = frame.alpha
                        } else {
                            val dy = dragY.value
                            val upRatio = (-dy / (sendThresholdPx * 2.4f)).coerceIn(0f, 1f)
                            val downRatio = (dy / (cancelThresholdPx * 2.2f)).coerceIn(0f, 1f)
                            val revealScale = if (reduceMotion) 1f else 0.88f + 0.12f * reveal.value
                            val dragScale = if (dy < 0f) 1f - upRatio * 0.22f else 1f - downRatio * 0.08f
                            translationY = dy
                            scaleX = revealScale * dragScale
                            scaleY = scaleX
                            rotationX = if (reduceMotion) 0f else if (dy < 0f) upRatio * 7f else -downRatio * 4f
                            alpha = (reveal.value * (1f - downRatio * 0.45f)).coerceIn(0f, 1f)
                        }
                    }
                    .then(
                        if (reduceMotion) Modifier else Modifier.nearbyCleanPulseRing(
                            haloProgressProvider = {
                                if (launching) {
                                    resolveNearbyStageLaunchFrame(
                                        launchProgress.value,
                                        launchStartY,
                                        launchStartScale,
                                        targetTopY,
                                        false,
                                        launchStartRotationX,
                                        launchFromButton,
                                    ).haloProgress
                                } else {
                                    (-dragY.value / (sendThresholdPx * 1.3f)).coerceIn(0f, 1f)
                                }
                            },
                            haloAlphaProvider = {
                                if (launching) {
                                    resolveNearbyStageLaunchFrame(
                                        launchProgress.value,
                                        launchStartY,
                                        launchStartScale,
                                        targetTopY,
                                        false,
                                        launchStartRotationX,
                                        launchFromButton,
                                    ).haloAlpha
                                } else {
                                    val r = (-dragY.value / sendThresholdPx).coerceIn(0f, 1f)
                                    if (r < 0.10f) 0f else r * 0.86f
                                }
                            },
                            accent = accent,
                            highlight = Color.White,
                            glassEnabled = glassEnabled,
                            cornerRadiusDp = 24.dp,
                        )
                    )
                    .pointerInput(peer.pin, video.contentKey, launching, reduceMotion, hapticEnabled) {
                        if (launching) return@pointerInput
                        val velocityTracker = VelocityTracker()
                        var rawDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                rawDrag = dragY.value
                            },
                            onDragCancel = {
                                armedToSend = false
                                rawDrag = 0f
                                scope.launch {
                                    if (reduceMotion) dragY.snapTo(0f)
                                    else dragY.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            },
                            onDragEnd = {
                                val currentY = dragY.value
                                val velocityY = velocityTracker.calculateVelocity().y
                                val shouldSend = shouldTriggerNearbyFlingSend(
                                    dragYPx = currentY,
                                    velocityYPxPerSec = velocityY,
                                    distanceThresholdPx = sendThresholdPx,
                                    velocityThresholdPxPerSec = flingVelocityThresholdPx,
                                    minFlingDistancePx = minFlingDistancePx,
                                )
                                val shouldCancel = currentY >= cancelThresholdPx ||
                                    (currentY >= minFlingDistancePx && velocityY >= flingVelocityThresholdPx)
                                rawDrag = 0f
                                when {
                                    shouldSend -> {
                                        val upRatio = (-currentY / (sendThresholdPx * 2.4f)).coerceIn(0f, 1f)
                                        val currentScale = 1f - upRatio * 0.22f
                                        val currentRotationX = if (reduceMotion) 0f else upRatio * 7f
                                        triggerLaunch(
                                            fromY = currentY,
                                            fromScale = currentScale,
                                            fromRotationX = currentRotationX,
                                            fromButton = false,
                                            velocityYPxPerSec = velocityY,
                                        )
                                    }
                                    shouldCancel -> {
                                        armedToSend = false
                                        dismissWithAnim()
                                    }
                                    else -> {
                                        armedToSend = false
                                        scope.launch {
                                            if (reduceMotion) dragY.snapTo(0f)
                                            else dragY.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                    }
                                }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            rawDrag += dragAmount
                            val damped = if (rawDrag < 0f) {
                                applyNearbyRubberBand(rawDrag, abs(targetTopY) * 0.85f)
                            } else {
                                applyNearbyRubberBand(rawDrag, cancelThresholdPx * 1.45f)
                            }
                            val nowArmed = damped <= -sendThresholdPx
                            if (nowArmed != armedToSend) {
                                armedToSend = nowArmed
                                if (nowArmed && hapticEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            scope.launch { dragY.snapTo(damped) }
                        }
                    },
            ) {
                val cardShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .shadow(
                            elevation = 24.dp,
                            shape = cardShape,
                            ambientColor = Color.Black.copy(alpha = 0.45f),
                            spotColor = Color.Black.copy(alpha = 0.55f),
                        )
                        .nearbyFlightBlur(
                            blurFractionProvider = {
                                if (launching) {
                                    resolveNearbyStageLaunchFrame(
                                        launchProgress.value,
                                        launchStartY,
                                        launchStartScale,
                                        targetTopY,
                                        reduceMotion,
                                        launchStartRotationX,
                                        launchFromButton,
                                    ).blurFraction
                                } else 0f
                            },
                            enabled = blurEnabled,
                            maxBlurDp = 10.dp,
                        )
                        .clip(cardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = if (armedToSend) 1.5.dp else 1.dp,
                            color = if (armedToSend) accent.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.28f),
                            shape = cardShape,
                        ),
                ) {
                    val cardBackdropSource = if (glassEnabled) {
                        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
                    } else null
                    val cardBackdrop = cardBackdropSource?.takeIf { it.isReady }?.backdrop
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(cardBackdropSource?.modifier ?: Modifier),
                    ) {
                        if (video.cover.isNotBlank()) {
                            AsyncImage(
                                model = video.cover,
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Black.copy(alpha = 0.24f),
                                        0.34f to Color.Black.copy(alpha = 0.04f),
                                        0.66f to Color.Black.copy(alpha = 0.36f),
                                        1.00f to Color.Black.copy(alpha = 0.86f),
                                    )
                                )
                        )
                    }

                    // 左上角：媒体类型 + 进度 / 倍速 / 播单上下文
                    NearbyGlassPill(
                        nativeContainerColor = Color.Black.copy(alpha = 0.56f),
                        backdrop = cardBackdrop,
                        glassContainerColor = Color.Black.copy(alpha = 0.28f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            AppIcon(
                                imageVector = nearbyMediaIcon(video),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            val tagText = if (video.isLive) {
                                video.mediaTypeLabel
                            } else {
                                val speedPart = if (video.speed != 1f) " · ${video.speed}x" else ""
                                val queuePart = video.queue?.items?.size?.takeIf { it > 1 }?.let { " · ${it}集播单" }.orEmpty()
                                "${video.mediaTypeLabel} · ${formatNearbyDuration(video.positionMs)}$speedPart$queuePart"
                            }
                            AppText(
                                text = tagText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // 右上角：上滑提示微标
                    NearbyGlassPill(
                        nativeContainerColor = if (armedToSend) accent else Color.Black.copy(alpha = 0.56f),
                        backdrop = cardBackdrop,
                        glassContainerColor = if (armedToSend) accent.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.28f),
                        borderColor = if (armedToSend) Color.White.copy(alpha = 0.42f) else null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = null,
                                tint = if (armedToSend) AppContentColors.onAccent() else Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            AppText(
                                text = if (armedToSend) "松手发送" else "上滑发送",
                                color = if (armedToSend) AppContentColors.onAccent() else Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (armedToSend) FontWeight.SemiBold else FontWeight.Medium
                                ),
                            )
                        }
                    }

                    // 底部：视频标题与元信息（含画质与弹幕偏好摘要）
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppText(
                            text = video.title.ifBlank { video.mediaTypeLabel },
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        val prefSummary = formatNearbyPreferenceSummary(video)
                        val subtitle = listOfNotNull(
                            if (video.isLive && video.uname.isNotBlank()) "主播 · ${video.uname}" else null,
                            prefSummary.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            AppText(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // 紧贴视频卡片下方的操作区（单行简洁按钮，避免长设备名换行，自然位于拇指舒适热区）
            Row(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val dragFade = (abs(dragY.value) / sendThresholdPx).coerceIn(0f, 1f)
                        alpha = if (launching) {
                            (1f - launchProgress.value * 2.6f).coerceIn(0f, 1f)
                        } else {
                            (reveal.value * (1f - dragFade * 0.72f)).coerceIn(0f, 1f)
                        }
                        translationY = if (reduceMotion) 0f else (1f - reveal.value) * 24f
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NearbyGlassPill(
                    nativeContainerColor = Color.White.copy(alpha = 0.16f),
                    backdrop = stageBackdrop,
                    glassContainerColor = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.size(46.dp),
                ) {
                    AppIconButton(
                        onClick = dismissWithAnim,
                        enabled = !launching,
                        modifier = Modifier.matchParentSize(),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "取消接力",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.size(0.dp))
                AppPrimaryButton(
                    text = "立即接力",
                    onClick = { triggerLaunch(fromY = 0f, fromScale = 1f, fromRotationX = 0f, fromButton = true) },
                    enabled = !launching,
                    leadingIcon = Icons.Outlined.SwapHoriz,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
