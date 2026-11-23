package com.android.purebilibili.feature.audio.lyrics.halcyon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A native, independently implemented focus-lyrics renderer. */
@Composable
internal fun AppleMusicLyricsView(
    lyrics: List<HalcyonLyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float = 1f,
    isPaused: Boolean = !isPlaying,
    brightenAllLinesWhenPaused: Boolean? = null,
    pageVisible: Boolean = true,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    lyricTextAlign: Int,
    contentColor: Color,
    wordLiftEnabled: Boolean = true,
    onLineClick: (HalcyonLyricLine) -> Unit,
    onLineDoubleClick: (() -> Unit)? = null,
    onLineLongClick: (HalcyonLyricLine) -> Unit,
    topContentPadding: Dp = 72.dp,
    bottomContentPadding: Dp = 132.dp,
    startContentPadding: Dp = 0.dp,
    endContentPadding: Dp = 0.dp,
    lineSpacing: Dp = 25.dp,
    focusOffsetRatio: Float = 0.24f,
    focusOffsetNudgeDp: Dp = 0.dp,
    focusOffsetDp: Dp? = null,
    useFocusLeadingPadding: Boolean = true,
    nonCurrentLineBlurEnabled: Boolean = true,
    userScrollEnabled: Boolean = true,
    reserveExtraLyricSpace: Boolean = false,
    singleLine: Boolean = false,
    followWordFocus: Boolean = false,
    showBackgroundText: Boolean = true,
    arcPerspectiveEnabled: Boolean = false,
    arcViewportProfile: ArcLyricViewportProfile? = null,
    reduceMotion: Boolean = false,
    linePresentation: ((Int, HalcyonLyricLine) -> MiniLyricLinePresentation?)? = null,
    modifier: Modifier = Modifier
) {
    val pronunciationBelow = HalcyonLyricSettings.lyricPronunciationBelow
    val sustainThresholdMs = HalcyonLyricSettings.appleMusicLyricsSustainThresholdMs
    val nonCurrentLineBlurPercent = HalcyonLyricSettings.lyricNonCurrentBlurPercent
    val wordSeekEnabled = HalcyonLyricSettings.lyricWordSeekEnabled
    val effectiveLineDoubleClick = onLineDoubleClick.takeIf {
        lyricLineDoubleTapEnabled(wordSeekEnabled)
    }
    val touchFeedbackEnabled = HalcyonLyricSettings.lyricTouchFeedbackEnabled
    val pauseCurrentOnly = HalcyonLyricSettings.lyricPauseCurrentOnly
    val revealAllLinesWhilePaused = brightenAllLinesWhenPaused ?: !pauseCurrentOnly
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            BasicText(
                text = "♪",
                style = TextStyle(fontSize = 28.sp, color = contentColor.copy(alpha = 0.58f), fontFamily = fontFamily)
            )
        }
        return
    }

    // Some files use a shared 00:00 timestamp for static credits / instrumental notices. They
    // are not a scrolling timeline: render every row as a readable, centered card instead of
    // pinning the first row to the normal lyric focus offset.
    val singleTimestampTimeline = lyrics.firstOrNull()?.timeMs?.let { timestamp ->
        lyrics.all { it.timeMs == timestamp }
    } == true
    if (singleTimestampTimeline) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(lineSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                lyrics.forEachIndexed { index, line ->
                    val presentation = linePresentation?.invoke(index, line)
                    AppleMusicLyricLine(
                        line = line,
                        active = true,
                        paused = true,
                        distance = 0,
                        userScrolling = true,
                        nonCurrentLineBlurEnabled = false,
                        currentPositionMs = Long.MIN_VALUE,
                        showTranslation = presentation?.showTranslation ?: showTranslation,
                        showPronunciation = presentation?.showPronunciation ?: showPronunciation,
                        pronunciationBelow = pronunciationBelow,
                        fontFamily = fontFamily,
                        translationFontFamily = translationFontFamily,
                        fontWeight = fontWeight,
                        fontScale = fontScale,
                        secondaryFontScale = secondaryFontScale,
                        primaryTextSizeSp = primaryTextSizeSp,
                        secondaryTextSizeSp = secondaryTextSizeSp,
                        defaultTextAlign = TextAlign.Center,
                        contentColor = contentColor,
                        wordLiftEnabled = false,
                        sustainThresholdMs = sustainThresholdMs,
                        reserveExtraLyricSpace = false,
                        singleLine = singleLine,
                        followWordFocus = followWordFocus,
                        showBackgroundText = presentation?.showBackgroundText ?: showBackgroundText,
                        showPrimaryText = presentation?.showPrimaryText ?: true,
                        onClick = { onLineClick(line) },
                        onDoubleClick = effectiveLineDoubleClick,
                        onLongClick = { onLineLongClick(line) },
                        onWordClick = if (wordSeekEnabled && !line.isOpeningMetadata) {
                            { positionMs -> onLineClick(line.copy(timeMs = positionMs)) }
                        } else null,
                        onTapFraction = line.openingSeekHandler(onLineClick),
                        touchFeedbackEnabled = touchFeedbackEnabled
                    )
                }
            }
        }
        return
    }

    val interludes = remember(lyrics) { lyrics.interludes() }
    val initialActiveIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val initialActiveInterlude = interludes.firstOrNull { it.isActiveAt(currentPositionMs) }
    fun hasVisibleBackground(index: Int): Boolean {
        val line = lyrics.getOrNull(index) ?: return false
        val presentation = linePresentation?.invoke(index, line)
        return showBackgroundText &&
            (presentation?.showBackgroundText ?: true) &&
            line.text.isNotBlank() &&
            !line.backgroundText.isNullOrBlank()
    }
    val initialBackgroundFocusIndex = resolveAppleMusicLyricsBackgroundFocusIndex(
        activeLyricIndex = initialActiveIndex,
        lyricCount = lyrics.size,
        hasBackground = hasVisibleBackground(initialActiveIndex)
    )
    val initialScrollTargetIndex = resolveAppleMusicLyricsScrollTargetIndex(
        activeLyricIndex = initialActiveIndex,
        activeInterlude = initialActiveInterlude,
        interludes = interludes,
        backgroundFocusLineIndex = initialBackgroundFocusIndex
    )
    // Start at the currently playing row. Waiting for the first post-layout effect while the
    // state still points at item 0 makes the lyric page flash the beginning of the song first.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollTargetIndex
    )
    val scrollSpring = remember { Animatable(0f) }
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    var trailingLineHeightPx by remember(lyrics) { mutableIntStateOf(0) }
    var hasPositionedScroll by remember(lyrics) { mutableStateOf(false) }
    var deferAutoScroll by remember { mutableStateOf(false) }
    LaunchedEffect(userDragging) {
        if (userDragging) {
            deferAutoScroll = true
        } else if (deferAutoScroll) {
            // ConePlayer keeps the user's reading position briefly before returning to the
            // current line. Its LyricView uses a 2-second delayed recenter message.
            delay(MANUAL_SCROLL_RECENTER_DELAY_MS)
            deferAutoScroll = false
        }
    }
    val renderIsPlaying = isPlaying && pageVisible
    var keepLinesSharp by remember { mutableStateOf(!isPlaying) }
    LaunchedEffect(userDragging, isPlaying) {
        when {
            !isPlaying -> keepLinesSharp = true
            userDragging -> keepLinesSharp = true
            else -> {
                delay(MANUAL_SCROLL_BLUR_RESUME_DELAY_MS)
                keepLinesSharp = false
            }
        }
    }
    var smoothPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    val latestCurrentPositionMs by rememberUpdatedState(currentPositionMs)
    val latestPlaying by rememberUpdatedState(renderIsPlaying)
    val latestPlaybackSpeed by rememberUpdatedState(playbackSpeed)

    LaunchedEffect(currentPositionMs, pageVisible, renderIsPlaying) {
        if (!pageVisible || !renderIsPlaying) {
            smoothPositionMs = currentPositionMs
        }
    }

    LaunchedEffect(currentIndex) {
        val line = lyrics.getOrNull(currentIndex) ?: return@LaunchedEffect
        val startMs = line.timeMs
        val endMs = line.endMs
            ?: line.words.maxOfOrNull { it.endMs }
            ?: line.backgroundEndMs
            ?: (startMs + 4_000L)
        val sampled = latestCurrentPositionMs
        smoothPositionMs = when {
            sampled in startMs until endMs.coerceAtLeast(startMs + 1L) -> sampled
            smoothPositionMs < startMs -> startMs
            else -> smoothPositionMs
        }
    }
    // Keep one frame-clock loop for the lifetime of this lyric list. Keying it on the 10 Hz
    // player sample (or word-lift) cancelled interpolation every tick and made the karaoke
    // fill jump like a slideshow.
    LaunchedEffect(lyrics, pageVisible, renderIsPlaying) {
        // A retained/paused lyrics page must not keep a frame-clock coroutine alive. Snap to the
        // latest sample so reopening starts from the correct line, then let the active playing
        // page resume the smooth karaoke clock below.
        if (!pageVisible || !renderIsPlaying) {
            smoothPositionMs = latestCurrentPositionMs
            return@LaunchedEffect
        }
        var lastFrameNs = 0L
        while (true) {
            val frameNs = withFrameNanos { it }
            val sampled = latestCurrentPositionMs
            val playing = latestPlaying
            if (lastFrameNs == 0L) {
                lastFrameNs = frameNs
                smoothPositionMs = sampled
                continue
            }
            val dtMs = (frameNs - lastFrameNs) / 1_000_000L
            lastFrameNs = frameNs
            smoothPositionMs = nextSmoothLyricPositionMs(
                displayMs = smoothPositionMs,
                sampledMs = sampled,
                frameDeltaMs = dtMs,
                playing = playing,
                playbackSpeed = latestPlaybackSpeed,
            )
        }
    }
    // Reading the frame clock in this composable's own body recomposed the whole lyric view --
    // and rebuilt the LazyColumn's interval content for every line of the song -- on every one
    // of the 120 ticks a second. Derive the interlude instead: it changes a handful of times per
    // song, and the per-frame reads below now sit inside the item bodies that actually need them.
    val activeInterlude by remember(interludes) {
        derivedStateOf { interludes.firstOrNull { it.isActiveAt(smoothPositionMs) } }
    }
    val activeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val backgroundFocusIndex = resolveAppleMusicLyricsBackgroundFocusIndex(
        activeLyricIndex = activeIndex,
        lyricCount = lyrics.size,
        hasBackground = hasVisibleBackground(activeIndex)
    )
    val scrollTargetIndex = resolveAppleMusicLyricsScrollTargetIndex(
        activeLyricIndex = activeIndex,
        activeInterlude = activeInterlude,
        interludes = interludes,
        backgroundFocusLineIndex = backgroundFocusIndex
    )
    val density = LocalDensity.current
    val focusOffsetNudgePx = with(density) { focusOffsetNudgeDp.toPx() }
    val focusOffsetPx = focusOffsetDp?.let { with(density) { it.toPx() } }
    LaunchedEffect(pageVisible, scrollTargetIndex, userDragging, deferAutoScroll, focusOffsetNudgePx, focusOffsetPx) {
        if (userDragging || deferAutoScroll) return@LaunchedEffect
        // Do not issue the first scroll before LazyColumn has a viewport; that was making the
        // focus line land under the page header until the user manually scrolled.
        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.filter { it > 0 }.first()
        val desiredItemOffset = if (focusOffsetPx != null) {
            (focusOffsetPx - focusOffsetNudgePx).coerceAtLeast(0f)
        } else {
            (viewportHeight * focusOffsetRatio - focusOffsetNudgePx).coerceAtLeast(0f)
        }

        if (!hasPositionedScroll) {
            // Initial positioning should not fly through the whole song when the player is
            // restored in the middle of a track.
            listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            scrollSpring.snapTo(0f)
            hasPositionedScroll = true
            return@LaunchedEffect
        }

        if (!pageVisible) {
            listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            scrollSpring.snapTo(0f)
            return@LaunchedEffect
        }

        // ConePlayer does not restart a fixed-duration list animation for each lyric. It changes
        // every row's spring target (damping 1.25, stiffness 200) and lets the retained velocity
        // carry the content into place. Drive the LazyColumn with the same overdamped spring and
        // correct the distance after variable-height rows have entered the viewport.
        // Jumping with scrollToItem on every line change was the post-1.2.4 stutter.
        repeat(CONE_SCROLL_CORRECTION_PASSES) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@repeat
            val targetItem = visibleItems.firstOrNull { it.index == scrollTargetIndex }
            val distance = if (targetItem != null) {
                targetItem.offset - desiredItemOffset
            } else {
                val firstItem = visibleItems.first()
                val averageItemExtent = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size +
                    layoutInfo.mainAxisItemSpacing
                firstItem.offset - desiredItemOffset +
                    (scrollTargetIndex - firstItem.index) * averageItemExtent
            }
            if (abs(distance) <= CONE_SCROLL_VISIBILITY_THRESHOLD_PX) return@LaunchedEffect

            // Keep the current spring value as a cumulative scroll offset. When a new line
            // arrives, Animatable retargets the same spring and carries its velocity into the
            // next row, which is the characteristic ConePlayer transition. Resetting this value
            // for every lyric change turns the motion into a series of imperceptible snaps.
            val animationStart = scrollSpring.value
            var appliedValue = animationStart
            // Move the list with dispatchRawDelta rather than listState.scroll {}. Opening a
            // scroll session raises isScrollInProgress, and Compose's scrollable then starts its
            // drag on the down event without waiting for the orientation touch slop. This spring
            // is overdamped and long, so it was still settling when the user's first swipes
            // landed: the vertical list claimed them outright and the pager never saw the
            // horizontal gesture meant to page back to the cover. A raw dispatch moves the same
            // pixels without owning the gesture, so the slop test decides the axis again.
            scrollSpring.animateTo(
                targetValue = animationStart + distance,
                animationSpec = spring(
                    dampingRatio = CONE_SCROLL_DAMPING_RATIO,
                    stiffness = CONE_SCROLL_STIFFNESS,
                    visibilityThreshold = CONE_SCROLL_VISIBILITY_THRESHOLD_PX
                )
            ) {
                // A real scroll session can now only come from the user (a drag or its fling).
                // Skip the frames it owns instead of fighting it for the same pixels; the
                // effect above restarts on userDragging anyway.
                if (listState.isScrollInProgress) {
                    appliedValue = value
                    return@animateTo
                }
                val consumed = listState.dispatchRawDelta(value - appliedValue)
                appliedValue += consumed
            }
        }
    }
    val defaultTextAlign = when (lyricTextAlign) {
        PLAYER_LYRIC_ALIGN_CENTER -> TextAlign.Center
        PLAYER_LYRIC_ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val trailingLineHeight = with(LocalDensity.current) { trailingLineHeightPx.toDp() }
        // The first lyric has no preceding rows that LazyColumn can scroll through. Reserve its
        // focus offset as actual leading content so 00:00 lyrics land at the same visual anchor
        // instead of sticking to the top edge of compact/immersive lyric viewports.
        val leadingFocusPadding = if (useFocusLeadingPadding) {
            resolveAppleMusicLyricsLeadingPadding(
                viewportHeight = maxHeight,
                focusOffsetRatio = focusOffsetRatio,
                focusOffsetNudge = focusOffsetNudgeDp,
                minimumTopPadding = topContentPadding,
                fixedFocusOffset = focusOffsetDp
            )
        } else {
            topContentPadding
        }
        // The mini preview is a bounded, non-scrollable line window. Adding enough trailing
        // padding to scroll the final row to the normal focus offset leaves a large blank tail
        // under the lyrics (and pushes the waveform/action area down). Only the full, scrollable
        // lyrics page needs that final-row affordance.
        val trailingFocusPadding = if (userScrollEnabled) {
            resolveAppleMusicLyricsTrailingPadding(
                viewportHeight = maxHeight,
                focusOffsetRatio = focusOffsetRatio,
                focusOffsetNudge = focusOffsetNudgeDp,
                trailingLineHeight = trailingLineHeight,
                minimumBottomPadding = bottomContentPadding,
                fixedFocusOffset = focusOffsetDp
            )
        } else {
            bottomContentPadding
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = startContentPadding,
                top = leadingFocusPadding,
                end = endContentPadding,
                bottom = trailingFocusPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(lineSpacing),
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (arcPerspectiveEnabled) {
                        Modifier
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                val topFadePx = minOf(84.dp.toPx(), size.height * 0.24f)
                                val bottomFadePx = minOf(72.dp.toPx(), size.height * 0.20f)
                                if (topFadePx > 0f) {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black),
                                            startY = 0f,
                                            endY = topFadePx,
                                        ),
                                        size = Size(size.width, topFadePx),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                                if (bottomFadePx > 0f) {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startY = size.height - bottomFadePx,
                                            endY = size.height,
                                        ),
                                        topLeft = Offset(0f, size.height - bottomFadePx),
                                        size = Size(size.width, bottomFadePx),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            lyrics.forEachIndexed { index, line ->
                interludes.firstOrNull { it.nextLineIndex == index }?.let { interlude ->
                    item(key = "interlude-${interlude.startMs}-${interlude.endMs}") {
                        AppleMusicInterlude(
                            interlude = interlude,
                            positionMs = smoothPositionMs,
                            contentColor = contentColor,
                            textAlign = line.duetTextAlign(defaultTextAlign),
                            touchFeedbackEnabled = touchFeedbackEnabled,
                            onSeek = { positionMs ->
                                onLineClick(HalcyonLyricLine(timeMs = positionMs, text = ""))
                            }
                        )
                    }
                }
                val itemKey = "${line.timeMs}-$index"
                item(key = itemKey) {
                    val duetActive = line.isDuetLine() && line.isActiveAt(smoothPositionMs)
                    val lineIsActive = activeInterlude == null && (index == activeIndex || duetActive)
                    val presentation = linePresentation?.invoke(index, line)
                    val rowModifier = Modifier
                        .then(
                            if (arcPerspectiveEnabled) {
                                Modifier.arcLyricRowTransform(
                                    listState = listState,
                                    itemKey = itemKey,
                                    focusOffsetRatio = focusOffsetRatio,
                                    reduceMotion = reduceMotion,
                                    viewportProfile = arcViewportProfile,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (index == lyrics.lastIndex) {
                                Modifier.onSizeChanged { trailingLineHeightPx = it.height }
                            } else {
                                Modifier
                            }
                        )
                    AppleMusicLyricLine(
                        line = line,
                        active = lineIsActive,
                        paused = isPaused && revealAllLinesWhilePaused,
                        distance = (index - activeIndex).coerceIn(-4, 4),
                        userScrolling = userDragging || keepLinesSharp,
                        nonCurrentLineBlurEnabled = nonCurrentLineBlurEnabled && isPlaying,
                        nonCurrentLineBlurPercent = nonCurrentLineBlurPercent,
                        // Do not invalidate every retained LazyColumn row for every playback tick.
                        // Only the active (or simultaneous duet) line needs a changing karaoke position.
                        currentPositionMs = if (lineIsActive) smoothPositionMs else Long.MIN_VALUE,
                        showTranslation = presentation?.showTranslation ?: showTranslation,
                        showPronunciation = presentation?.showPronunciation ?: showPronunciation,
                        pronunciationBelow = pronunciationBelow,
                        fontFamily = fontFamily,
                        translationFontFamily = translationFontFamily,
                        fontWeight = fontWeight,
                        fontScale = fontScale,
                        secondaryFontScale = secondaryFontScale,
                        primaryTextSizeSp = primaryTextSizeSp,
                        secondaryTextSizeSp = secondaryTextSizeSp,
                        defaultTextAlign = defaultTextAlign,
                        contentColor = contentColor,
                        wordLiftEnabled = wordLiftEnabled,
                        sustainThresholdMs = sustainThresholdMs,
                        reserveExtraLyricSpace = reserveExtraLyricSpace,
                        singleLine = singleLine,
                        followWordFocus = followWordFocus,
                        showBackgroundText = presentation?.showBackgroundText ?: showBackgroundText,
                        showPrimaryText = presentation?.showPrimaryText ?: true,
                        onClick = { onLineClick(line) },
                        onDoubleClick = effectiveLineDoubleClick,
                        onLongClick = { onLineLongClick(line) },
                        onWordClick = if (wordSeekEnabled && !line.isOpeningMetadata) {
                            { positionMs -> onLineClick(line.copy(timeMs = positionMs)) }
                        } else null,
                        onTapFraction = line.openingSeekHandler(onLineClick),
                        touchFeedbackEnabled = touchFeedbackEnabled,
                        modifier = rowModifier
                    )
                }
            }
        }
    }
}

internal fun lyricLineDoubleTapEnabled(wordSeekEnabled: Boolean): Boolean = !wordSeekEnabled

private fun HalcyonLyricLine.openingSeekHandler(
    onLineClick: (HalcyonLyricLine) -> Unit
): ((Float) -> Unit)? {
    if (resolveOpeningLyricSeekPosition(this, 0f) == null) return null
    return { fraction ->
        resolveOpeningLyricSeekPosition(this, fraction)?.let { positionMs ->
            onLineClick(copy(timeMs = positionMs))
        }
    }
}

internal fun resolveOpeningLyricSeekPosition(line: HalcyonLyricLine, fraction: Float): Long? {
    val endMs = line.endMs?.takeIf { line.isOpeningMetadata && it > line.timeMs } ?: return null
    return line.timeMs + ((endMs - line.timeMs) * fraction.coerceIn(0f, 1f)).toLong()
}

internal fun resolveAppleMusicLyricsLeadingPadding(
    viewportHeight: Dp,
    focusOffsetRatio: Float,
    minimumTopPadding: Dp,
    focusOffsetNudge: Dp = 0.dp,
    fixedFocusOffset: Dp? = null
): Dp = maxOf(
    minimumTopPadding,
    ((fixedFocusOffset ?: (viewportHeight * focusOffsetRatio.coerceIn(0f, 1f))) - focusOffsetNudge).coerceAtLeast(0.dp)
)

/**
 * Leaves enough scrollable space after the final lyric for its top edge to reach the same
 * focus offset used by the rest of the list.  A fixed bottom inset only works for short
 * viewports or short final rows; translated and wrapped final rows otherwise stop at the
 * system navigation area.
 */
internal fun resolveAppleMusicLyricsTrailingPadding(
    viewportHeight: Dp,
    focusOffsetRatio: Float,
    trailingLineHeight: Dp,
    minimumBottomPadding: Dp,
    focusOffsetNudge: Dp = 0.dp,
    fixedFocusOffset: Dp? = null
): Dp {
    val offset = fixedFocusOffset ?: (viewportHeight * focusOffsetRatio.coerceIn(0f, 1f))
    val requiredPadding = (
        viewportHeight - offset + focusOffsetNudge - trailingLineHeight
    ).coerceAtLeast(0.dp)
    return maxOf(minimumBottomPadding, requiredPadding)
}

internal fun resolveAppleMusicLyricsFocusOffset(
    viewportHeightPx: Int,
    focusOffsetRatio: Float,
    itemHeightPx: Int
): Int {
    val preferredOffset = (viewportHeightPx * focusOffsetRatio.coerceIn(0f, 1f)).roundToInt()
    val maximumOffset = (viewportHeightPx - itemHeightPx).coerceAtLeast(0)
    return preferredOffset.coerceIn(0, maximumOffset)
}

internal fun resolveAppleMusicLyricsScrollTargetIndex(
    activeLyricIndex: Int,
    activeInterlude: AppleMusicInterlude?,
    interludes: List<AppleMusicInterlude>,
    backgroundFocusLineIndex: Int? = null
): Int {
    activeInterlude?.let { interlude ->
        return interlude.nextLineIndex + interludes.count { it.nextLineIndex < interlude.nextLineIndex }
    }
    val sourceIndex = backgroundFocusLineIndex ?: activeLyricIndex
    return sourceIndex + interludes.count { it.nextLineIndex <= sourceIndex }
}

/**
 * x-bg is rendered inside its original lyric row, but the row grows when the backing vocal
 * appears. Keep the next original line at the focus position for the whole source row once a
 * backing vocal is present. This mirrors the waiting-dot treatment and, importantly, does not
 * snap back after the x-bg animation finishes; consecutive x-bg rows advance one line at a time.
 */
internal fun resolveAppleMusicLyricsBackgroundFocusIndex(
    activeLyricIndex: Int,
    lyricCount: Int,
    hasBackground: Boolean
): Int? = null

internal fun nextSmoothLyricPositionMs(
    displayMs: Long,
    sampledMs: Long,
    frameDeltaMs: Long,
    playing: Boolean,
    seekThresholdMs: Long = 1_500L,
    backwardToleranceMs: Long = PLAYER_POSITION_BACKWARD_DRIFT_TOLERANCE_MS,
    playbackSpeed: Float = 1f,
): Long {
    if (!playing) return sampledMs
    if (frameDeltaMs > seekThresholdMs) return sampledMs
    val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    val predicted = displayMs + (frameDeltaMs.coerceAtLeast(0L) * speed.toDouble()).roundToLong()
    val delta = sampledMs - predicted
    return when {
        abs(delta) > seekThresholdMs -> sampledMs
        delta < -backwardToleranceMs -> sampledMs
        delta < 0L && -delta <= backwardToleranceMs -> predicted
        delta > 80L -> predicted + (delta / 4L)
        else -> predicted
    }
}

private fun HalcyonLyricLine.isDuetLine(): Boolean = agent.equals("v1", true) || agent.equals("v2", true)

private fun HalcyonLyricLine.isActiveAt(positionMs: Long): Boolean {
    val timedEnd = endMs ?: words.maxOfOrNull { it.endMs } ?: backgroundEndMs ?: timeMs + 4_000L
    return positionMs in timeMs until timedEnd.coerceAtLeast(timeMs + 1L)
}

private const val MANUAL_SCROLL_BLUR_RESUME_DELAY_MS = 3_000L
private const val MANUAL_SCROLL_RECENTER_DELAY_MS = 2_000L
private const val CONE_SCROLL_DAMPING_RATIO = 1.25f
private const val CONE_SCROLL_STIFFNESS = 200f
private const val CONE_SCROLL_VISIBILITY_THRESHOLD_PX = 0.75f
private const val CONE_SCROLL_CORRECTION_PASSES = 2
