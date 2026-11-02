package com.android.purebilibili.feature.audio.lyrics.halcyon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

internal fun isInlineRubyPronunciation(text: String): Boolean {
    val compact = text.filterNot { it.isWhitespace() }
    if (compact.isEmpty()) return false
    if (compact.any { it.isAppleMusicLatinLetter() }) return false
    return compact.any { it.isAppleMusicKana() || it.isKanjiOrHangul() }
}

private fun Char.isAppleMusicKana(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
}

internal fun appleMusicKaraokeLiftPx(
    wordLiftEnabled: Boolean,
    textSizePx: Float,
    progress: Float,
    wordLiftScale: Float = 1f
): Float = if (wordLiftEnabled) {
    maxOf(textSizePx * 0.06f, 5f) * progress * wordLiftScale.coerceIn(0f, 1f)
} else {
    0f
}

@Composable
internal fun TimedLyricText(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    active: Boolean,
    style: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    wordLiftScale: Float = 1f,
    sustainThresholdMs: Int = HALCYON_DEFAULT_SUSTAIN_THRESHOLD_MS,
    sustainGlowScale: Float = 1f,
    singleLine: Boolean = false,
    statusBarMarquee: Boolean = false,
    followWordFocus: Boolean = false,
    pronunciation: String = "",
    pronunciationWords: List<LyricWord> = emptyList(),
    rubyBelow: Boolean = false,
    splitRubyByCharacter: Boolean = false,
    rubyStyle: TextStyle? = null,
    outlineColor: Color? = null,
    outlineWidth: Float = 0f,
    glowColor: Color? = null,
    glowRadius: Float = 0f,
    onWordClick: ((Long) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 动画字形只用于会位移的歌词；减少动态效果时采用静态文字渲染。
    val lyricStyle = style.copy(
        textMotion = if (LocalSystemReduceMotion.current) TextMotion.Static else TextMotion.Animated
    )
    // TTML may encode the blank before a word as part of that word. Move it to the prior
    // karaoke unit before wrapping so every v1 line, including wrapped continuations, starts
    // at the same left edge. Right-aligned v2 rows are visually tolerant of this, but v1 is not.
    // wordLiftEnabled controls per-word vertical lift only; timed karaoke fill still renders.
    val sourceWords = remember(text, words, splitRubyByCharacter) {
        words.moveLeadingSpacesToPreviousWord().let { source ->
            if (splitRubyByCharacter) source.splitForAppleMusicRuby() else source
        }
    }
    val timedWords = remember(text, sourceWords, sustainThresholdMs) {
        sourceWords.toAppleMusicRenderWords(text, sustainThresholdMs)
    }
    val rubies = remember(timedWords, pronunciation, pronunciationWords) {
        rubiesForTimedWords(
            words = timedWords.map { it.word },
            pronunciationWords = pronunciationWords,
            pronunciation = pronunciation
        )
    }
    if (timedWords.isEmpty()) {
        val textModifier = Modifier
            .fillMaxWidth()
            .then(if (singleLine && statusBarMarquee) Modifier.basicMarquee() else Modifier)
        val textGlow = if (glowColor != null && glowRadius > 0f) {
            Shadow(
                color = glowColor.copy(alpha = glowColor.alpha * lyricStyle.color.alpha),
                offset = Offset.Zero,
                blurRadius = glowRadius
            )
        } else null
        if (outlineColor != null && outlineWidth > 0f) {
            Box(modifier = modifier) {
                BasicText(
                    text = text,
                    style = lyricStyle.copy(color = outlineColor, drawStyle = Stroke(width = outlineWidth), shadow = null),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    softWrap = !singleLine,
                    overflow = TextOverflow.Clip,
                    modifier = textModifier
                )
                BasicText(
                    text = text,
                    style = lyricStyle.copy(shadow = textGlow),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    softWrap = !singleLine,
                    overflow = TextOverflow.Clip,
                    modifier = textModifier
                )
            }
        } else {
            BasicText(
                text = text,
                style = lyricStyle.copy(shadow = textGlow),
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                softWrap = !singleLine,
                overflow = TextOverflow.Clip,
                modifier = modifier.then(if (singleLine && statusBarMarquee) Modifier.basicMarquee() else Modifier)
            )
        }
        return
    }
    // Keep the timed units as individual layout children. This is the same important distinction
    // as the smooth renderer: a long timed line breaks between singable units, not at arbitrary
    // glyphs, so highlighted and dim lines retain identical visual rows.
    val horizontalArrangement = when (lyricStyle.textAlign) {
        TextAlign.End -> Arrangement.End
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.Start
    }
    // The clock ticks every frame. Hand the words a State they read in the draw/layer phase
    // instead of a value they take as a parameter, so a frame no longer recomposes the whole
    // line's worth of word subtrees just to move one feathered edge.
    val rainbowEnabled = HalcyonLyricSettings.lyricRainbowEnabled
    val fullTrailBreath = HalcyonLyricSettings.appleMusicLyricsFullTrailBreath
    val hdrHighlightEnabled = HalcyonLyricSettings.lyricHdrHighlightEnabled
    val positionState = rememberUpdatedState(positionMs)
    val content: @Composable () -> Unit = {
        timedWords.forEachIndexed { index, renderWord ->
            AppleMusicKaraokeWord(
                renderWord = renderWord,
                positionMs = positionState,
                active = active,
                baseStyle = lyricStyle,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled,
                wordLiftScale = wordLiftScale,
                sustainGlowScale = sustainGlowScale,
                outlineColor = outlineColor,
                outlineWidth = outlineWidth,
                glowColor = glowColor,
                glowRadius = glowRadius,
                rainbowEnabled = rainbowEnabled,
                fullTrailBreath = fullTrailBreath,
                hdrHighlightEnabled = hdrHighlightEnabled,
                ruby = rubies.getOrNull(index).orEmpty(),
                rubyStyle = rubyStyle?.copy(textMotion = lyricStyle.textMotion),
                rubyBelow = rubyBelow,
                onWordClick = onWordClick,
                onLongPress = onLongPress
            )
        }
    }
    if (singleLine) {
        if (followWordFocus) {
            AppleMusicFocusedTimedRow(
                timedWords = timedWords,
                positionMs = positionMs,
                active = active,
                horizontalArrangement = horizontalArrangement,
                rubyBelow = rubyBelow,
                modifier = modifier,
                content = content
            )
        } else {
            Row(
                modifier = modifier.then(if (statusBarMarquee) Modifier.basicMarquee() else Modifier),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = if (rubyBelow) Alignment.Top else Alignment.Bottom
            ) {
                content()
            }
        }
    } else {
        // FlowRow measures each visual row independently. With centered/right-aligned lyrics it
        // therefore lets wrapped rows acquire a different origin than the first row (especially
        // visible for a translation below a long English line). Lay rows out ourselves against
        // the full line width so every row shares the exact same alignment anchor.
        AppleMusicTimedWordRows(
            textAlign = lyricStyle.textAlign,
            rubyBelow = rubyBelow,
            breakBefore = lyricUnitBreakAllowedBefore(timedWords.map { it.word.text }),
            modifier = modifier
        ) {
            content()
        }
    }
}

/**
 * Scroll a long single-line lyric to the currently sung word. Unlike basicMarquee this has a
 * stable end position: the first word stays at the leading edge until focus reaches it, and the
 * last word stays visible after the line finishes instead of restarting from the beginning.
 */
@Composable
private fun AppleMusicFocusedTimedRow(
    timedWords: List<AppleMusicRenderWord>,
    positionMs: Long,
    active: Boolean,
    horizontalArrangement: Arrangement.Horizontal,
    rubyBelow: Boolean = false,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var contentWidthPx by remember(timedWords) { mutableIntStateOf(0) }
        val focusIndex = if (!active || timedWords.isEmpty()) {
            0
        } else {
            timedWords.indexOfLast { positionMs >= it.word.startMs }
                .coerceIn(0, timedWords.lastIndex)
        }
        val focusFraction = if (timedWords.size <= 1) {
            0f
        } else {
            focusIndex.toFloat() / (timedWords.lastIndex).coerceAtLeast(1)
        }
        val targetOffset = ((contentWidthPx - viewportWidthPx).coerceAtLeast(0f) * focusFraction)
        val animatedOffset = remember { Animatable(0f) }
        LaunchedEffect(targetOffset) {
            animatedOffset.animateTo(
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = 90)
            )
        }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .wrapContentWidth(unbounded = true)
                .onSizeChanged { contentWidthPx = it.width }
                .graphicsLayer { translationX = if (isRtl) animatedOffset.value else -animatedOffset.value },
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = if (rubyBelow) Alignment.Top else Alignment.Bottom
        ) {
            content()
        }
    }
}

