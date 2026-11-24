package com.android.purebilibili.feature.audio.lyrics.halcyon

import androidx.compose.runtime.Immutable

/** Ported Halcyon `LyricWord`. */
@Immutable
internal data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Ported Halcyon `LyricLine` (primary / background / pronunciation / duet agent).
 */
@Immutable
internal data class HalcyonLyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val pronunciation: String? = null,
    val pronunciationWords: List<LyricWord> = emptyList(),
    val agent: String? = null,
    val agentName: String? = null,
    val backgroundText: String? = null,
    val backgroundWords: List<LyricWord> = emptyList(),
    val backgroundTranslation: String? = null,
    val backgroundStartMs: Long? = null,
    val backgroundEndMs: Long? = null,
    val isTtml: Boolean = false,
    val endMs: Long? = null,
    val isOpeningMetadata: Boolean = false,
)

/** Ported Halcyon `primaryEndMs` (capped by next line unless duet overlap). */
internal fun HalcyonLyricLine.primaryEndMs(
    nextLine: HalcyonLyricLine? = null,
    nextLineStartMs: Long? = null,
    fallbackDurationMs: Long = 4_000L,
): Long {
    val resolvedNextLineStartMs = nextLineStartMs ?: nextLine?.timeMs
    if (text.isBlank() && !backgroundText.isNullOrBlank()) {
        val backgroundOnlyEnd = (
            backgroundEndMs
                ?: backgroundWords.maxOfOrNull { it.endMs }
                ?: endMs
                ?: resolvedNextLineStartMs
                ?: (timeMs + fallbackDurationMs)
            )
        val cappedBackgroundOnlyEnd = if (
            resolvedNextLineStartMs != null &&
            backgroundOnlyEnd > resolvedNextLineStartMs
        ) {
            resolvedNextLineStartMs
        } else {
            backgroundOnlyEnd
        }
        return cappedBackgroundOnlyEnd.coerceAtLeast(timeMs + 1L)
    }

    val mainWordEndMs = words.maxOfOrNull { it.endMs }
    val backgroundWordEndMs = backgroundWords.maxOfOrNull { it.endMs }
    val backgroundTimedEndMs = listOfNotNull(backgroundEndMs, backgroundWordEndMs).maxOrNull()
    val mainEnd = when {
        mainWordEndMs != null && backgroundTimedEndMs != null ->
            maxOf(mainWordEndMs, backgroundTimedEndMs)
        mainWordEndMs != null -> mainWordEndMs
        backgroundTimedEndMs != null -> backgroundTimedEndMs
        else -> endMs
    }
    val cappedEnd = when {
        resolvedNextLineStartMs == null -> mainEnd
        mainEnd == null -> resolvedNextLineStartMs
        mainEnd > resolvedNextLineStartMs &&
            !preservesPrimaryOverlapWith(nextLine, mainWordEndMs ?: backgroundTimedEndMs ?: mainEnd)
            -> resolvedNextLineStartMs
        else -> mainEnd
    }
    return (cappedEnd ?: (timeMs + fallbackDurationMs)).coerceAtLeast(timeMs + 1L)
}

private fun HalcyonLyricLine.preservesPrimaryOverlapWith(
    nextLine: HalcyonLyricLine?,
    sungEndMs: Long,
): Boolean {
    val nextLineStartMs = nextLine?.timeMs ?: return false
    if (sungEndMs <= nextLineStartMs) return false
    val currentAgent = agent?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
    val nextAgent = nextLine.agent?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
    if (currentAgent == nextAgent) return false
    if (!((currentAgent == "v1" && nextAgent == "v2") ||
            (currentAgent == "v2" && nextAgent == "v1"))
    ) {
        return false
    }
    val currentText = normalizedDuetText()
    val nextText = nextLine.normalizedDuetText()
    return currentText.isNotBlank() && currentText == nextText
}

private fun HalcyonLyricLine.normalizedDuetText(): String =
    buildString {
        append(text)
        if (!backgroundText.isNullOrBlank()) append(backgroundText)
    }.lowercase().replace(Regex("""\s+"""), "")

