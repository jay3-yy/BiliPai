package com.android.purebilibili.feature.home.components

import kotlin.math.roundToInt

internal enum class LinkedDockPhase { Expanded, Playback, Search }

internal fun resolveLinkedDockRestingPhase(
    collapseRequested: Boolean,
    hasAudio: Boolean,
): LinkedDockPhase = if (collapseRequested && hasAudio) {
    LinkedDockPhase.Playback
} else {
    LinkedDockPhase.Expanded
}

internal data class LinkedDockGeometry(
    val searchWidth: Int,
    val audioWidth: Int,
    val audioX: Int,
    val audioY: Int,
    val top: Int,
    val height: Int,
)

internal fun resolveLinkedDockGeometry(
    width: Int,
    button: Int,
    barHeight: Int,
    gap: Int,
    hasAudio: Boolean,
    searchEnabled: Boolean,
    mergeProgress: Float,
    searchProgress: Float,
): LinkedDockGeometry {
    val merge = mergeProgress.coerceIn(0f, 1f)
    val search = searchProgress.coerceIn(0f, 1f)
    val top = ((if (hasAudio) barHeight + gap else 0) * (1f - merge)).roundToInt()
    val searchWidth = if (!searchEnabled) 0 else (
        button + (width - button * (if (hasAudio) 3 else 2) - gap * (if (hasAudio) 2 else 1)) * search
    ).roundToInt().coerceAtLeast(button).coerceAtMost((width - button).coerceAtLeast(0))
    // Both playback and search retain separate capsule surfaces.
    val playbackGap = gap
    val compactAudioWidth = (width - button - searchWidth -
        playbackGap * (if (searchEnabled) 2 else 1)).coerceAtLeast(0)
    return LinkedDockGeometry(
        searchWidth = searchWidth,
        audioWidth = if (hasAudio) (width + (compactAudioWidth - width) * merge).roundToInt() else 0,
        audioX = ((button + playbackGap) * merge).roundToInt(),
        audioY = (top * merge).roundToInt(),
        top = top,
        height = top + barHeight,
    )
}