@Composable
private fun AppleMusicTimedWordRows(
    textAlign: TextAlign,
    rubyBelow: Boolean = false,
    breakBefore: BooleanArray = BooleanArray(0),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val availableWidth = constraints.maxWidth
            .takeUnless { it == androidx.compose.ui.unit.Constraints.Infinity }
            ?: measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) }
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = availableWidth)
        val placeables = measurables.map { it.measure(childConstraints) }
        val breaks = if (breakBefore.size == placeables.size) {
            breakBefore
        } else {
            BooleanArray(placeables.size) { true }
        }
        val rowRanges = wrapLyricUnitRows(
            widths = placeables.map { it.width }.toIntArray(),
            breakBefore = breaks,
            availableWidth = availableWidth
        )
        val rows = rowRanges.map { range -> placeables.slice(range) }
        val rowWidths = rows.map { row -> row.sumOf { it.width } }
        val rowHeights = rows.map { row -> row.maxOf { it.height } }

        val layoutWidth = availableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = rowHeights.sum().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutWidth, layoutHeight) {
            var y = 0
            rows.indices.forEach { rowIndex ->
                val rowWidth = rowWidths[rowIndex]
                var x = when (textAlign) {
                    TextAlign.End -> (layoutWidth - rowWidth).coerceAtLeast(0)
                    TextAlign.Center -> ((layoutWidth - rowWidth) / 2).coerceAtLeast(0)
                    else -> 0
                }
                rows[rowIndex].forEach { placeable ->
                    // Bottom-align when ruby is above so furigana grows upward without dropping the kanji.
                    // Top-align when ruby is below so furigana grows downward without raising the kanji.
                    val placeableY = if (rubyBelow) y else y + rowHeights[rowIndex] - placeable.height
                    placeable.placeRelative(x, placeableY)
                    x += placeable.width
                }
                y += rowHeights[rowIndex]
            }
        }
    }
}