/** Ported Halcyon `currentLyricIndexAt` + leading-zero suppression. */
internal const val LEADING_ZERO_LYRIC_SUPPRESSION_MS = 750L

internal data class LyricIndexResult(
    val index: Int,
    val suppressedLeadingZero: Boolean,
)

internal fun currentLyricIndexAt(
    positionMs: Long,
    lyrics: List<HalcyonLyricLine>,
    suppressLeadingZero: Boolean,
): LyricIndexResult {
    if (lyrics.isEmpty()) return LyricIndexResult(index = -1, suppressedLeadingZero = false)

    var index = -1
    for (i in lyrics.indices.reversed()) {
        val line = lyrics[i]
        if (!line.hasVisibleLyricText()) continue
        val nextLine = lyrics.getOrNull(i + 1)
        val endMs = line.primaryEndMs(nextLine = nextLine)
        if (positionMs >= line.timeMs && positionMs < endMs) {
            index = i
            break
        }
    }
    if (index == -1) {
        for (i in lyrics.indices.reversed()) {
            val line = lyrics[i]
            if (!line.hasVisibleLyricText()) continue
            if (positionMs >= line.timeMs) {
                index = i
                break
            }
        }
    }
    val shouldSuppressLeadingZero = suppressLeadingZero &&
        lyrics.getOrNull(index)?.timeMs == 0L &&
        positionMs in 0L until LEADING_ZERO_LYRIC_SUPPRESSION_MS
    return LyricIndexResult(
        index = if (shouldSuppressLeadingZero) -1 else index,
        suppressedLeadingZero = shouldSuppressLeadingZero,
    )
}

internal fun HalcyonLyricLine.hasVisibleLyricText(): Boolean {
    val textFields = listOf(text, translation, pronunciation, backgroundText, backgroundTranslation)
    if (textFields.any { !it.isNullOrBlank() }) return true
    return words.any { it.text.isNotBlank() } ||
        pronunciationWords.any { it.text.isNotBlank() } ||
        backgroundWords.any { it.text.isNotBlank() }
}

/** Ported Halcyon `PlayerMiniLyrics.MiniLyricLinePresentation`. */
internal data class MiniLyricLinePresentation(
    val showPrimaryText: Boolean = true,
    val showTranslation: Boolean = true,
    val showPronunciation: Boolean = false,
    val showBackgroundText: Boolean = true,
)

/**
 * Map BiliPai lines to Halcyon words/lines (spans→words, endMs, ♪ filter).
 * Without word-level spans, CJK lines are split per character (Halcyon TTML path).
 */
internal fun mapToHalcyonLyrics(
    lines: List<com.android.purebilibili.feature.audio.lyrics.LyricLine>,
): List<HalcyonLyricLine> {
    val sorted = lines.sortedBy { it.startTimeMs }
    val mapped = sorted.mapIndexed { index, line ->
        val next = sorted.getOrNull(index + 1)
        val words = buildHalcyonWords(
            text = line.text,
            lineStartMs = line.startTimeMs,
            lineEndMs = line.endTimeMs,
            spans = line.spans,
            nextLineStartMs = next?.startTimeMs,
        )
        val translation = line.translations.firstOrNull()?.trim()?.takeIf {
            it.isNotBlank() && !isDecorativeSecondary(line.text, it)
        }
        HalcyonLyricLine(
            timeMs = line.startTimeMs,
            text = line.text,
            words = words,
            translation = translation,
            endMs = line.endTimeMs,
        )
    }
    return mapped.mapIndexed { index, line ->
        val next = mapped.getOrNull(index + 1)
        line.copy(endMs = line.primaryEndMs(next))
    }
}

