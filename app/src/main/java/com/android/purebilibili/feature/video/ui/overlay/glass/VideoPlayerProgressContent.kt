package com.android.purebilibili.feature.video.ui.overlay.glass

import com.android.purebilibili.feature.video.ui.overlay.*

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.data.model.response.VideoshotData
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import com.android.purebilibili.feature.video.ui.components.glass.ChapterListPanel

/** Each consumer reads progress in its own restart scope, outside the control bar shell. */
@Composable
internal fun PlayerControlProgressBar(
    progressProvider: () -> PlayerProgress,
    displayPositionProvider: () -> Long,
    isSeekScrubbing: Boolean,
    layoutPolicy: VideoProgressBarLayoutPolicy,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekDragStart: (Long) -> Unit,
    onSeekDragUpdate: (Long) -> Unit,
    onSeekDragCancel: () -> Unit,
    videoshotData: VideoshotData?,
    viewPoints: List<ViewPoint>,
    sponsorMarkers: List<SponsorProgressMarker>,
    pbpRidgeSamples: List<PbpRidgeSample>,
    currentChapterProvider: () -> String?,
    onChapterClick: () -> Unit,
    modifier: Modifier = Modifier,
    neutralGlassTrack: Boolean = false,
) {
    val progress = progressProvider()
    VideoProgressBar(
        currentPosition = progress.current,
        displayPositionMs = displayPositionProvider(),
        displayPositionProvider = displayPositionProvider,
        duration = progress.duration,
        bufferedPosition = progress.buffered,
        isSeekScrubbing = isSeekScrubbing,
        layoutPolicy = layoutPolicy,
        onSeek = onSeek,
        onSeekStart = onSeekStart,
        onSeekDragStart = onSeekDragStart,
        onSeekDragUpdate = onSeekDragUpdate,
        onSeekDragCancel = onSeekDragCancel,
        videoshotData = videoshotData,
        viewPoints = viewPoints,
        sponsorMarkers = sponsorMarkers,
        pbpRidgeSamples = pbpRidgeSamples,
        currentChapter = currentChapterProvider(),
        onChapterClick = onChapterClick,
        modifier = modifier,
        neutralGlassTrack = neutralGlassTrack,
    )
}

@Composable
internal fun PlayerControlViewPointSegments(
    progressProvider: () -> PlayerProgress,
    viewPoints: List<ViewPoint>,
    onSeek: (Long) -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier,
    positionProvider: (() -> Long)? = null,
) {
    if (viewPoints.isEmpty()) return
    val progress = progressProvider()
    if (progress.duration <= 0L) return
    ViewPointSegmentBar(
        viewPoints = viewPoints,
        durationMs = progress.duration,
        currentPositionMs = progress.current,
        positionProvider = positionProvider,
        onSeek = onSeek,
        modifier = modifier,
    )
    Spacer(modifier = Modifier.height(spacing))
}

@Composable
internal fun ProgressTimeText(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    fontSp: Int,
) {
    val seconds by remember(positionProvider) { derivedStateOf { positionProvider() / 1000L } }
    val durationSeconds by remember(durationProvider) { derivedStateOf { durationProvider() / 1000L } }
    AppText(
        text = "${FormatUtils.formatDuration(seconds.toInt())} / ${FormatUtils.formatDuration(durationSeconds.toInt())}",
        color = Color.White.copy(alpha = 0.9f),
        fontSize = fontSp.sp,
        lineHeight = (fontSp + 2).sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun OverlayPersistentProgressBar(
    progressProvider: () -> PlayerProgress,
    modifier: Modifier = Modifier,
) {
    val progress = progressProvider()
    PersistentBottomProgressBar(
        current = progress.current,
        duration = progress.duration,
        modifier = modifier,
    )
}

@Composable
internal fun OverlayChapterListPanel(
    progressProvider: () -> PlayerProgress,
    viewPoints: List<ViewPoint>,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    isFullscreen: Boolean = false,
    showViewPoints: Boolean = true,
    onShowViewPointsChange: ((Boolean) -> Unit)? = null,
) {
    // 横屏全屏共用可拖动侧栏，仅材质由玻璃入口显式选择。
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    if (isFullscreen && windowSize.width > windowSize.height) {
        com.android.purebilibili.feature.video.ui.components.ChapterListPanel(
            viewPoints = viewPoints,
            currentPositionMs = progressProvider().current,
            onSeek = onSeek,
            onDismiss = onDismiss,
            isFullscreen = true,
            useGlassSurface = true,
            showViewPoints = showViewPoints,
            onShowViewPointsChange = onShowViewPointsChange,
        )
        return
    }
    ChapterListPanel(
        viewPoints = viewPoints,
        currentPositionMs = progressProvider().current,
        onSeek = onSeek,
        onDismiss = onDismiss,
    )
}