@Composable
private fun AppleMusicKaraokeWord(
    renderWord: AppleMusicRenderWord,
    positionMs: State<Long>,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    wordLiftScale: Float = 1f,
    sustainGlowScale: Float = 1f,
    outlineColor: Color? = null,
    outlineWidth: Float = 0f,
    glowColor: Color? = null,
    glowRadius: Float = 0f,
    rainbowEnabled: Boolean = false,
    fullTrailBreath: Boolean = false,
    hdrHighlightEnabled: Boolean = false,
    ruby: String = "",
    rubyStyle: TextStyle? = null,
    rubyBelow: Boolean = false,
    onWordClick: ((Long) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val word = renderWord.word
    val rubyContent: @Composable () -> Unit = {
        if (ruby.isNotBlank() && rubyStyle != null) {
            val tracking = when {
                ruby.length >= 5 -> (-0.55).sp
                ruby.length >= 4 -> (-0.30).sp
                else -> (-0.10).sp
            }
            val scaledRubyStyle = if (ruby.length >= 5) {
                rubyStyle.copy(
                    fontSize = rubyStyle.fontSize * 0.88f,
                    letterSpacing = tracking
                )
            } else {
                rubyStyle.copy(letterSpacing = tracking)
            }
            BasicText(
                text = ruby,
                style = scaledRubyStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(
                if (onWordClick != null) {
                    Modifier.pointerInput(word.startMs, onWordClick, onLongPress) {
                        detectTapGestures(
                            onTap = { onWordClick(word.startMs) },
                            onLongPress = onLongPress?.let { press -> { press() } }
                        )
                    }
                } else Modifier
            )
            // The reference renderer moves each word independently by 6% of the text size (at
            // least 5 px), then adds only a 3% bottom-anchored scale during the held-note phase.
            // Keeping the transform on the word rather than the whole line is what creates the
            // floating vocal feel. Reading the clock inside the layer block keeps the lift on the
            // layer phase, so a 20-word line no longer recomposes 20 subtrees per frame.
            .graphicsLayer {
                translationY = -appleMusicKaraokeLiftPx(
                    wordLiftEnabled = wordLiftEnabled,
                    textSizePx = baseStyle.fontSize.toPx(),
                    progress = renderWord.karaokeProgress(positionMs.value, active),
                    wordLiftScale = wordLiftScale
                )
                transformOrigin = TransformOrigin(0.5f, if (rubyBelow) 0f else 1f)
            }
    ) {
        if (!rubyBelow) rubyContent()
        AppleMusicKaraokeGlyphs(
            renderWord = renderWord,
            positionMs = positionMs,
            active = active,
            baseStyle = baseStyle,
            contentColor = contentColor,
            sustainGlowScale = sustainGlowScale,
            outlineColor = outlineColor,
            outlineWidth = outlineWidth,
            glowColor = glowColor,
            glowRadius = glowRadius,
            rainbowEnabled = rainbowEnabled,
            fullTrailBreath = fullTrailBreath,
            hdrHighlightEnabled = hdrHighlightEnabled
        )
        if (rubyBelow) rubyContent()
    }
}

/** Holds the last measured paragraph so the draw phase can reuse it without re-laying out text. */
private class KaraokeGlyphLayout {
    var value: TextLayoutResult? = null
}

/**
 * One karaoke unit, measured once and repainted from the playback clock.
 *
 * The previous renderer stacked up to four [BasicText] children per word and rebuilt their
 * [TextStyle]s from `positionMs` on every frame, so an active line re-entered composition — and
 * the sweeping word re-entered text layout — 120 times a second. Every visual pass is kept here
 * (dim base, feathered sweep, sustain halo and its travelling sheen, held-note glow); they are
 * simply issued as `drawText` calls against one cached [TextLayoutResult], which is the one thing
 * that must not be recomputed per frame.
 */
@Composable
private fun AppleMusicKaraokeGlyphs(
    renderWord: AppleMusicRenderWord,
    positionMs: State<Long>,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    sustainGlowScale: Float,
    outlineColor: Color? = null,
    outlineWidth: Float = 0f,
    glowColor: Color? = null,
    glowRadius: Float = 0f,
    rainbowEnabled: Boolean = false,
    fullTrailBreath: Boolean = false,
    hdrHighlightEnabled: Boolean = false
) {
    val word = renderWord.word
    val measurer = rememberTextMeasurer()
    val cache = remember { KaraokeGlyphLayout() }
    val baseAlpha = baseStyle.color.alpha
    val bright = contentColor.copy(alpha = baseAlpha)
    // QZ-style experimental trail: unsung dim ≈ 0.35; Halcyon default stays 0.36.
    val dimFactor = if (fullTrailBreath) QzKaraokeDimAlphaFactor else DefaultKaraokeDimAlphaFactor
    val dim = contentColor.copy(alpha = baseAlpha * dimFactor)
    val sustainDurationMs = renderWord.sustainDurationMs
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl || word.text.isRtlText()
    val useRainbow = rainbowEnabled && active
    val trailSustainGlowScale = if (fullTrailBreath) {
        (sustainGlowScale * 1.25f).coerceAtMost(1.5f)
    } else {
        sustainGlowScale
    }
    // lyric_hdr_highlight_enabled: ~1.5× sustain glow / bright highlight (settings summary).
    val effectiveSustainGlowScale = if (hdrHighlightEnabled) {
        (trailSustainGlowScale * LyricHdrBrightnessRatio).coerceAtMost(2.2f)
    } else {
        trailSustainGlowScale
    }
    val glowCap = if (hdrHighlightEnabled) 2.2f else 1.5f

    Layout(
        modifier = Modifier.drawBehind {
            val layout = cache.value ?: return@drawBehind
            val clock = positionMs.value
            val progress = renderWord.karaokeProgress(clock, active)
            if (outlineColor != null && outlineWidth > 0f) {
                drawText(
                    textLayoutResult = layout,
                    color = outlineColor,
                    drawStyle = Stroke(width = outlineWidth)
                )
            }
            if (glowColor != null && glowRadius > 0f) {
                drawText(
                    textLayoutResult = layout,
                    color = Color.Transparent,
                    shadow = Shadow(
                        color = glowColor.copy(alpha = glowColor.alpha * baseAlpha),
                        offset = Offset.Zero,
                        blurRadius = glowRadius
                    )
                )
            }
            val glow = renderWord.sustainGlowAlpha(clock, active) * effectiveSustainGlowScale.coerceIn(0f, glowCap)
            val fontSizePx = baseStyle.fontSize.toPx().coerceAtLeast(1f)
            val feather = karaokeTrailFeather(
                fullTrailBreath = fullTrailBreath,
                wordWidthPx = layout.size.width.toFloat().coerceAtLeast(1f),
                fontSizePx = fontSizePx
            )
            // QZ trail has no metallic travelling sheen — keep sheen only on the default path.
            val trailSheenGlow = if (fullTrailBreath) 0f else glow

            if (glow > 0f) {
                // A long held note grows a soft halo around the glyph before the fill reaches it.
                val durationScale = ((sustainDurationMs - 600L).coerceAtLeast(0L) / 2_400f)
                    .coerceIn(0f, 1f)
                val haloColor = contentColor.withHdrHighlightBoost(hdrHighlightEnabled && active)
                drawText(
                    textLayoutResult = layout,
                    color = haloColor.copy(
                        alpha = ((0.05f + durationScale * 0.08f) * glow * baseAlpha).coerceIn(0f, 1f)
                    ),
                    shadow = Shadow(
                        color = haloColor.copy(
                            alpha = ((0.32f + durationScale * 0.14f) * glow * baseAlpha).coerceIn(0f, 1f)
                        ),
                        offset = Offset.Zero,
                        blurRadius = (8f + durationScale * 6f) * glow
                    )
                )
            }
            val glowShadow = glow.takeIf { it > 0.05f }?.let { glowAlpha ->
                val shadowBase = contentColor.withHdrHighlightBoost(hdrHighlightEnabled && active)
                Shadow(
                    color = shadowBase.copy(alpha = 0.24f * baseAlpha * glowAlpha),
                    offset = Offset.Zero,
                    blurRadius = 6f * glowAlpha
                )
            }
            val wordWidth = layout.size.width.toFloat().coerceAtLeast(1f)
            val fillColor = if (useRainbow) {
                karaokeRainbowColor(0.5f, baseAlpha).withHdrHighlightBoost(hdrHighlightEnabled)
            } else {
                bright.withHdrHighlightBoost(hdrHighlightEnabled && active)
            }
            val rainbowAlpha = if (hdrHighlightEnabled) {
                (baseAlpha * LyricHdrBrightnessRatio).coerceAtMost(1f)
            } else {
                baseAlpha
            }
            when {
                progress <= 0f -> drawText(textLayoutResult = layout, color = dim)
                progress >= 1f -> {
                    if (useRainbow) {
                        drawText(
                            textLayoutResult = layout,
                            brush = karaokeRainbowBrush(
                                alpha = rainbowAlpha,
                                wordWidth = wordWidth,
                                isRtl = isRtl
                            ),
                            shadow = glowShadow
                        )
                    } else {
                        drawText(
                            textLayoutResult = layout,
                            color = fillColor,
                            shadow = glowShadow
                        )
                    }
                }
                else -> {
                    drawText(textLayoutResult = layout, color = dim)
                    // A real alpha gradient rather than a hard clip: the sweep keeps its soft
                    // feathered edge while the dim glyph stays legible underneath it.
                    drawText(
                        textLayoutResult = layout,
                        brush = if (useRainbow) {
                            Brush.horizontalGradient(
                                colorStops = karaokeRainbowFillStops(
                                    progress = progress,
                                    baseAlpha = rainbowAlpha,
                                    isRtl = isRtl,
                                    feather = feather
                                ),
                                startX = 0f,
                                endX = wordWidth
                            )
                        } else {
                            Brush.horizontalGradient(
                                colorStops = karaokeFillStops(
                                    progress,
                                    fillColor,
                                    isRtl,
                                    feather,
                                    qzCentered = false
                                ),
                                startX = 0f,
                                endX = wordWidth
                            )
                        },
                        shadow = glowShadow
                    )
                    // A narrow material sheen follows the karaoke edge. Reserve it for an actual
                    // held note: on every short syllable a second moving highlight reads as two
                    // independent progress bars. Trail ON keeps sheen off (QZ path).
                    if (trailSheenGlow > 0.05f) {
                        drawText(
                            textLayoutResult = layout,
                            brush = Brush.horizontalGradient(
                                colorStops = karaokeSheenStops(
                                    progress = progress,
                                    contentColor = if (useRainbow) {
                                        karaokeRainbowColor(progress, baseAlpha)
                                            .withHdrHighlightBoost(hdrHighlightEnabled)
                                    } else {
                                        contentColor.withHdrHighlightBoost(hdrHighlightEnabled && active)
                                    },
                                    glow = trailSheenGlow,
                                    baseAlpha = baseAlpha,
                                    isRtl = isRtl,
                                    trailWidth = 0.20f
                                ),
                                startX = 0f,
                                endX = wordWidth
                            )
                        )
                    }
                }
            }
        }
    ) { _, constraints ->
        val cached = cache.value
        val layout = if (
            cached != null &&
            cached.layoutInput.text.text == word.text &&
            cached.layoutInput.style == baseStyle &&
            cached.layoutInput.constraints == constraints &&
            cached.layoutInput.layoutDirection == layoutDirection &&
            cached.layoutInput.density.density == density &&
            cached.layoutInput.density.fontScale == fontScale
        ) {
            cached
        } else {
            measurer.measure(
                text = word.text,
                style = baseStyle,
                overflow = TextOverflow.Clip,
                constraints = constraints,
                layoutDirection = layoutDirection,
                density = this
            ).also { cache.value = it }
        }
        layout(layout.size.width, layout.size.height) {}
    }
}

private fun AppleMusicRenderWord.karaokeProgress(positionMs: Long, active: Boolean): Float =
    if (active) {
        ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L))
            .coerceIn(0f, 1f)
    } else {
        0f
    }

/**
 * Soft trailing feather ≈ 2–3 English letters as a fraction of em.
 * Latin advance ≈ 0.5em, so 2.5 letters ≈ 1.25em at the active lyric font size.
 */
internal const val QzKaraokeSoftEdgeChars = 2.5f
internal const val QzKaraokeLatinAdvanceEm = 0.5f
/** Soft-edge width in em (= SoftEdgeChars × LatinAdvanceEm). */
internal const val QzKaraokeSoftEdgeEm = QzKaraokeSoftEdgeChars * QzKaraokeLatinAdvanceEm
/** Unsung dim when experimental QZ-style trail is on (raised from 0.20 toward video match). */
internal const val QzKaraokeDimAlphaFactor = 0.35f
internal const val DefaultKaraokeDimAlphaFactor = 0.36f
internal const val DefaultKaraokeFeather = 0.15f

/**
 * Trail feather width as a fraction of the painted span.
 * When [fullTrailBreath] is on: character-relative soft edge (~2.5 Latin letters / ~1.25em)
 * trailing behind progress (not centered).
 */
internal fun karaokeTrailFeather(
    fullTrailBreath: Boolean,
    wordWidthPx: Float,
    fontSizePx: Float = 0f
): Float {
    if (!fullTrailBreath) return DefaultKaraokeFeather
    val softPx = if (fontSizePx > 0f) {
        fontSizePx * QzKaraokeSoftEdgeEm
    } else {
        // Unit-test fallback when font size is omitted: ~2.5 letters at a mid AM lyric size.
        40f * QzKaraokeSoftEdgeEm
    }
    return (softPx / wordWidthPx.coerceAtLeast(1f)).coerceIn(0.12f, 0.72f)
}


/**
 * Classic 七彩 / ROYGBIV spectrum anchors (赤橙黄绿青蓝紫).
 * Used as brush colorStops; [karaokeRainbowColor] samples a continuous HSV sweep.
 */
internal val LyricSpectrumRainbowColors: List<Color> = listOf(
    Color(0xFFFF3B30), // 赤 red
    Color(0xFFFF9500), // 橙 orange
    Color(0xFFFFCC00), // 黄 yellow
    Color(0xFF34C759), // 绿 green
    Color(0xFF5AC8FA), // 青 cyan
    Color(0xFF007AFF), // 蓝 blue
    Color(0xFFAF52DE)  // 紫 violet
)

/** Lyricon-style HDR highlight brightness ratio (BasicStyle hdrBrightnessRatio default). */
internal const val LyricHdrBrightnessRatio: Float = 1.5f

/**
 * Sample a continuous 七彩 spectrum at position t in 0..1.
 * HSV hue sweeps 0°→300° (red→violet) so karaoke fill looks like a real rainbow.
 */
internal fun karaokeRainbowColor(position: Float, alpha: Float): Color {
    val t = position.coerceIn(0f, 1f)
    return Color.hsv(
        hue = t * 300f,
        saturation = 0.88f,
        value = 1f,
        alpha = alpha.coerceIn(0f, 1f)
    )
}

/**
 * Static soft multi-stop horizontal 七彩 rainbow across [wordWidth].
 * Seven HSV-sampled stops (ROYGBIV) for a continuous spectrum brush.
 */
internal fun karaokeRainbowBrush(
    alpha: Float,
    wordWidth: Float,
    isRtl: Boolean
): Brush {
    val stopCount = LyricSpectrumRainbowColors.size.coerceAtLeast(2)
    val stops = Array(stopCount) { i ->
        val p = i / (stopCount - 1).toFloat()
        val sample = if (isRtl) 1f - p else p
        p to karaokeRainbowColor(sample, alpha)
    }
    return Brush.horizontalGradient(colorStops = stops, startX = 0f, endX = wordWidth.coerceAtLeast(1f))
}

/**
 * SDR stand-in for Lyricon HDR highlight boost (~1.5× luminance).
 * Compose text does not practically wire DesiredHdrHeadroom; we brighten toward white.
 */
internal fun Color.withHdrHighlightBoost(enabled: Boolean, ratio: Float = LyricHdrBrightnessRatio): Color {
    if (!enabled || ratio <= 1f) return this
    val t = (1f - 1f / ratio).coerceIn(0f, 1f)
    return Color(
        red = (red + (1f - red) * t).coerceIn(0f, 1f),
        green = (green + (1f - green) * t).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * t).coerceIn(0f, 1f),
        alpha = alpha
    )
}