private fun buildHalcyonWords(
    text: String,
    lineStartMs: Long,
    lineEndMs: Long,
    spans: List<com.android.purebilibili.feature.audio.lyrics.LyricSpan>,
    nextLineStartMs: Long?,
): List<LyricWord> {
    if (text.isEmpty()) return emptyList()
    val effectiveLineEnd = when {
        lineEndMs > lineStartMs && nextLineStartMs != null && nextLineStartMs > lineStartMs ->
            minOf(lineEndMs, nextLineStartMs)
        lineEndMs > lineStartMs -> lineEndMs
        nextLineStartMs != null && nextLineStartMs > lineStartMs -> nextLineStartMs
        else -> lineStartMs + 4_000L
    }
    val raw = if (spans.isEmpty()) {
        splitPlainLineToWords(text, lineStartMs, effectiveLineEnd, effectiveLineEnd)
    } else {
        spans.filter { it.text.isNotEmpty() }.flatMap { span ->
            val segments = span.text.split('\n')
            if (segments.size <= 1) {
                listOf(
                    LyricWord(
                        text = span.text,
                        startMs = span.startTimeMs,
                        endMs = span.endTimeMs.coerceAtLeast(span.startTimeMs + 1L),
                    ),
                )
            } else {
                val duration = (span.endTimeMs - span.startTimeMs).coerceAtLeast(segments.size.toLong())
                segments.mapIndexed { index, segment ->
                    val start = span.startTimeMs + duration * index / segments.size
                    val end = span.startTimeMs + duration * (index + 1) / segments.size
                    LyricWord(segment, start, end.coerceAtLeast(start + 1L))
                }
            }
        }
    }
    return raw.mapIndexed { index, word ->
        val nextStart = raw.getOrNull(index + 1)?.startMs?.takeIf { it > word.startMs } ?: effectiveLineEnd
        word.copy(endMs = minOf(word.endMs, nextStart).coerceAtLeast(word.startMs + 1L))
    }
}

/**
 * Halcyon CJK karaoke path: without word timestamps, split per character so the
 * fill sweep can advance glyph-by-glyph like the reference player.
 */
private fun splitPlainLineToWords(
    text: String,
    lineStartMs: Long,
    lineEndMs: Long,
    maxEnd: Long,
): List<LyricWord> {
    val lineDuration = (minOf(lineEndMs, maxEnd) - lineStartMs).coerceAtLeast(1L)
    val units = mutableListOf<String>()
    val builder = StringBuilder()
    for (ch in text) {
        if (ch == '\n') {
            if (builder.isNotEmpty()) {
                units += builder.toString()
                builder.clear()
            }
            continue
        }
        if (ch.isKanjiOrHangul()) {
            if (builder.isNotEmpty()) {
                units += builder.toString()
                builder.clear()
            }
            units += ch.toString()
        } else {
            builder.append(ch)
            // Latin/other runs end at whitespace so words stay readable.
            if (ch.isWhitespace() && builder.length > 1) {
                units += builder.toString()
                builder.clear()
            }
        }
    }
    if (builder.isNotEmpty()) units += builder.toString()
    if (units.isEmpty()) {
        return listOf(
            LyricWord(
                text = text,
                startMs = lineStartMs,
                endMs = (lineStartMs + lineDuration).coerceAtLeast(lineStartMs + 1L),
            ),
        )
    }
    if (units.size == 1) {
        return listOf(
            LyricWord(
                text = units[0],
                startMs = lineStartMs,
                endMs = (lineStartMs + lineDuration).coerceAtLeast(lineStartMs + 1L),
            ),
        )
    }
    val count = units.size.toLong()
    return units.mapIndexed { index, unit ->
        val start = lineStartMs + lineDuration * index / count
        val end = lineStartMs + lineDuration * (index + 1) / count
        LyricWord(unit, start, end.coerceAtLeast(start + 1L))
    }
}

internal fun isDecorativeSecondary(primary: String, secondary: String): Boolean {
    val s = secondary.trim()
    if (s.isEmpty()) return true
    if (s.any { it == '♪' || it == '♫' }) return true
    fun normalize(value: String): String = value.filterNot {
        it.isWhitespace() || it in "♪♫，。？！、,.?!\"'“”"
    }
    val a = normalize(primary)
    val b = normalize(s)
    return a.isNotEmpty() && a == b
}

/** Ported Halcyon page focus anchor (PlayerLyricAlignment). */
internal fun resolveLyricPageFocusOffsetRatio(upperAlignmentRatio: Float): Float =
    upperAlignmentRatio.coerceIn(0f, 1f)
