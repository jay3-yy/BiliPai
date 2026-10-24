package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure
import kotlinx.coroutines.flow.collectLatest

internal fun shouldFreezeVideoDetailPlayerBounds(
    ownsCardTransition: Boolean,
    committedCardReturn: Boolean,
    exposure: VideoCardTransitionExposure,
): Boolean {
    // A committed entry can lose session ownership before its final frame is disposed.
    if (committedCardReturn) return true
    if (!ownsCardTransition) return false
    return when (exposure) {
        VideoCardTransitionExposure.Opening,
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning,
        VideoCardTransitionExposure.Restoring -> true
        VideoCardTransitionExposure.Idle,
        VideoCardTransitionExposure.SettledHidden -> false
    }
}

private class VideoPlayerCoordinatesHolder {
    // Layout callbacks keep the latest handle without feeding motion back into composition.
    var coordinates: LayoutCoordinates? = null
}

/** Publishes settled bounds after both entry and cancelled-gesture landing, even without a relayout. */
@Composable
internal fun Modifier.onSettledVideoPlayerPositioned(
    freezeProvider: () -> Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
): Modifier {
    val holder = remember { VideoPlayerCoordinatesHolder() }
    val latestFreezeProvider = rememberUpdatedState(freezeProvider)
    val latestOnPositioned = rememberUpdatedState(onPositioned)
    LaunchedEffect(holder) {
        snapshotFlow { latestFreezeProvider.value() }.collectLatest { frozen ->
            if (!frozen) {
                // Sample after the endpoint layout; a re-grab cancels this pending publication.
                withFrameNanos { }
                if (!latestFreezeProvider.value()) {
                    holder.coordinates?.takeIf { it.isAttached }?.let { latestOnPositioned.value(it) }
                }
            }
        }
    }
    return onGloballyPositioned { coordinates ->
        holder.coordinates = coordinates
        if (!latestFreezeProvider.value()) latestOnPositioned.value(coordinates)
    }
}