internal fun karaokeRainbowFillStops(
    progress: Float,
    baseAlpha: Float,
    isRtl: Boolean,
    feather: Float = DefaultKaraokeFeather
): Array<Pair<Float, Color>> {
    val soft = feather.coerceIn(0.05f, 0.85f)
    val leading = karaokeRainbowColor(0.15f, baseAlpha)
    val mid = karaokeRainbowColor(0.5f, baseAlpha)
    val edge = karaokeRainbowColor(0.85f, baseAlpha)
    return if (isRtl) {
        arrayOf(
            0f to Color.Transparent,
            (1f - progress).coerceAtLeast(0f) to Color.Transparent,
            (1f - (progress - soft * 0.45f)).coerceIn(0f, 1f) to edge,
            (1f - (progress - soft)).coerceIn(0f, 1f) to mid,
            1f to leading
        )
    } else {
        arrayOf(
            0f to leading,
            (progress - soft).coerceAtLeast(0f) to mid,
            (progress - soft * 0.45f).coerceAtLeast(0f) to edge,
            progress to Color.Transparent,
            1f to Color.Transparent
        )
    }
}

/**
 * Karaoke fill color stops.
 * Default / experimental trail: all-behind soft edge of [feather] (soft zone trails progress).
 * [qzCentered]: legacy soft zone centered on progress (half ahead / half behind) with
 * expanded domain so the edge fully clears at 0 and 1 — unused by the trail toggle.
 */
