package com.android.purebilibili.feature.video.ui.gesture

import kotlin.math.abs

data class TwoFingerSpeedToggleState(
    val verticalEnabled: Boolean = false,
    val horizontalEnabled: Boolean = false
)

enum class TwoFingerSpeedGestureMode {
    Off,
    Vertical,
    Horizontal
}

enum class LockedTwoFingerSpeedAxis {
    Vertical,
    Horizontal
}

internal fun applyVerticalTwoFingerSpeedToggle(
    current: TwoFingerSpeedToggleState,
    enabled: Boolean
): TwoFingerSpeedToggleState {
    return if (enabled) {
        TwoFingerSpeedToggleState(
            verticalEnabled = true,
            horizontalEnabled = false
        )
    } else {
        current.copy(verticalEnabled = false)
    }
}

internal fun applyHorizontalTwoFingerSpeedToggle(
    current: TwoFingerSpeedToggleState,
    enabled: Boolean
): TwoFingerSpeedToggleState {
    return if (enabled) {
        TwoFingerSpeedToggleState(
            verticalEnabled = false,
            horizontalEnabled = true
        )
    } else {
        current.copy(horizontalEnabled = false)
    }
}

internal fun resolveTwoFingerSpeedGestureMode(
    verticalEnabled: Boolean,
    horizontalEnabled: Boolean
): TwoFingerSpeedGestureMode {
    return when {
        verticalEnabled -> TwoFingerSpeedGestureMode.Vertical
        horizontalEnabled -> TwoFingerSpeedGestureMode.Horizontal
        else -> TwoFingerSpeedGestureMode.Off
    }
}

internal fun resolveLockedTwoFingerSpeedAxis(
    mode: TwoFingerSpeedGestureMode,
    totalDragX: Float,
    totalDragY: Float,
    thresholdPx: Float
): LockedTwoFingerSpeedAxis? {
    return when (mode) {
        TwoFingerSpeedGestureMode.Vertical -> {
            if (abs(totalDragY) >= thresholdPx && abs(totalDragY) > abs(totalDragX)) {
                LockedTwoFingerSpeedAxis.Vertical
            } else {
                null
            }
        }

        TwoFingerSpeedGestureMode.Horizontal -> {
            if (abs(totalDragX) >= thresholdPx && abs(totalDragX) > abs(totalDragY)) {
                LockedTwoFingerSpeedAxis.Horizontal
            } else {
                null
            }
        }

        TwoFingerSpeedGestureMode.Off -> null
    }
}

internal fun resolveTwoFingerGesturePlaybackSpeed(
    startSpeed: Float,
    mode: TwoFingerSpeedGestureMode,
    totalDragX: Float,
    totalDragY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    supportedSpeeds: List<Float>
): Float {
    if (mode == TwoFingerSpeedGestureMode.Off || supportedSpeeds.isEmpty()) {
        return startSpeed
    }

    val snappedStartSpeed = supportedSpeeds.minByOrNull { abs(it - startSpeed) } ?: startSpeed
    val startIndex = supportedSpeeds.indexOf(snappedStartSpeed).coerceAtLeast(0)
    val primaryDragPx = when (mode) {
        TwoFingerSpeedGestureMode.Vertical -> -totalDragY
        TwoFingerSpeedGestureMode.Horizontal -> totalDragX
        TwoFingerSpeedGestureMode.Off -> 0f
    }
    val containerSizePx = when (mode) {
        TwoFingerSpeedGestureMode.Vertical -> containerHeightPx
        TwoFingerSpeedGestureMode.Horizontal -> containerWidthPx
        TwoFingerSpeedGestureMode.Off -> 0f
    }.coerceAtLeast(1f)
    val stepPx = (containerSizePx * 0.12f).coerceAtLeast(72f)
    val stepOffset = (primaryDragPx / stepPx).toInt()
    val targetIndex = (startIndex + stepOffset).coerceIn(0, supportedSpeeds.lastIndex)
    return supportedSpeeds[targetIndex]
}
