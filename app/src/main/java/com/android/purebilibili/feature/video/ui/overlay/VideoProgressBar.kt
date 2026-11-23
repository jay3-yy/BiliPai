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
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeekDragStart by rememberUpdatedState(onSeekDragStart)
    val currentOnSeekDragUpdate by rememberUpdatedState(onSeekDragUpdate)
    val currentOnSeekDragCancel by rememberUpdatedState(onSeekDragCancel)
    // 拖动进度条刻度触感：拖起轻振，跨过整秒时短振，快速滑动按 60ms 限频。
    val seekHaptic = rememberHapticFeedback(AppHapticScene.PLAYER_SEEK)
    val seekHapticPolicy = remember { SeekTickHapticPolicy() }
    LaunchedEffect(displayPositionMs, isSeekScrubbing) {
        if (!isSeekScrubbing) {
            dragTargetPositionMs = displayPositionMs.coerceAtLeast(0L)
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
    val previewImageHeightDp = if (videoshotData?.isValid == true) {
        resolveSeekPreviewBubbleHeightDp(
            LocalConfiguration.current.screenWidthDp, LocalConfiguration.current.screenHeightDp,
        ).takeIf { it > 0 }
    } else null
    val previewAreaHeightDp = if (isSeekScrubbing) {
        resolveVideoProgressPreviewAreaHeightDp(
            layoutPolicy = layoutPolicy,
            hasChapter = currentChapter != null,
            previewImageHeightDp = previewImageHeightDp
        ).dp
    } else 0.dp
    val thumbSizeDp = if (isSeekScrubbing) {
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
        if (isSeekScrubbing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewAreaHeightDp)
                    .padding(bottom = layoutPolicy.previewBottomPaddingDp.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                ProgressSeekPreview(
                    videoshotData = videoshotData,
                    positionProvider = activePositionProvider,
                    currentPosition = currentPosition,
                    duration = duration,
                    thumbCenterXPx = containerWidthPx * resolveProgressFraction(
                        positionMs = activePositionProvider(),
                        durationMs = duration
                    ),
                    containerWidthPx = containerWidthPx
                )

            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(baseHeightDp)
                .testTag("player_progress_seek_area")
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
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
                                    var latestDragPositionMs = dragStartPositionMs
                                    val startDrag: (Offset) -> Unit = { offset ->
                                        val targetPositionMs = resolveSeekPositionFromTouch(
                                            touchX = offset.x,
                                            containerWidthPx = size.width.toFloat(),
                                            durationMs = duration
                                        )
                                        dragInProgress = true
                                        dragStartPositionMs = targetPositionMs
                                        latestDragPositionMs = targetPositionMs
                                        dragTargetPositionMs = targetPositionMs
                                        currentOnSeekStart()
                                        currentOnSeekDragStart(targetPositionMs)
                                        if (seekHapticPolicy.start(targetPositionMs, duration, SystemClock.uptimeMillis())) {
                                            seekHaptic(HapticType.LIGHT)
                                        }
                                    }
                                    val updateDrag: (PointerInputChange) -> Unit = { change ->
                                        change.consume()
                                        val targetPositionMs = resolveSeekPositionFromTouch(
                                            touchX = change.position.x,
                                            containerWidthPx = size.width.toFloat(),
                                            durationMs = duration
                                        )
                                        latestDragPositionMs = targetPositionMs
                                        dragTargetPositionMs = targetPositionMs
                                        currentOnSeekDragUpdate(targetPositionMs)
                                        if (seekHapticPolicy.update(targetPositionMs, SystemClock.uptimeMillis())) {
                                            seekHaptic(HapticType.GESTURE_STEP)
                                        }
                                    }
                                    val endDrag: () -> Unit = {
                                        val commitPositionMs = resolveSeekDragCommitPositionMs(
                                            dragStartPositionMs = dragStartPositionMs,
                                            latestDragPositionMs = latestDragPositionMs
                                        )
                                        dragInProgress = false
                                        currentOnSeek(commitPositionMs)
                                        seekHapticPolicy.finish()
                                    }
                                    val cancelDrag: () -> Unit = {
                                        dragInProgress = false
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
    containerWidthPx: Float
) {
    val target = positionProvider()
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
            modifier = Modifier.testTag("player_seek_preview"))
    } else {
        SeekPreviewBubbleSimple(targetPositionMs = target, currentPositionMs = currentPosition,
            offsetX = thumbCenterXPx, containerWidth = containerWidthPx, placement = placement,
            modifier = Modifier.testTag("player_seek_preview"))
    }
}