internal fun karaokeFillStops(
    progress: Float,
    bright: Color,
    isRtl: Boolean,
    feather: Float = DefaultKaraokeFeather,
    qzCentered: Boolean = false
): Array<Pair<Float, Color>> {
    val soft = feather.coerceIn(0.05f, 0.85f)
    if (!qzCentered) {
        return if (isRtl) {
            arrayOf(
                0f to Color.Transparent,
                (1f - progress).coerceAtLeast(0f) to Color.Transparent,
                (1f - (progress - soft)).coerceIn(0f, 1f) to bright,
                1f to bright
            )
        } else {
            arrayOf(
                0f to bright,
                (progress - soft).coerceAtLeast(0f) to bright,
                progress to Color.Transparent,
                1f to Color.Transparent
            )
        }
    }
    // QZ: softNorm = min(100/w,1); center = ((1+soft)*p) - soft/2; solid..dim = center ± soft/2
    val half = soft * 0.5f
    val center = ((1f + soft) * progress.coerceIn(0f, 1f)) - half
    val solidEnd = center - half
    val dimStart = center + half
    return if (isRtl) {
        val a = (1f - dimStart).coerceIn(0f, 1f)
        val b = (1f - solidEnd).coerceIn(a, 1f)
        arrayOf(
            0f to Color.Transparent,
            a to Color.Transparent,
            b to bright,
            1f to bright
        )
    } else {
        val a = solidEnd.coerceIn(0f, 1f)
        val b = dimStart.coerceIn(a, 1f)
        arrayOf(
            0f to bright,
            a to bright,
            b to Color.Transparent,
            1f to Color.Transparent
        )
    }
}

internal fun karaokeSheenStops(
    progress: Float,
    contentColor: Color,
    glow: Float,
    baseAlpha: Float,
    isRtl: Boolean,
    trailWidth: Float = 0.20f
): Array<Pair<Float, Color>> {
    val sheenAlpha = (0.10f + glow * 0.20f) * baseAlpha
    val trail = trailWidth.coerceIn(0.12f, 0.55f)
    return if (isRtl) {
        arrayOf(
            0f to Color.Transparent,
            (1f - (progress + 0.045f)).coerceAtLeast(0f) to Color.Transparent,
            (1f - (progress - 0.055f)).coerceIn(0f, 1f) to contentColor.copy(alpha = sheenAlpha),
            (1f - (progress - trail)).coerceAtMost(1f) to Color.Transparent,
            1f to Color.Transparent
        )
    } else {
        val sheenStart = (progress - trail).coerceAtLeast(0f)
        arrayOf(
            0f to Color.Transparent,
            sheenStart to Color.Transparent,
            (progress - 0.055f).coerceIn(sheenStart, progress) to contentColor.copy(alpha = sheenAlpha),
            (progress + 0.045f).coerceAtMost(1f) to Color.Transparent,
            1f to Color.Transparent
        )
    }
}

internal fun rubiesForTimedWords(
    words: List<LyricWord>,
    pronunciationWords: List<LyricWord>,
    pronunciation: String
): List<String> {
    if (words.isEmpty()) return emptyList()
    val blanks = List(words.size) { "" }
    val rubyWords = pronunciationWords
        .map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotBlank() && it.endMs > it.startMs }
    val rubyText = pronunciation.trim()
    if (rubyWords.isEmpty() && rubyText.isBlank()) return blanks

    if (rubyWords.isNotEmpty()) {
        val spansAllWords = rubyWords.size == 1 &&
            words.size > 1 &&
            words.all { word -> timedRangesOverlap(word, rubyWords.first()) }
        if (spansAllWords) {
            return attachRubyByCorrespondence(words, rubyWords.first().text)
        }
        val assigned = assignRubySpansToWords(words, rubyWords)
        if (assigned.any { it.isNotBlank() }) return assigned
        return attachRubyByCorrespondence(words, rubyWords.joinToString("") { it.text })
    }
    return attachRubyByCorrespondence(words, rubyText)
}

internal fun assignRubySpansToWords(
    words: List<LyricWord>,
    rubyWords: List<LyricWord>
): List<String> {
    if (rubyWords.size == words.size) return rubyWords.map { it.text }
    val result = MutableList(words.size) { "" }
    val usedWords = BooleanArray(words.size)
    rubyWords.forEach { ruby ->
        val match = words.indices
            .filter { index -> !usedWords[index] }
            .maxWithOrNull(
                compareBy<Int> { overlapMs(words[it], ruby) }
                    .thenBy { -kotlin.math.abs(words[it].startMs - ruby.startMs) }
            )
            ?.takeIf { index ->
                overlapMs(words[index], ruby) > 0L ||
                    kotlin.math.abs(words[index].startMs - ruby.startMs) <= 25L
            }
        if (match != null) {
            usedWords[match] = true
            result[match] = ruby.text
        }
    }
    return result
}

