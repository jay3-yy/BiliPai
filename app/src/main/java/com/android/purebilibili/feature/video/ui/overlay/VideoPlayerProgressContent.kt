package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.data.model.response.VideoshotData
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import com.android.purebilibili.feature.video.ui.components.ChapterListPanel
import com.android.purebilibili.feature.video.ui.components.PlayerListPopupPlacement

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
    seekAreaHeight: Dp? = null,
    verticalHeightScale: Float = 1f,
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
        seekAreaHeight = seekAreaHeight,
        verticalHeightScale = verticalHeightScale,
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
    interactive: Boolean = true,
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
        interactive = interactive,
    )
    Spacer(modifier = Modifier.height(spacing))
}

@Composable
internal fun ProgressTimeText(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val seconds by remember(positionProvider) { derivedStateOf { positionProvider() / 1000L } }
    val durationSeconds by remember(durationProvider) { derivedStateOf { durationProvider() / 1000L } }
    val duration = FormatUtils.formatDuration(durationSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    val style = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Default,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val width = remember(duration, measurer, density) {
        with(density) { measurer.measure(duration, style).size.width.toDp() }
    }
    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(
            text = FormatUtils.formatDuration(seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
            color = Color.White, style = style, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(), maxLines = 1, softWrap = false, tapToCopyEnabled = false,
        )
        AppText(
            text = duration, color = Color(0xFFD0D0D0), style = style,
            maxLines = 1, softWrap = false, tapToCopyEnabled = false,
        )
    }
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
    placement: PlayerListPopupPlacement = PlayerListPopupPlacement.START,
    anchorBounds: IntRect? = null,
    isFullscreen: Boolean = false,
    playerBounds: IntRect? = null,
    showViewPoints: Boolean = true,
    onShowViewPointsChange: ((Boolean) -> Unit)? = null,
) {
    ChapterListPanel(
        viewPoints = viewPoints,
        currentPositionMs = progressProvider().current,
        onSeek = onSeek,
        onDismiss = onDismiss,
        placement = placement,
        anchorBounds = anchorBounds,
        isFullscreen = isFullscreen,
        playerBounds = playerBounds,
        showViewPoints = showViewPoints,
        onShowViewPointsChange = onShowViewPointsChange,
    )
}

/** 常驻章节的可见条贴底，点击区域向上扩展；锁屏时仅展示。 */
@Composable
internal fun OverlayPersistentChapterBar(
    progressProvider: () -> PlayerProgress,
    viewPoints: List<ViewPoint>,
    onChapterClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {},
) {
    val latestOnHeightChanged by rememberUpdatedState(onHeightChanged)
    val progress = progressProvider()
    if (viewPoints.isEmpty() || progress.duration <= 0L) return
    DisposableEffect(Unit) {
        onDispose { latestOnHeightChanged(0) }
    }
    Box(
        modifier = modifier
            .onSizeChanged { latestOnHeightChanged(it.height) }
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "查看完整章节",
                onClick = onChapterClick,
            )
            .heightIn(min = 40.dp),
        // 保留点击高度，但可见章节条贴住底边，不在条下方留空。
        contentAlignment = Alignment.BottomCenter,
    ) {
        ViewPointSegmentBar(
            viewPoints = viewPoints,
            durationMs = progress.duration,
            currentPositionMs = progress.current,
            onSeek = {},
            interactive = false,
        )
    }
}
