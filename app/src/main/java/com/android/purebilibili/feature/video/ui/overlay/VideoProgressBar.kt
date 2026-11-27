package com.android.purebilibili.feature.video.ui.overlay

import android.os.SystemClock
import com.android.purebilibili.core.player.SeekTickHapticPolicy
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.plugin.skin.LocalUiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinAnimatedAsset
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import com.android.purebilibili.core.plugin.skin.assetPath
import com.android.purebilibili.core.plugin.skin.parseUiSkinColor
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.drawMediaProgressTrack
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.feature.video.progress.PbpRidgeDensity
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubble
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubbleSimple
import com.android.purebilibili.feature.video.ui.components.SeekPreviewFilmstrip
import com.android.purebilibili.feature.video.ui.components.resolveSeekPreviewBubbleHeightDp
import kotlin.math.roundToInt

/**
 * Reusing existing VideoProgressBar
 */
@Composable
fun VideoProgressBar(
    currentPosition: Long,
    displayPositionMs: Long,
    duration: Long,
    bufferedPosition: Long,
    isSeekScrubbing: Boolean,
    layoutPolicy: VideoProgressBarLayoutPolicy,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    viewPoints: List<com.android.purebilibili.data.model.response.ViewPoint> = emptyList(),
    sponsorMarkers: List<SponsorProgressMarker> = emptyList(),
    pbpRidgeSamples: List<PbpRidgeSample> = emptyList(),
    abLoopPointAMs: Long? = null,
    abLoopPointBMs: Long? = null,
    currentChapter: String? = null,
    onChapterClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    displayPositionProvider: (() -> Long)? = null,
    seekAreaHeight: Dp? = null,
    verticalHeightScale: Float = 1f,
    horizontalSeekOnly: Boolean = false,
) {
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var dragTargetPositionMs by remember { mutableLongStateOf(displayPositionMs.coerceAtLeast(0L)) }
    var hoverPositionMs by remember { mutableLongStateOf(-1L) }
    var isFineScrubbingActive by remember { mutableStateOf(false) }
    var activeSnapLabel by remember { mutableStateOf<String?>(null) }
    val scrubbingConfig = LocalVideoProgressScrubbingConfig.current
    val currentScrubbingConfig by rememberUpdatedState(scrubbingConfig)
    val snapPoints = remember(duration, viewPoints, pbpRidgeSamples) {
        extractProgressSnapPoints(
            durationMs = duration,
            viewPoints = viewPoints,
            pbpRidgeSamples = pbpRidgeSamples,
        )
    }
    val currentSnapPoints by rememberUpdatedState(snapPoints)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeekDragStart by rememberUpdatedState(onSeekDragStart)
    val currentOnSeekDragUpdate by rememberUpdatedState(onSeekDragUpdate)
    val currentOnSeekDragCancel by rememberUpdatedState(onSeekDragCancel)
    // 拖动进度条刻度触感：常规跨秒轻刻度，吸附章节/高能点/精细模式切换使用线性马达清晰刻度。
    val seekHaptic = rememberHapticFeedback(AppHapticScene.PLAYER_SEEK)
    val seekRichHaptic = com.android.purebilibili.core.ui.rememberRichHapticFeedback(AppHapticScene.PLAYER_SEEK)
    val seekHapticPolicy = remember { SeekTickHapticPolicy() }
    LaunchedEffect(displayPositionMs, isSeekScrubbing, duration) {
        if (!isSeekScrubbing) {
            dragTargetPositionMs = displayPositionMs.coerceAtLeast(0L)
            isFineScrubbingActive = false
            activeSnapLabel = null
        } else {
            hoverPositionMs = -1L
        }
        if (duration <= 0L) {
            hoverPositionMs = -1L
        }
    }

    val glassTrackHighlight = rememberPlayerProgressGlassHighlight()
    val uiSkinState = LocalUiSkinState.current
    val progressSkin = uiSkinState.activeSkin?.takeIf {
        uiSkinState.enabled && UiSkinSurface.PLAYER_PROGRESS in it.manifest.surfaces
    }
    val progressColors = progressSkin?.manifest?.colors
    val primaryColor = parseUiSkinColor(
        progressColors?.playerProgressActiveTint,
        // 与详情页选中态共用颜色入口，保留皮肤显式覆盖及原生主色。
        AppContentColors.selectionAccent(LocalAppThemeConfig.current.liquidGlassEnabled),
    )
    val bufferedTrackColor = parseUiSkinColor(
        progressColors?.playerProgressBufferedTint,
        Color.White.copy(alpha = 0.42f),
    )
    val inactiveTrackColor = parseUiSkinColor(
        progressColors?.playerProgressTrackTint,
        Color.White.copy(alpha = 0.24f),
    )
    val progressThumbPath = uiSkinState.assetPath(UiSkinSurface.PLAYER_PROGRESS) { assets ->
        if (isSeekScrubbing) {
            assets.playerProgressDraggingIcon ?: assets.playerProgressIcon ?: assets.playerProgressStaticIcon
        } else {
            assets.playerProgressIcon ?: assets.playerProgressStaticIcon
        }
    }
    val activePositionProvider: () -> Long = {
        resolveSeekPreviewTargetPositionMs(
            displayPositionMs = displayPositionProvider?.invoke() ?: displayPositionMs,
            dragTargetPositionMs = dragTargetPositionMs,
            isSeekScrubbing = isSeekScrubbing
        )
    }
    val bufferedProgress = resolveProgressFraction(
        positionMs = bufferedPosition,
        durationMs = duration
    )
    val resolvedSponsorMarkers = remember(duration, sponsorMarkers) {
        resolveSponsorProgressBarMarkers(
            durationMs = duration,
            markers = sponsorMarkers
        )
    }
    val baseHeightDp = seekAreaHeight ?: (
        if (currentChapter != null) layoutPolicy.baseHeightWithChapterDp.dp
        else layoutPolicy.baseHeightWithoutChapterDp.dp
    ) * verticalHeightScale
    val isPointerHovering = !isSeekScrubbing && hoverPositionMs >= 0L && duration > 0L
    val showSeekPreview = isSeekScrubbing || isPointerHovering
    val previewPositionProvider: () -> Long = remember(isSeekScrubbing, activePositionProvider) {
        { if (isSeekScrubbing) activePositionProvider() else hoverPositionMs.coerceAtLeast(0L) }
    }
    val previewImageHeightDp = if (showSeekPreview && videoshotData?.isValid == true) {
        resolveSeekPreviewBubbleHeightDp(
            screenWidthDp = LocalConfiguration.current.screenWidthDp,
            containerHeightDp = LocalConfiguration.current.screenHeightDp,
        ).takeIf { it > 0 }
    } else null
    val basePreviewAreaHeightDp = if (showSeekPreview) {
        resolveVideoProgressPreviewAreaHeightDp(
            layoutPolicy = layoutPolicy,
            hasChapter = currentChapter != null,
            previewImageHeightDp = previewImageHeightDp
        ).dp
    } else 0.dp
    val previewAreaHeightDp = if (isSeekScrubbing && isFineScrubbingActive) {
        val minFilmstripHeight = if (videoshotData?.isValid == true) 96.dp else 72.dp
        maxOf(basePreviewAreaHeightDp, minFilmstripHeight)
    } else basePreviewAreaHeightDp
    val thumbSizeDp = if (showSeekPreview) {
        layoutPolicy.thumbDraggingSizeDp.dp
    } else {
        layoutPolicy.thumbIdleSizeDp.dp
    }
    val thumbSizePx = with(LocalDensity.current) { thumbSizeDp.toPx() }
    val trackHeightPx = with(LocalDensity.current) { layoutPolicy.trackHeightDp.dp.toPx() }

    // Seek gestures must wrap chapter + track. A track-sibling pointerInput loses DOWN to the
    // chapter clickable (full-width + 48dp min touch), which is why chapter videos cannot scrub.
    val seekGestureHost = resolveVideoProgressBarSeekGestureHost(
        hasChapterLabel = currentChapter != null
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(baseHeightDp + previewAreaHeightDp)
    ) {
        if (showSeekPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewAreaHeightDp)
                    .padding(bottom = layoutPolicy.previewBottomPaddingDp.dp),
                contentAlignment = if (isFineScrubbingActive) Alignment.BottomCenter else Alignment.BottomStart
            ) {
                ProgressSeekPreview(
                    videoshotData = videoshotData,
                    positionProvider = previewPositionProvider,
                    currentPosition = currentPosition,
                    duration = duration,
                    thumbCenterXPx = containerWidthPx * resolveProgressFraction(
                        positionMs = previewPositionProvider(),
                        durationMs = duration
                    ),
                    containerWidthPx = containerWidthPx,
                    fineScrubbingActive = isSeekScrubbing && isFineScrubbingActive,
                    statusLabel = if (isPointerHovering) {
                        "手写笔/指针悬停预览 · 点击跳转"
                    } else {
                        activeSnapLabel ?: if (!isFineScrubbingActive && scrubbingConfig.fineScrubbingEnabled && !horizontalSeekOnly) {
                            "上滑进入精准时间轴"
                        } else null
                    },
                )

            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(baseHeightDp)
                .testTag("player_progress_seek_area")
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .pointerInput(duration, isSeekScrubbing) {
                    if (duration <= 0L) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val isStylusOrMouse =
                                change.type == androidx.compose.ui.input.pointer.PointerType.Stylus ||
                                    change.type == androidx.compose.ui.input.pointer.PointerType.Mouse
                            val hoverAllowed = com.android.purebilibili.feature.video.annotation
                                .shouldActivatePointerHoverPreview(
                                    isStylusOrMouse = isStylusOrMouse,
                                    isSeekScrubbing = isSeekScrubbing,
                                    durationMs = duration,
                                )
                            when (event.type) {
                                androidx.compose.ui.input.pointer.PointerEventType.Enter,
                                androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                    if (hoverAllowed && !change.pressed && size.width > 0) {
                                        hoverPositionMs = resolveSeekPositionFromTouch(
                                            touchX = change.position.x,
                                            containerWidthPx = size.width.toFloat(),
                                            durationMs = duration,
                                        )
                                    } else if (change.pressed) {
                                        hoverPositionMs = -1L
                                    }
                                }
                                androidx.compose.ui.input.pointer.PointerEventType.Exit -> {
                                    hoverPositionMs = -1L
                                }
                            }
                        }
                    }
                }
                .then(
                    if (seekGestureHost == VideoProgressBarSeekGestureHost.SharedOuterContainer) {
                        Modifier
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val targetPositionMs = resolveSeekPositionFromTouch(
                                        touchX = offset.x,
                                        containerWidthPx = size.width.toFloat(),
                                        durationMs = duration
                                    )
                                    dragTargetPositionMs = targetPositionMs
                                    currentOnSeekStart()
                                    currentOnSeekDragStart(targetPositionMs)
                                    currentOnSeekDragUpdate(targetPositionMs)
                                    if (seekHapticPolicy.start(targetPositionMs, duration, SystemClock.uptimeMillis())) {
                                        seekHaptic(HapticType.LIGHT)
                                    }
                                    seekHapticPolicy.finish()
                                    currentOnSeek(targetPositionMs)
                                }
                            }
                            .pointerInput(duration, horizontalSeekOnly) {
                                var dragInProgress = false
                                try {
                                    var dragStartPositionMs = displayPositionMs.coerceAtLeast(0L)
                                    var originPositionMs = currentPosition.coerceAtLeast(0L)
                                    var latestDragPositionMs = dragStartPositionMs
                                    var dragStartY = 0f
                                    var fineActive = false
                                    var fineAnchorPosMs = dragStartPositionMs
                                    var fineAnchorTouchX = 0f
                                    var hasLeftOrigin = false
                                    var cancelledAtOrigin = false
                                    var lastSnapKind = ProgressScrubSnapKind.NONE
                                    var lastSnapPositionMs = -1L
                                    val enterFinePx = FINE_SCRUB_ENTER_UPWARD_DP.dp.toPx()
                                    val exitFinePx = FINE_SCRUB_EXIT_UPWARD_DP.dp.toPx()
                                    val startDrag: (Offset) -> Unit = { offset ->
                                        val targetPositionMs = resolveSeekPositionFromTouch(
                                            touchX = offset.x,
                                            containerWidthPx = size.width.toFloat(),
                                            durationMs = duration
                                        )
                                        dragInProgress = true
                                        originPositionMs = (displayPositionProvider?.invoke() ?: currentPosition).coerceAtLeast(0L)
                                        dragStartPositionMs = targetPositionMs
                                        latestDragPositionMs = targetPositionMs
                                        dragTargetPositionMs = targetPositionMs
                                        dragStartY = offset.y
                                        fineActive = false
                                        fineAnchorPosMs = targetPositionMs
                                        fineAnchorTouchX = offset.x
                                        hasLeftOrigin = hasLeftOriginThreshold(targetPositionMs, originPositionMs, duration)
                                        cancelledAtOrigin = false
                                        lastSnapKind = ProgressScrubSnapKind.NONE
                                        lastSnapPositionMs = -1L
                                        isFineScrubbingActive = false
                                        activeSnapLabel = null
                                        currentOnSeekStart()
                                        currentOnSeekDragStart(targetPositionMs)
                                        if (seekHapticPolicy.start(targetPositionMs, duration, SystemClock.uptimeMillis())) {
                                            seekHaptic(HapticType.LIGHT)
                                        }
                                    }
                                    val updateDrag: (PointerInputChange) -> Unit = { change ->
                                        change.consume()
                                        val cfg = currentScrubbingConfig
                                        val upwardDragPx = dragStartY - change.position.y
                                        val nextFineActive = resolveFineScrubbingActive(
                                            currentlyActive = fineActive,
                                            enabled = cfg.fineScrubbingEnabled && !horizontalSeekOnly,
                                            upwardDragPx = upwardDragPx,
                                            enterThresholdPx = enterFinePx,
                                            exitThresholdPx = exitFinePx,
                                        )
                                        if (nextFineActive != fineActive) {
                                            fineActive = nextFineActive
                                            isFineScrubbingActive = nextFineActive
                                            if (nextFineActive) {
                                                fineAnchorPosMs = latestDragPositionMs
                                                fineAnchorTouchX = change.position.x
                                                seekRichHaptic(com.android.purebilibili.core.ui.AppRichHapticPattern.SEEK_SNAP)
                                            }
                                        }
                                        val rawTargetPositionMs = if (fineActive) {
                                            resolveFineScrubPositionMs(
                                                anchorPositionMs = fineAnchorPosMs,
                                                anchorTouchX = fineAnchorTouchX,
                                                currentTouchX = change.position.x,
                                                containerWidthPx = size.width.toFloat(),
                                                durationMs = duration,
                                            )
                                        } else {
                                            resolveSeekPositionFromTouch(
                                                touchX = change.position.x,
                                                containerWidthPx = size.width.toFloat(),
                                                durationMs = duration
                                            )
                                        }
                                        if (!hasLeftOrigin && hasLeftOriginThreshold(rawTargetPositionMs, originPositionMs, duration)) {
                                            hasLeftOrigin = true
                                        }
                                        val resolution = resolveProgressScrubTarget(
                                            rawTargetPositionMs = rawTargetPositionMs,
                                            originPositionMs = originPositionMs,
                                            durationMs = duration,
                                            hasLeftOrigin = hasLeftOrigin,
                                            fineScrubbingActive = fineActive,
                                            snapAndOriginCancelEnabled = cfg.snapAndOriginCancelEnabled,
                                            snapPoints = currentSnapPoints,
                                        )
                                        val targetPositionMs = resolution.positionMs
                                        cancelledAtOrigin = resolution.cancelledAtOrigin
                                        activeSnapLabel = resolution.snapLabel
                                        val enteredNewSnap = resolution.snapKind != ProgressScrubSnapKind.NONE &&
                                            (resolution.snapKind != lastSnapKind || resolution.positionMs != lastSnapPositionMs)
                                        lastSnapKind = resolution.snapKind
                                        lastSnapPositionMs = resolution.positionMs
                                        latestDragPositionMs = targetPositionMs
                                        dragTargetPositionMs = targetPositionMs
                                        currentOnSeekDragUpdate(targetPositionMs)
                                        if (enteredNewSnap) {
                                            seekRichHaptic(com.android.purebilibili.core.ui.AppRichHapticPattern.SEEK_SNAP)
                                        } else if (seekHapticPolicy.update(targetPositionMs, SystemClock.uptimeMillis())) {
                                            seekRichHaptic(com.android.purebilibili.core.ui.AppRichHapticPattern.SEEK_TICK)
                                        }
                                    }
                                    val endDrag: () -> Unit = {
                                        dragInProgress = false
                                        isFineScrubbingActive = false
                                        activeSnapLabel = null
                                        if (cancelledAtOrigin) {
                                            currentOnSeekDragCancel()
                                        } else {
                                            val commitPositionMs = resolveSeekDragCommitPositionMs(
                                                dragStartPositionMs = dragStartPositionMs,
                                                latestDragPositionMs = latestDragPositionMs
                                            )
                                            currentOnSeek(commitPositionMs)
                                        }
                                        seekHapticPolicy.finish()
                                    }
                                    val cancelDrag: () -> Unit = {
                                        dragInProgress = false
                                        isFineScrubbingActive = false
                                        activeSnapLabel = null
                                        currentOnSeekDragCancel()
                                        seekHapticPolicy.finish()
                                    }
                                    if (horizontalSeekOnly) {
                                        detectHorizontalDragGestures(
                                            onDragStart = startDrag,
                                            onHorizontalDrag = { change, _ -> updateDrag(change) },
                                            onDragEnd = endDrag,
                                            onDragCancel = cancelDrag,
                                        )
                                    } else {
                                        detectDragGestures(
                                            onDragStart = startDrag,
                                            onDrag = { change, _ -> updateDrag(change) },
                                            onDragEnd = endDrag,
                                            onDragCancel = cancelDrag,
                                        )
                                    }
                                } finally {
                                    if (shouldCancelSeekDragOnPointerInputCompletion(dragInProgress)) {
                                        currentOnSeekDragCancel()
                                        seekHapticPolicy.finish()
                                    }
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                if (currentChapter != null) {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .pointerInput(currentChapter) {
                                    detectTapGestures { onChapterClick() }
                                }
                                .padding(
                                    bottom = layoutPolicy.chapterBottomPaddingDp.dp,
                                    start = layoutPolicy.chapterStartPaddingDp.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                Icons.Outlined.ViewList,
                                contentDescription = "Chapter",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(layoutPolicy.chapterIconSizeDp.dp)
                            )
                            Spacer(modifier = Modifier.width(layoutPolicy.chapterSpacingDp.dp))
                            AppText(
                                text = currentChapter,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = layoutPolicy.chapterFontSp.sp,
                                lineHeight = layoutPolicy.chapterFontSp.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layoutPolicy.touchContainerHeightDp.dp * verticalHeightScale),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Spacer(
                        Modifier.fillMaxWidth()
                            .height(layoutPolicy.touchContainerHeightDp.dp * verticalHeightScale)
                            .drawWithCache {
                                val trackTop = ((size.height - trackHeightPx) / 2f).coerceAtLeast(0f)
                                val centerY = trackTop + trackHeightPx / 2f
                                val ridgeHeight = (size.height * 0.42f).coerceAtMost(18.dp.toPx())
                                val ridgePoints = pbpRidgeSamples.map { sample ->
                                    val intensity = when (sample.density) {
                                        PbpRidgeDensity.QUIET -> sample.intensity * 0.78f
                                        PbpRidgeDensity.NORMAL -> sample.intensity
                                        PbpRidgeDensity.HOT -> sample.intensity * 1.14f
                                    }.coerceIn(0f, 1f)
                                    Offset(size.width * sample.fraction.coerceIn(0f, 1f), centerY - ridgeHeight * intensity)
                                }
                                val ridgePath = Path().apply {
                                    moveTo(0f, centerY)
                                    ridgePoints.forEach { lineTo(it.x, it.y) }
                                    lineTo(size.width, centerY)
                                    close()
                                }
                                val ridgeLinePath = Path().apply {
                                    ridgePoints.forEachIndexed { index, point ->
                                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                                    }
                                }
                                val hotSegments = (0 until (ridgePoints.size - 1).coerceAtLeast(0)).filter { index ->
                                    pbpRidgeSamples[index].density == PbpRidgeDensity.HOT ||
                                        pbpRidgeSamples[index + 1].density == PbpRidgeDensity.HOT
                                }
                                val ridgeBrush = Brush.verticalGradient(
                                    listOf(primaryColor.copy(alpha = 0.18f), primaryColor.copy(alpha = 0.03f)),
                                    startY = centerY - ridgeHeight, endY = centerY
                                )
                                val ridgeGlow = Stroke(trackHeightPx * 2.8f, cap = StrokeCap.Round)
                                val ridgeStroke = Stroke(trackHeightPx * 0.9f, cap = StrokeCap.Round)
                                val sponsorLines = resolvedSponsorMarkers.map { marker ->
                                    Triple(Offset(size.width * marker.startFraction, centerY),
                                        Offset(size.width * marker.endFraction, centerY), marker.color)
                                }
                                val chapterXs = if (duration > 0L) viewPoints.mapNotNull { point ->
                                    resolveProgressFraction(point.fromMs, duration)
                                        .takeIf { it in 0.01f..0.99f }?.let { size.width * it }
                                } else emptyList()
                                val (abStartFraction, abEndFraction) = com.android.purebilibili.feature.video.playback.policy
                                    .resolveAbLoopProgressFractions(abLoopPointAMs, abLoopPointBMs, duration)
                                val abMarkerColor = Color(0xFF4FC3F7)
                                onDrawBehind {
                                    if (ridgePoints.size >= 2 && size.width > 0f) {
                                        drawPath(ridgePath, ridgeBrush)
                                        drawPath(ridgeLinePath, primaryColor.copy(alpha = 0.14f), style = ridgeGlow)
                                        drawPath(ridgeLinePath, primaryColor.copy(alpha = 0.46f), style = ridgeStroke)
                                        hotSegments.forEach { index ->
                                            drawLine(primaryColor.copy(alpha = 0.68f), ridgePoints[index],
                                                ridgePoints[index + 1], trackHeightPx * 1.25f, StrokeCap.Round)
                                        }
                                    }
                                    drawMediaProgressTrack(
                                        progressFraction = resolveProgressFraction(activePositionProvider(), duration),
                                        bufferedFraction = bufferedProgress,
                                        trackHeightPx = trackHeightPx,
                                        activeColor = primaryColor,
                                        bufferedColor = bufferedTrackColor,
                                        inactiveColor = inactiveTrackColor,
                                    )
                                    glassTrackHighlight?.draw(this, trackHeightPx)
                                    sponsorLines.forEach { (start, end, color) ->
                                        drawLine(color, start, end, trackHeightPx, StrokeCap.Round)
                                    }
                                    if (abStartFraction != null && abEndFraction != null && abEndFraction > abStartFraction) {
                                        drawLine(
                                            color = abMarkerColor.copy(alpha = 0.85f),
                                            start = Offset(size.width * abStartFraction, centerY),
                                            end = Offset(size.width * abEndFraction, centerY),
                                            strokeWidth = trackHeightPx * 1.35f,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                    listOfNotNull(abStartFraction, abEndFraction).forEach { fraction ->
                                        val x = size.width * fraction
                                        drawLine(
                                            color = abMarkerColor,
                                            start = Offset(x, trackTop - 4f),
                                            end = Offset(x, trackTop + trackHeightPx + 4f),
                                            strokeWidth = 2.5f,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                    chapterXs.forEach { x ->
                                        drawLine(Color.White.copy(alpha = 0.85f), Offset(x, trackTop - 2f),
                                            Offset(x, trackTop + trackHeightPx + 2f),
                                            if (isSeekScrubbing) 2f else 1.5f)
                                    }
                                }
                            }
                    )

                    if (duration > 0L && containerWidthPx > 0f) {
                        val thumbOffsetProvider: () -> Int = {
                            (containerWidthPx * resolveProgressFraction(activePositionProvider(), duration) - thumbSizePx / 2f)
                                .coerceIn(0f, (containerWidthPx - thumbSizePx).coerceAtLeast(0f))
                                .roundToInt()
                        }
                        if (progressThumbPath != null) {
                            UiSkinAnimatedAsset(
                                path = progressThumbPath,
                                size = thumbSizeDp,
                                iterations = if (isSeekScrubbing) Int.MAX_VALUE else 1,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset(thumbOffsetProvider(), 0) },
                                contentDescription = null,
                            )
                        } else if (LocalPlayerControlGlassEnabled.current) {
                            PlayerControlGlassThumb(
                                size = thumbSizeDp,
                                dragging = isSeekScrubbing,
                                modifier = Modifier.align(Alignment.CenterStart)
                                    .offset { IntOffset(thumbOffsetProvider(), 0) },
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset(thumbOffsetProvider(), 0) }
                                    .size(thumbSizeDp)
                                    .background(primaryColor, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSeekPreview(
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData?,
    positionProvider: () -> Long,
    currentPosition: Long,
    duration: Long,
    thumbCenterXPx: Float,
    containerWidthPx: Float,
    fineScrubbingActive: Boolean = false,
    statusLabel: String? = null,
) {
    val target = positionProvider()
    if (fineScrubbingActive) {
        SeekPreviewFilmstrip(
            videoshotData = videoshotData,
            targetPositionMs = target,
            currentPositionMs = currentPosition,
            durationMs = duration,
            statusLabel = statusLabel,
            modifier = Modifier.testTag("player_seek_preview"),
        )
        return
    }
    // 与竖屏 PortraitProgressBar 一致：预览跟随拖动 thumb；宽度未测量时回退居中。
    val placement = if (containerWidthPx > 0f) {
        SeekPreviewBubblePlacement.Anchored
    } else {
        SeekPreviewBubblePlacement.Centered
    }
    if (videoshotData != null && videoshotData.isValid) {
        SeekPreviewBubble(videoshotData = videoshotData, targetPositionMs = target,
            currentPositionMs = currentPosition, durationMs = duration, offsetX = thumbCenterXPx,
            containerWidth = containerWidthPx, placement = placement,
            statusLabel = statusLabel,
            modifier = Modifier.testTag("player_seek_preview"))
    } else {
        SeekPreviewBubbleSimple(targetPositionMs = target, currentPositionMs = currentPosition,
            offsetX = thumbCenterXPx, containerWidth = containerWidthPx, placement = placement,
            statusLabel = statusLabel,
            modifier = Modifier.testTag("player_seek_preview"))
    }
}