private fun attachRubyBySyllables(words: List<LyricWord>, reading: String): List<String> {
    val tokens = reading.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return List(words.size) { "" }

    val charToWord = mutableListOf<Int>()
    val surface = buildString {
        words.forEachIndexed { index, word ->
            word.text.forEach { character ->
                append(character)
                charToWord += index
            }
        }
    }
    val rubyByWord = MutableList(words.size) { mutableListOf<String>() }
    var sIdx = 0
    var tIdx = 0

    while (sIdx < surface.length) {
        val character = surface[sIdx]
        if (character.isWhitespace()) {
            sIdx++
            continue
        }

        if (character.isKanjiOrHangul()) {
            var runEnd = sIdx + 1
            while (runEnd < surface.length && surface[runEnd].isKanjiOrHangul()) {
                runEnd++
            }
            val runLength = runEnd - sIdx

            // Look ahead for the next Latin anchor in surface
            var nextLatinMatch: Int? = null
            var restIdx = runEnd
            while (restIdx < surface.length) {
                val restChar = surface[restIdx]
                if (restChar.isAppleMusicLatinLetter()) {
                    var wordEnd = restIdx + 1
                    while (wordEnd < surface.length && (surface[wordEnd].isAppleMusicLatinLetter() || surface[wordEnd] in "'’-")) {
                        wordEnd++
                    }
                    val latinWord = surface.substring(restIdx, wordEnd).filter { it.isAppleMusicLatinLetter() }.lowercase()
                    if (latinWord.isNotEmpty()) {
                        for (tokI in tIdx until tokens.size) {
                            val normTok = tokens[tokI].filter { it.isAppleMusicLatinLetter() }.lowercase()
                            if (normTok == latinWord) {
                                nextLatinMatch = tokI
                                break
                            }
                        }
                    }
                    break
                }
                restIdx++
            }

            val consumed = if (nextLatinMatch != null) {
                maxOf(1, nextLatinMatch - tIdx)
            } else {
                val remTokens = tokens.size - tIdx
                val remKanji = (runEnd until surface.length).count { surface[it].isKanjiOrHangul() }
                if (remKanji > 0) {
                    maxOf(1, minOf(runLength, remTokens - remKanji))
                } else {
                    remTokens
                }
            }

            val tokEnd = minOf(tokens.size, tIdx + consumed)
            val assignedTokens = tokens.subList(tIdx, tokEnd)
            tIdx = tokEnd

            if (assignedTokens.size == runLength) {
                assignedTokens.forEachIndexed { i, tok ->
                    rubyByWord[charToWord[sIdx + i]].add(tok)
                }
            } else if (assignedTokens.size == 1) {
                rubyByWord[charToWord[sIdx]].add(assignedTokens[0])
            } else {
                for (i in 0 until minOf(runLength, assignedTokens.size)) {
                    val tok = if (i == runLength - 1 && assignedTokens.size > runLength) {
                        assignedTokens.subList(i, assignedTokens.size).joinToString(" ")
                    } else {
                        assignedTokens[i]
                    }
                    rubyByWord[charToWord[sIdx + i]].add(tok)
                }
            }
            sIdx = runEnd
        } else if (character.isAppleMusicLatinLetter()) {
            var wordEnd = sIdx + 1
            while (wordEnd < surface.length && (surface[wordEnd].isAppleMusicLatinLetter() || surface[wordEnd] in "'’-")) {
                wordEnd++
            }
            val latinWord = surface.substring(sIdx, wordEnd).filter { it.isAppleMusicLatinLetter() }.lowercase()
            if (tIdx < tokens.size) {
                val normTok = tokens[tIdx].filter { it.isAppleMusicLatinLetter() }.lowercase()
                if (normTok == latinWord) {
                    tIdx++
                }
            }
            sIdx = wordEnd
        } else {
            if (tIdx < tokens.size && tokens[tIdx] == character.toString()) {
                tIdx++
            }
            sIdx++
        }
    }

    return rubyByWord.map { it.joinToString(" ") }
}

internal fun attachRubyByCorrespondence(words: List<LyricWord>, reading: String): List<String> {
    if (words.isEmpty()) return emptyList()
    val cleanReading = reading.trim()
    if (cleanReading.isBlank()) return List(words.size) { "" }

    if (cleanReading.contains(' ') || cleanReading.any { it.isAppleMusicLatinLetter() }) {
        val syllableAligned = attachRubyBySyllables(words, cleanReading)
        if (syllableAligned.any { it.isNotBlank() }) {
            return syllableAligned
        }
    }

    val readingChars = cleanReading.filterNot { it.isWhitespace() }.toList()
    if (readingChars.isEmpty()) return List(words.size) { "" }

    val charToWord = mutableListOf<Int>()
    val surface = buildString {
        words.forEachIndexed { index, word ->
            word.text.forEach { character ->
                append(character)
                charToWord += index
            }
        }
    }
    val rubyByWord = MutableList(words.size) { StringBuilder() }
    var surfaceIndex = 0
    var readingIndex = 0
    while (surfaceIndex < surface.length) {
        val character = surface[surfaceIndex]
        when {
            character.isWhitespace() -> surfaceIndex++
            character.isAppleMusicCjkIdeograph() -> {
                var runEnd = surfaceIndex + 1
                while (runEnd < surface.length && surface[runEnd].isAppleMusicCjkIdeograph()) {
                    runEnd++
                }
                val consumed = readingConsumedForKanjiRun(
                    rest = surface.substring(runEnd),
                    reading = readingChars,
                    readingIndex = readingIndex
                )
                val rubyEnd = (readingIndex + consumed).coerceAtMost(readingChars.size)
                val runLength = runEnd - surfaceIndex
                val pieces = splitReadingAcrossKanji(
                    reading = readingChars.subList(readingIndex, rubyEnd),
                    kanjiCount = runLength
                )
                pieces.forEachIndexed { offset, piece ->
                    if (piece.isNotEmpty()) {
                        rubyByWord[charToWord[surfaceIndex + offset]].append(piece)
                    }
                }
                readingIndex = rubyEnd
                surfaceIndex = runEnd
            }
            character.isAppleMusicKana() || character.isAppleMusicLatinLetter() || character.isDigit() -> {
                if (readingIndex < readingChars.size && kanaEquals(character, readingChars[readingIndex])) {
                    readingIndex++
                }
                surfaceIndex++
            }
            else -> {
                if (readingIndex < readingChars.size && character == readingChars[readingIndex]) {
                    readingIndex++
                }
                surfaceIndex++
            }
        }
    }
    if (readingIndex < readingChars.size) {
        val leftover = readingChars.subList(readingIndex, readingChars.size).joinToString("")
        val target = rubyByWord.indices.lastOrNull { rubyByWord[it].isNotEmpty() }
            ?: words.indexOfLast { word -> word.text.any { it.isAppleMusicCjkIdeograph() } }
                .takeIf { it >= 0 }
            ?: 0
        rubyByWord[target].append(leftover)
    }
    return rubyByWord.map { it.toString() }
}

