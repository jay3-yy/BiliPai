package com.android.purebilibili.feature.audio.screen

import kotlin.math.roundToInt

internal fun resolveAudioNowPlayingCoverSizeDp(mergeProgress: Float, searchProgress: Float): Float =
    32f - 4f * mergeProgress.coerceIn(0f, 1f) * (1f - searchProgress.coerceIn(0f, 1f))

internal fun resolveAudioNowPlayingBarHeightDp(mergeProgress: Float, searchProgress: Float): Float =
    resolveAudioNowPlayingCoverSizeDp(mergeProgress, searchProgress) + 14f

/**
 * The source bar is hidden only while the shared-transition host owns the
 * return handoff; that contract lives in
 * [com.android.purebilibili.core.ui.transition.resolveNowPlayingBarReturnVisibility].
 * A separate landing animation is intentionally not used:
 * the shared morph is the only geometry timeline.
 */

internal fun canOpenAudioNowPlayingBarSource(
    layoutStable: Boolean,
    imeSettled: Boolean = true,
    sharedTransitionRunning: Boolean = false,
): Boolean = layoutStable && imeSettled && !sharedTransitionRunning

internal data class AudioNowPlayingBarRowMetrics(
    val coverPx: Int,
    val horizontalPaddingPx: Int,
    val spacerPx: Int,
    val playWidthPx: Int,
    val extraWidthPx: Int,
    val titleWidthPx: Int,
    val contentStartPx: Int,
    val artistHeightPx: Int,
    val controlHeightPx: Int,
)

internal fun resolveAudioNowPlayingPrimaryProgress(searchProgress: Float): Float =
    1f - searchProgress.coerceIn(0f, 1f)

internal fun resolveAudioNowPlayingSupplementalProgress(
    mergeProgress: Float,
    searchProgress: Float,
): Float = (1f - mergeProgress.coerceIn(0f, 1f)) *
    resolveAudioNowPlayingPrimaryProgress(searchProgress)

internal fun resolveAudioNowPlayingPrimaryAlpha(searchProgress: Float): Float {
    val primary = resolveAudioNowPlayingPrimaryProgress(searchProgress)
    return ((primary - 0.12f) / 0.88f).coerceIn(0f, 1f)
}

internal fun resolveAudioNowPlayingSupplementalAlpha(
    mergeProgress: Float,
    searchProgress: Float,
): Float {
    val supplemental = resolveAudioNowPlayingSupplementalProgress(mergeProgress, searchProgress)
    return ((supplemental - 0.15f) / 0.85f).coerceIn(0f, 1f)
}

internal fun resolveAudioNowPlayingBarRowMetrics(
    maxWidthPx: Int,
    mergeProgress: Float,
    searchProgress: Float,
    density: Float,
): AudioNowPlayingBarRowMetrics {
    val merge = mergeProgress.coerceIn(0f, 1f)
    val search = searchProgress.coerceIn(0f, 1f)
    val primary = resolveAudioNowPlayingPrimaryProgress(search)
    val supplemental = resolveAudioNowPlayingSupplementalProgress(merge, search)
    fun dp(value: Float): Int = (value * density).roundToInt().coerceAtLeast(0)
    val cover = dp(resolveAudioNowPlayingCoverSizeDp(merge, search))
    val horizontalPadding = dp((12f - 2f * merge) * primary)
    val spacer = dp((10f - 4f * merge) * primary)
    val controlHeightDp = resolveAudioNowPlayingBarHeightDp(merge, search)
    val playWidth = dp(controlHeightDp * primary)
    val extraWidth = dp(controlHeightDp * supplemental)
    val artistHeight = dp(16f * supplemental)
    val controlHeight = dp(controlHeightDp)
    val innerWidth = (maxWidthPx - horizontalPadding * 2).coerceAtLeast(0)
    val remaining = (innerWidth - cover - spacer - playWidth - extraWidth).coerceAtLeast(0)
    val titleWidth = (remaining * primary).roundToInt()
    val packed = cover + spacer + titleWidth + playWidth + extraWidth
    val contentStart = horizontalPadding +
        ((innerWidth - packed).coerceAtLeast(0) * search / 2f).roundToInt()
    return AudioNowPlayingBarRowMetrics(
        coverPx = cover,
        horizontalPaddingPx = horizontalPadding,
        spacerPx = spacer,
        playWidthPx = playWidth,
        extraWidthPx = extraWidth,
        titleWidthPx = titleWidth,
        contentStartPx = contentStart,
        artistHeightPx = artistHeight,
        controlHeightPx = controlHeight,
    )
}
