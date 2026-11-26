package com.android.purebilibili.feature.nearby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class NearbyReceiveStageExit { None, Accepting, Rejecting }

/**
 * 接收端对称舞台（与发送端 [NearbySendStageOverlay] 形成「上抛 ↔ 下落」空间联动）：
 * - 收到接力推送时，大视频卡片从屏幕正上方自然下落、由前倾回正并由微模糊变锐利，
 *   伴随底层高斯模糊与中央封面无缝羽化光晕，稳稳落位中央；
 * - 点击大卡片或底部「继续播放」：原地轻盈扩展消散，无缝进入播放；
 * - 上滑卡片退回 / 点取消 / 点空白 / 返回键：卡片优雅退回上方并拒绝本次接力。
 */
@Composable
internal fun NearbyReceiveStageOverlay(
    request: NearbyIncoming,
    localDanmakuEnabled: Boolean?,
    onBindBackdropBlurProvider: ((() -> Float)?) -> Unit,
    onAccept: (syncDanmaku: Boolean, openAsAudioOnly: Boolean) -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val glassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val stageBackdropSource = if (glassEnabled) {
        com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    } else null
    val stageBackdrop = stageBackdropSource?.takeIf { it.isReady }?.backdrop
    val blurEnabled = !reduceMotion && !isLowBlurBudgetForced()
    val haptic = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    val hapticEnabled = LocalAppThemeConfig.current.hapticFeedbackEnabled
    val accent = AppContentColors.accent()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 0f = 屏幕顶边起点，1f = 落位屏幕中央
    val dropProgress = remember(request.id) { Animatable(0f) }
    val dragY = remember(request.id) { Animatable(0f) }
    val exitProgress = remember(request.id) { Animatable(0f) }
    var exitMode by remember(request.id) { mutableStateOf(NearbyReceiveStageExit.None) }
    var syncDanmakuOnAccept by remember(request.id) { mutableStateOf(false) }
    var openAsAudioOnly by remember(request.id) { mutableStateOf(request.video.isVideo && request.video.audioOnly) }

    val latestAccept by rememberUpdatedState(onAccept)
    val latestReject by rememberUpdatedState(onReject)
    val latestBindBlur by rememberUpdatedState(onBindBackdropBlurProvider)

    LaunchedEffect(request.id, reduceMotion) {
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        if (reduceMotion) {
            dropProgress.snapTo(1f)
        } else {
            dropProgress.animateTo(
                1f,
                spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    fun triggerAccept() {
        if (exitMode != NearbyReceiveStageExit.None) return
        exitMode = NearbyReceiveStageExit.Accepting
        val shouldSyncDanmaku = syncDanmakuOnAccept && !openAsAudioOnly
        val targetAudioOnly = request.video.isVideo && openAsAudioOnly
        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            if (!reduceMotion) {
                exitProgress.animateTo(1f, continuityTween(210))
            }
            latestAccept(shouldSyncDanmaku, targetAudioOnly)
        }
    }

    fun triggerReject() {
        if (exitMode != NearbyReceiveStageExit.None) return
        exitMode = NearbyReceiveStageExit.Rejecting
        scope.launch {
            if (!reduceMotion) {
                exitProgress.animateTo(1f, continuityTween(220))
            }
            latestReject()
        }
    }

    BackHandler(enabled = exitMode == NearbyReceiveStageExit.None) { triggerReject() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val dropStartY = -(constraints.maxHeight * 0.46f).coerceAtLeast(220f)
        val rejectThresholdPx = with(density) { 84.dp.toPx() }
        val minFlingDistancePx = with(density) { 28.dp.toPx() }
        val flingVelocityThresholdPx = with(density) { 750.dp.toPx() }
        val isCompactHeight = maxHeight < 520.dp
        val cardOpticalLiftPx = with(density) { (if (isCompactHeight) 30.dp else 36.dp).toPx() }
        val media = request.video
        val peer = request.peer

        DisposableEffect(request.id, rejectThresholdPx, reduceMotion) {
            latestBindBlur {
                if (reduceMotion) 0f
                else when (exitMode) {
                    NearbyReceiveStageExit.None -> {
                        val dragFade = (abs(dragY.value) / (rejectThresholdPx * 2.4f)).coerceIn(0f, 0.45f)
                        (dropProgress.value.coerceIn(0f, 1f) * (1f - dragFade)).coerceIn(0f, 1f)
                    }
                    NearbyReceiveStageExit.Accepting,
                    NearbyReceiveStageExit.Rejecting -> (1f - exitProgress.value).coerceIn(0f, 1f)
                }
            }
            onDispose { latestBindBlur(null) }
        }

        // 1. 全屏柔和暗角衬底
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val base = when (exitMode) {
                        NearbyReceiveStageExit.None -> {
                            val dragFade = (abs(dragY.value) / (rejectThresholdPx * 2.4f)).coerceIn(0f, 0.45f)
                            dropProgress.value.coerceIn(0f, 1f) * (1f - dragFade)
                        }
                        NearbyReceiveStageExit.Accepting,
                        NearbyReceiveStageExit.Rejecting -> 1f - exitProgress.value
                    }
                    alpha = (0.54f * base).coerceIn(0f, 0.6f)
                }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = exitMode == NearbyReceiveStageExit.None,
                    onClick = { triggerReject() },
                ),
        )

        // 2. 中央无缝羽化封面氛围高斯光晕（置于顶部胶囊与卡片下方）
        NearbyAmbientCoverGlow(
            coverUrl = media.cover,
            accent = accent,
            blurEnabled = blurEnabled,
            alphaProvider = {
                val base = when (exitMode) {
                    NearbyReceiveStageExit.None -> dropProgress.value.coerceIn(0f, 1f)
                    NearbyReceiveStageExit.Accepting,
                    NearbyReceiveStageExit.Rejecting -> 1f - exitProgress.value
                }
                (0.64f * base).coerceIn(0f, 0.8f)
            },
            translationYProvider = {
                val dropY = if (reduceMotion) 0f else dropStartY * (1f - dropProgress.value)
                dropY + dragY.value - cardOpticalLiftPx
            },
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(width = 560.dp, height = 460.dp)
                .then(stageBackdropSource?.modifier ?: Modifier),
        )

        // 3. 顶部对称来源设备胶囊
        val sourcePillShape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .graphicsLayer {
                    val p = dropProgress.value.coerceIn(0f, 1f)
                    val visible = when (exitMode) {
                        NearbyReceiveStageExit.None -> p
                        NearbyReceiveStageExit.Accepting,
                        NearbyReceiveStageExit.Rejecting -> (1f - exitProgress.value * 1.4f).coerceIn(0f, 1f)
                    }
                    alpha = visible
                    translationY = if (reduceMotion) 0f else (1f - p) * -28f
                },
        ) {
            val sourcePillContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = nearbyDeviceIcon(peer.formFactor),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        AppText(
                            text = "来自 ${peer.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (glassEnabled) Color.White else AppContentColors.primaryText(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppText(
                            text = "${peer.formFactor.label} · 邀请你接续播放",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (glassEnabled) {
                NearbyGlassPill(
                    nativeContainerColor = AppSurfaceTokens.surfaceContainerHigh(),
                    backdrop = stageBackdrop,
                    shape = sourcePillShape,
                    glassContainerColor = Color.Black.copy(alpha = 0.34f),
                    borderColor = accent.copy(alpha = 0.45f),
                    borderWidth = 0.9.dp,
                    blurRadius = 18.dp,
                    modifier = Modifier.widthIn(max = 400.dp),
                ) { sourcePillContent() }
            } else {
                AppPopupSurface(
                    type = AppPopupSurfaceType.MENU,
                    shape = sourcePillShape,
                    containerColor = AppSurfaceTokens.surfaceContainerHigh(),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .border(1.dp, accent.copy(alpha = 0.45f), sourcePillShape),
                ) { sourcePillContent() }
            }
        }

        // 4. 屏幕中央视觉单元：由正上方自然下落落位的 16:9 大视频卡片 + 紧贴其下的对称操作区
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
                        val p = dropProgress.value
                        val clampedP = p.coerceIn(0f, 1f)
                        when (exitMode) {
                            NearbyReceiveStageExit.None -> {
                                val dropY = if (reduceMotion) 0f else dropStartY * (1f - p)
                                val dropScale = if (reduceMotion) 1f else 0.38f + 0.62f * p
                                val dy = dragY.value
                                val dragRatio = (abs(dy) / (rejectThresholdPx * 2.2f)).coerceIn(0f, 1f)
                                translationY = dropY + dy * 0.72f
                                scaleX = (dropScale * (1f - dragRatio * 0.12f)).coerceAtLeast(0.2f)
                                scaleY = scaleX
                                rotationX = if (reduceMotion) 0f else -12f * (1f - clampedP) + (if (dy < 0f) dragRatio * 6f else 0f)
                                alpha = (clampedP * 1.4f).coerceIn(0f, 1f) * (1f - dragRatio * 0.35f)
                            }
                            NearbyReceiveStageExit.Accepting -> {
                                val e = exitProgress.value.coerceIn(0f, 1f)
                                translationY = 0f
                                scaleX = if (reduceMotion) 1f else 1f + 0.08f * e
                                scaleY = scaleX
                                rotationX = 0f
                                alpha = (1f - e).coerceIn(0f, 1f)
                            }
                            NearbyReceiveStageExit.Rejecting -> {
                                val e = exitProgress.value.coerceIn(0f, 1f)
                                val eased = e * e
                                translationY = if (reduceMotion) 0f else dragY.value + (dropStartY - dragY.value) * eased
                                scaleX = if (reduceMotion) 1f else 1f - 0.52f * eased
                                scaleY = scaleX
                                rotationX = if (reduceMotion) 0f else 10f * eased
                                alpha = (1f - e).coerceIn(0f, 1f)
                            }
                        }
                    }
                    .then(
                        if (reduceMotion) Modifier else Modifier.nearbyCleanPulseRing(
                            haloProgressProvider = { dropProgress.value.coerceIn(0f, 1f) },
                            haloAlphaProvider = {
                                if (exitMode != NearbyReceiveStageExit.None) 0f
                                else nearbyFlightEnvelope(dropProgress.value.coerceIn(0f, 1f)) * 0.85f
                            },
                            accent = accent,
                            highlight = Color.White,
                            glassEnabled = glassEnabled,
                            cornerRadiusDp = 24.dp,
                        )
                    )
                    .clickable(
                        enabled = exitMode == NearbyReceiveStageExit.None,
                        onClick = { triggerAccept() },
                    )
                    .pointerInput(request.id, exitMode, reduceMotion) {
                        if (exitMode != NearbyReceiveStageExit.None) return@pointerInput
                        val velocityTracker = VelocityTracker()
                        var rawDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                rawDrag = dragY.value
                            },
                            onDragCancel = {
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
                                val flingReject = abs(currentY) >= minFlingDistancePx && abs(velocityY) >= flingVelocityThresholdPx
                                rawDrag = 0f
                                if (abs(currentY) >= rejectThresholdPx || flingReject) {
                                    triggerReject()
                                } else {
                                    scope.launch {
                                        if (reduceMotion) dragY.snapTo(0f)
                                        else dragY.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
                                        )
                                    }
                                }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            rawDrag += dragAmount
                            val damped = applyNearbyRubberBand(rawDrag, rejectThresholdPx * 1.65f)
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
                                if (exitMode == NearbyReceiveStageExit.None) {
                                    ((1f - dropProgress.value.coerceIn(0f, 1f)) * 0.85f).coerceIn(0f, 1f)
                                } else if (exitMode == NearbyReceiveStageExit.Rejecting) {
                                    (exitProgress.value * 0.7f).coerceIn(0f, 1f)
                                } else 0f
                            },
                            enabled = blurEnabled,
                            maxBlurDp = 10.dp,
                        )
                        .clip(cardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, Color.White.copy(alpha = 0.28f), cardShape),
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
                        if (media.cover.isNotBlank()) {
                            AsyncImage(
                                model = media.cover,
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
                                imageVector = nearbyMediaIcon(media),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            val tagText = if (media.isLive) {
                                media.mediaTypeLabel
                            } else {
                                val speedPart = if (media.speed != 1f) " · ${media.speed}x" else ""
                                val queuePart = media.queue?.items?.size?.takeIf { it > 1 }?.let { " · ${it}集播单" }.orEmpty()
                                "${media.mediaTypeLabel} · ${formatNearbyDuration(media.positionMs)}$speedPart$queuePart"
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

                    // 底部：视频标题与对端画质/弹幕偏好提示
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppText(
                            text = media.title.ifBlank { media.mediaTypeLabel },
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        val prefSummary = formatNearbyPreferenceSummary(media)
                        val hint = listOfNotNull(
                            if (media.isLive && media.uname.isNotBlank()) "主播 · ${media.uname}" else null,
                            if (media.audioOnly) {
                                if (openAsAudioOnly) "对端正在纯听 · 将继续纯听播放" else "对端正在纯听 · 将切换为视频画面"
                            } else null,
                            prefSummary.takeIf { it.isNotBlank() }?.let { "对端 $it" },
                            if (!media.isLive && !media.audioOnly && prefSummary.isBlank()) "点击卡片或下方按钮立即接续" else null,
                        ).joinToString(" · ")
                        if (hint.isNotBlank()) {
                            AppText(
                                text = hint,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // 普通视频支持接收端自由切换「画面续播」与「继续纯听」（默认跟随对端模式）
            if (media.isVideo) {
                val pillShape = RoundedCornerShape(999.dp)
                NearbyGlassPill(
                    nativeContainerColor = Color.White.copy(alpha = 0.14f),
                    backdrop = stageBackdrop,
                    shape = pillShape,
                    glassContainerColor = Color.Black.copy(alpha = 0.28f),
                    borderColor = Color.White.copy(alpha = 0.22f),
                    borderWidth = 0.9.dp,
                    blurRadius = 16.dp,
                    modifier = Modifier.graphicsLayer {
                        val p = dropProgress.value.coerceIn(0f, 1f)
                        alpha = when (exitMode) {
                            NearbyReceiveStageExit.None -> p
                            NearbyReceiveStageExit.Accepting,
                            NearbyReceiveStageExit.Rejecting -> (1f - exitProgress.value * 2f).coerceIn(0f, 1f)
                        }
                        translationY = if (reduceMotion) 0f else (1f - p) * 18f
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val selectMode: (Boolean) -> Unit = { audioOnlySelected ->
                            if (openAsAudioOnly != audioOnlySelected) {
                                openAsAudioOnly = audioOnlySelected
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(if (!openAsAudioOnly) accent else Color.Transparent)
                                .clickable(enabled = exitMode == NearbyReceiveStageExit.None) { selectMode(false) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.SmartDisplay,
                                contentDescription = null,
                                tint = if (!openAsAudioOnly) AppContentColors.onAccent() else Color.White.copy(alpha = 0.84f),
                                modifier = Modifier.size(14.dp),
                            )
                            AppText(
                                text = "画面续播",
                                color = if (!openAsAudioOnly) AppContentColors.onAccent() else Color.White.copy(alpha = 0.84f),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (!openAsAudioOnly) FontWeight.SemiBold else FontWeight.Medium
                                ),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(pillShape)
                                .background(if (openAsAudioOnly) accent else Color.Transparent)
                                .clickable(enabled = exitMode == NearbyReceiveStageExit.None) { selectMode(true) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Headphones,
                                contentDescription = null,
                                tint = if (openAsAudioOnly) AppContentColors.onAccent() else Color.White.copy(alpha = 0.84f),
                                modifier = Modifier.size(14.dp),
                            )
                            AppText(
                                text = "继续纯听",
                                color = if (openAsAudioOnly) AppContentColors.onAccent() else Color.White.copy(alpha = 0.84f),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (openAsAudioOnly) FontWeight.SemiBold else FontWeight.Medium
                                ),
                            )
                        }
                    }
                }
            }

            // 对端与本机弹幕开关不同且选择画面续播时，提供轻量化一键同步选择胶囊
            val remoteDanmaku = media.danmakuEnabled
            val danmakuDiffers = !openAsAudioOnly &&
                remoteDanmaku != null &&
                localDanmakuEnabled != null &&
                remoteDanmaku != localDanmakuEnabled
            if (danmakuDiffers) {
                val chipShape = RoundedCornerShape(999.dp)
                val remoteLabel = if (remoteDanmaku == true) "弹幕开" else "弹幕关"
                val localLabel = if (localDanmakuEnabled == true) "弹幕开" else "弹幕关"
                NearbyGlassPill(
                    nativeContainerColor = if (syncDanmakuOnAccept) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
                    backdrop = stageBackdrop,
                    shape = chipShape,
                    glassContainerColor = if (syncDanmakuOnAccept) accent.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.28f),
                    borderColor = if (syncDanmakuOnAccept) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.22f),
                    borderWidth = 0.9.dp,
                    blurRadius = 16.dp,
                    modifier = Modifier.graphicsLayer {
                        val p = dropProgress.value.coerceIn(0f, 1f)
                        alpha = when (exitMode) {
                            NearbyReceiveStageExit.None -> p
                            NearbyReceiveStageExit.Accepting,
                            NearbyReceiveStageExit.Rejecting -> (1f - exitProgress.value * 2f).coerceIn(0f, 1f)
                        }
                        translationY = if (reduceMotion) 0f else (1f - p) * 18f
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(enabled = exitMode == NearbyReceiveStageExit.None) {
                                syncDanmakuOnAccept = !syncDanmakuOnAccept
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(
                            imageVector = if (syncDanmakuOnAccept) Icons.Outlined.Check else Icons.Outlined.Subtitles,
                            contentDescription = null,
                            tint = if (syncDanmakuOnAccept) accent else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp),
                        )
                        AppText(
                            text = if (syncDanmakuOnAccept) {
                                "接续时将同步为「$remoteLabel」"
                            } else {
                                "对端$remoteLabel（本机$localLabel）· 点此同步"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 紧贴视频卡片下方的对称操作区
            Row(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = dropProgress.value.coerceIn(0f, 1f)
                        val visible = when (exitMode) {
                            NearbyReceiveStageExit.None -> p
                            NearbyReceiveStageExit.Accepting,
                            NearbyReceiveStageExit.Rejecting -> (1f - exitProgress.value * 2f).coerceIn(0f, 1f)
                        }
                        alpha = visible
                        translationY = if (reduceMotion) 0f else (1f - p) * 24f
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
                        onClick = { triggerReject() },
                        enabled = exitMode == NearbyReceiveStageExit.None,
                        modifier = Modifier.matchParentSize(),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "暂不接收",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.size(0.dp))
                AppPrimaryButton(
                    text = if (media.isVideo && openAsAudioOnly) "继续纯听" else "继续播放",
                    onClick = { triggerAccept() },
                    enabled = exitMode == NearbyReceiveStageExit.None,
                    leadingIcon = if (media.isVideo && openAsAudioOnly) Icons.Outlined.Headphones else Icons.Outlined.PlayArrow,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