private fun readingConsumedForKanjiRun(
    rest: String,
    reading: List<Char>,
    readingIndex: Int
): Int {
    val remaining = reading.size - readingIndex
    if (remaining <= 0) return 0
    val laterKanjiRuns = countIdeographRuns(rest)
    val nextAnchor = rest.firstOrNull { character ->
        character.isAppleMusicKana() || character.isAppleMusicLatinLetter() || character.isDigit()
    }
    if (nextAnchor == null) {
        return if (laterKanjiRuns > 0) {
            (remaining - laterKanjiRuns).coerceAtLeast(1).coerceAtMost(remaining)
        } else {
            remaining
        }
    }
    val anchorAt = reading.subList(readingIndex, reading.size).indexOfFirst { kanaEquals(it, nextAnchor) }
    if (anchorAt >= 0) return anchorAt
    if (laterKanjiRuns <= 0) return remaining
    val keep = laterKanjiRuns.coerceAtMost(remaining - 1)
    return (remaining - keep).coerceAtLeast(1)
}

private fun countIdeographRuns(text: String): Int {
    var count = 0
    var inRun = false
    text.forEach { character ->
        val ideograph = character.isAppleMusicCjkIdeograph()
        if (ideograph && !inRun) count++
        inRun = ideograph
    }
    return count
}

private fun splitReadingAcrossKanji(reading: List<Char>, kanjiCount: Int): List<String> {
    if (kanjiCount <= 0) return emptyList()
    if (kanjiCount == 1) return listOf(reading.joinToString(""))
    if (reading.isEmpty()) return List(kanjiCount) { "" }
    val pieces = MutableList(kanjiCount) { StringBuilder() }
    val guaranteed = minOf(kanjiCount, reading.size)
    repeat(guaranteed) { index -> pieces[index].append(reading[index]) }
    if (reading.size > guaranteed) {
        pieces[kanjiCount - 1].append(reading.subList(guaranteed, reading.size).joinToString(""))
    }
    return pieces.map { it.toString() }
}

private fun kanaEquals(first: Char, second: Char): Boolean {
    fun fold(character: Char): Char {
        if (character in '\u30A1'..'\u30F6') return (character.code - 0x60).toChar()
        return character
    }
    return fold(first) == fold(second)
}

private fun timedRangesOverlap(first: LyricWord, second: LyricWord): Boolean =
    minOf(first.endMs, second.endMs) > maxOf(first.startMs, second.startMs)

private fun overlapMs(first: LyricWord, second: LyricWord): Long =
    (minOf(first.endMs, second.endMs) - maxOf(first.startMs, second.startMs)).coerceAtLeast(0L)

private fun Char.isAppleMusicCjkIdeograph(): Boolean = isKanjiOrHangul()

private fun AppleMusicRenderWord.sustainGlowAlpha(positionMs: Long, active: Boolean): Float {
    val sustainEndMs = sustainEndMs ?: return 0f
    if (!active || positionMs !in word.startMs until sustainEndMs) return 0f
    val duration = sustainEndMs - word.startMs
    val elapsed = positionMs - word.startMs
    // ConePlayer starts the held-note envelope at the beginning of the marked word; it does not
    // wait for a separate attack delay. This is why its halo is already visible around the first
    // sung glyph in a long "Oh" rather than appearing halfway through the word.
    val progress = (elapsed.toFloat() / duration.coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    return if (progress < 0.7f) {
        sin((progress / 0.7f) * (PI.toFloat() / 2f))
    } else {
        cos(((progress - 0.7f) / 0.3f) * (PI.toFloat() / 2f))
    }.coerceIn(0f, 1f)
}

private data class AppleMusicRenderWord(
    val word: LyricWord,
    val sustainEndMs: Long? = null
) {
    val sustainDurationMs: Long get() = (sustainEndMs ?: word.endMs) - word.startMs
}

private fun List<LyricWord>.toAppleMusicRenderWords(
    lineText: String,
    sustainThresholdMs: Int
): List<AppleMusicRenderWord> {
    if (isEmpty() || lineText.isBlank()) return emptyList()
    val result = mutableListOf<AppleMusicRenderWord>()
    var cursor = 0
    forEachIndexed { index, word ->
        if (word.text.isBlank() || word.endMs <= word.startMs) return@forEachIndexed
        val start = lineText.indexOf(word.text, cursor)
        if (start < 0) return emptyList()
        val end = start + word.text.length
        val nextStart = getOrNull(index + 1)?.text?.let { next -> lineText.indexOf(next, end) } ?: -1
        val suffix = when {
            nextStart > end -> lineText.substring(end, nextStart)
            index == lastIndex && end < lineText.length -> lineText.substring(end)
            else -> ""
        }
        val duration = word.endMs - word.startMs
        val splitForCharacters = word.shouldSplitForAppleMusicCharacters(sustainThresholdMs)
        if (splitForCharacters) {
            val chars = word.text.toCharArray()
            val segmentDuration = duration / chars.size
            chars.forEachIndexed { charIndex, char ->
                val segmentStart = word.startMs + segmentDuration * charIndex
                val segmentEnd = if (charIndex == chars.lastIndex) {
                    word.endMs
                } else {
                    segmentStart + segmentDuration
                }
                result += AppleMusicRenderWord(
                    word = LyricWord(
                        text = char.toString() + if (charIndex == chars.lastIndex) suffix else "",
                        startMs = segmentStart,
                        endMs = segmentEnd
                    ),
                    sustainEndMs = word.endMs
                )
            }
        } else {
            // TTML providers sometimes put a short English phrase in a single timed span.
            // Split it at word boundaries so each word gets its own progressive feather.
            result += AppleMusicRenderWord(
                word = word.copy(text = word.text + suffix),
                sustainEndMs = word.endMs.takeIf { duration >= sustainThresholdMs.coerceAtLeast(0) }
            )
                .splitEnglishPhraseForAppleMusic()
        }
        cursor = end + suffix.length
    }
    return result
}

/**
 * A TTML/LRC provider may put a whole long CJK phrase in one timed span. If that span wraps in
 * the player, a single BasicText child gives every visual row the same progress. Split long
 * timed phrases into character-sized children so wrapped rows can complete from top to bottom.
 */
internal fun LyricWord.shouldSplitForAppleMusicCharacters(
    sustainThresholdMs: Int = HALCYON_DEFAULT_SUSTAIN_THRESHOLD_MS
): Boolean {
    if (endMs - startMs < sustainThresholdMs.coerceAtLeast(0).toLong() || text.length <= 1) return false
    // Latin words should never be split into characters across line wraps.
    // Whole words are kept intact so that "stranger" never breaks into "stra" and "nger".
    if (text.any { it.isAppleMusicLatinLetter() }) return false
    return text.any { it.isAppleMusicCjkCharacter() }
}

private fun Char.isAppleMusicLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/**
 * Apple word-timed lyrics split English into sung syllables (`heart` / `brea` / `ker`).
 * Those pieces must wrap as one word. A break is allowed only at whitespace or where a
 * Latin word starts, so CJK can still wrap per character.
 */
internal fun lyricUnitBreakAllowedBefore(texts: List<String>): BooleanArray =
    BooleanArray(texts.size) { index ->
        index == 0 || !continuesLatinWord(texts[index - 1], texts[index])
    }

private fun continuesLatinWord(previous: String, current: String): Boolean {
    if (previous.isEmpty() || current.isEmpty()) return false
    if (previous.last().isWhitespace() || current.first().isWhitespace()) return false
    val prevChar = previous.last()
    val nextChar = current.first()
    val prevJoins = prevChar.isAppleMusicLatinLetter() || prevChar == '\'' || prevChar == '’' || prevChar == '-'
    val nextJoins = nextChar.isAppleMusicLatinLetter() || nextChar == '\'' || nextChar == '’' || nextChar == '-'
    return prevJoins && nextJoins &&
        (prevChar.isAppleMusicLatinLetter() || nextChar.isAppleMusicLatinLetter())
}

/**
 * @return inclusive index ranges, one per visual row.
 * A Latin syllable run moves to the next row together when it fits there. A run wider than
 * the row still breaks, so one enormous word cannot overflow the screen.
 */
internal fun wrapLyricUnitRows(
    widths: IntArray,
    breakBefore: BooleanArray,
    availableWidth: Int
): List<IntRange> {
    if (widths.isEmpty()) return emptyList()
    val rows = mutableListOf<IntRange>()
    var rowStart = 0
    var rowWidth = 0
    var index = 0
    while (index < widths.size) {
        val width = widths[index]
        if (rowWidth == 0 || rowWidth + width <= availableWidth) {
            rowWidth += width
            index++
            continue
        }
        if (index > rowStart && index < breakBefore.size && !breakBefore[index]) {
            var runStart = index
            while (runStart > rowStart && !breakBefore[runStart]) runStart--
            var runWidth = width
            for (cursor in runStart until index) runWidth += widths[cursor]
            if (runStart > rowStart && runWidth <= availableWidth) {
                rows += rowStart until runStart
                rowStart = runStart
                rowWidth = runWidth - width
                continue
            }
        }
        if (index > rowStart) {
            rows += rowStart until index
            rowStart = index
            rowWidth = 0
            continue
        }
        rows += index..index
        index++
        rowStart = index
        rowWidth = 0
    }
    if (rowStart < widths.size) rows += rowStart until widths.size
    return rows
}

private fun Char.isAppleMusicCjkCharacter(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}

private fun AppleMusicRenderWord.splitEnglishPhraseForAppleMusic(): List<AppleMusicRenderWord> {
    val sourceText = word.text
    if (!sourceText.any { it in 'a'..'z' || it in 'A'..'Z' } || !sourceText.any(Char::isWhitespace)) {
        return listOf(this)
    }
    val segments = Regex("\\S+\\s*").findAll(sourceText).map { it.value }.toList()
    if (segments.size < 2) return listOf(this)

    val totalWeight = segments.sumOf { segment ->
        segment.count { it.isLetterOrDigit() }.coerceAtLeast(1)
    }.coerceAtLeast(1)
    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
    var elapsed = 0L
    return segments.mapIndexed { index, segment ->
        val weight = segment.count { it.isLetterOrDigit() }.coerceAtLeast(1)
        val startMs = word.startMs + elapsed
        val endMs = if (index == segments.lastIndex) {
            word.endMs
        } else {
            (word.startMs + (duration * (elapsed + weight) / totalWeight)).coerceAtLeast(startMs + 1L)
        }
        elapsed += weight
        AppleMusicRenderWord(
            word = LyricWord(text = segment, startMs = startMs, endMs = endMs),
            // A sustained source span is represented by a glow on the final sung word; this
            // avoids every word in a phrase receiving the same permanent halo.
            sustainEndMs = sustainEndMs?.takeIf { index == segments.lastIndex }
        )
    }
}

/** Keep inter-word whitespace on the previous unit so a wrapped row starts at the shared edge. */
internal fun List<LyricWord>.moveLeadingSpacesToPreviousWord(): List<LyricWord> {
    val result = mutableListOf<LyricWord>()
    forEach { word ->
        val leadingWhitespace = word.text.takeWhile(Char::isWhitespace)
        if (leadingWhitespace.isNotEmpty() && result.isNotEmpty()) {
            val previous = result.removeAt(result.lastIndex)
            result += previous.copy(text = previous.text + leadingWhitespace)
        }
        val visibleText = word.text.drop(leadingWhitespace.length)
        if (visibleText.isNotEmpty()) result += word.copy(text = visibleText)
    }
    return result
}

/**
 * TTML ruby often arrives as one timed span for a whole Japanese phrase. Split that span into
 * character-sized timed units before attaching ruby text so the reading can sit below its own
 * character instead of becoming one second line under the whole phrase.
 */
internal fun List<LyricWord>.splitForAppleMusicRuby(): List<LyricWord> {
    if (isEmpty()) return this
    return flatMap { word ->
        val pieces = mutableListOf<StringBuilder>()
        word.text.forEach { character ->
            if (character.isWhitespace() && pieces.isNotEmpty()) {
                pieces.last().append(character)
            } else {
                pieces += StringBuilder().append(character)
            }
        }
        val hasMultipleCjkCharacters = pieces.size > 1 &&
            pieces.any { piece -> piece.any { it.isAppleMusicCjkCharacter() } }
        val duration = word.endMs - word.startMs
        if (!hasMultipleCjkCharacters || duration < pieces.size.toLong()) {
            listOf(word)
        } else {
            pieces.mapIndexed { index, piece ->
                val start = word.startMs + duration * index / pieces.size
                val end = if (index == pieces.lastIndex) {
                    word.endMs
                } else {
                    word.startMs + duration * (index + 1) / pieces.size
                }
                LyricWord(
                    text = piece.toString(),
                    startMs = start,
                    endMs = end.coerceAtLeast(start + 1L)
                )
            }
        }
    }
}
